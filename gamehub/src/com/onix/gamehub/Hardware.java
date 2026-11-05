package com.onix.gamehub;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * O aparelho (TV ou celular), como o pc/src/hardware.js faz com o PC: processador, memória, 32 ou
 * 64 bits e OpenGL ES, para cada console 3D ter um mínimo (abaixo dele o console não aparece) e
 * para a resolução sair sozinha. Quatro faixas:
 *   0 = fraco (só núcleos econômicos, como Cortex-A53/A55: a maioria das TVs e celulares baratos)
 *   1 = básico (Cortex-A72/A73/A75, ou pouca memória)
 *   2 = intermediário (Cortex-A76 a A720, 4 GB ou mais)
 *   3 = forte (Cortex-X, 6 GB ou mais)
 * O processador decide; a memória e os 32 bits só baixam a faixa. Tudo é lido de /proc e /sys,
 * sem permissão nenhuma.
 */
final class Hardware {

    static final String[] TIERS = {"Fraco", "Básico", "Intermediário", "Forte"};

    private static int tier = -1, cpuScore, cores, mhz, ramMb, gles;
    private static String cpu = "";
    private static boolean is64;

    private Hardware() {
    }

    /** Faixa de cada núcleo ARM pelo "CPU part" (implementador 0x41 = ARM, 0x51 = Qualcomm, 0x53 = Samsung). */
    private static int partScore(int impl, int part) {
        if (impl == 0x41) {
            switch (part) {
                case 0xd44: case 0xd4c: case 0xd48: case 0xd4e: case 0xd82: case 0xd85: case 0xd8e:
                    return 3;   // Cortex-X1, X1C, X2, X3, X4, X925
                case 0xd0b: case 0xd0c: case 0xd0d: case 0xd0e: case 0xd41: case 0xd4b: case 0xd47: case 0xd4d: case 0xd81: case 0xd87:
                    return 2;   // Cortex-A76, N1, A77, A78, A78C, A710, A715, A720, A725
                case 0xd07: case 0xd08: case 0xd09: case 0xd0a: case 0xc0f: case 0xc0e:
                    return 1;   // Cortex-A57, A72, A73, A75, A15, A17
                case 0xd01: case 0xd02: case 0xd03: case 0xd04: case 0xd05: case 0xd46: case 0xd80: case 0xc05: case 0xc07: case 0xc09:
                    return 0;   // Cortex-A32, A34, A53, A35, A55, A510, A520, A5, A7, A9
            }
        } else if (impl == 0x51) {
            switch (part) {
                case 0x804: return 2;                          // Kryo 4xx Gold (A76)
                case 0x800: case 0x802: case 0x205: case 0x211: return 1;   // Kryo 2xx/3xx Gold, Kryo
                case 0x801: case 0x803: case 0x805: return 0;  // os Silver (A53/A55)
            }
        } else if (impl == 0x53) {
            return part >= 3 ? 2 : 1;                          // Exynos Mongoose M3 em diante
        }
        return -1;
    }

    static synchronized void detect() {
        if (tier >= 0) return;
        is64 = Build.SUPPORTED_64_BIT_ABIS.length > 0;
        cpuScore = -1;
        int impl = 0;
        String hw = "";
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"));
            try {
                for (String l; (l = r.readLine()) != null; ) {
                    int c = l.indexOf(':');
                    if (c < 0) continue;
                    String k = l.substring(0, c).trim(), v = l.substring(c + 1).trim();
                    if (k.equals("processor")) cores++;
                    else if (k.equals("CPU implementer")) impl = Integer.decode(v);
                    else if (k.equals("CPU part")) cpuScore = Math.max(cpuScore, partScore(impl, Integer.decode(v)));
                    else if (k.equals("Hardware")) hw = v;
                }
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
        }
        for (int i = 0; i < 16; i++) {
            String f = read("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (f != null) try {
                mhz = Math.max(mhz, Integer.parseInt(f.trim()) / 1000);
            } catch (Exception ignored) {
            }
        }
        if (cores == 0) cores = Runtime.getRuntime().availableProcessors();
        // Núcleo desconhecido: vai pela velocidade.
        if (cpuScore < 0) cpuScore = mhz >= 2600 ? 2 : mhz >= 1900 ? 1 : 0;
        String mem = read("/proc/meminfo");
        if (mem != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("MemTotal:\\s+(\\d+)").matcher(mem);
            if (m.find()) ramMb = (int) (Long.parseLong(m.group(1)) / 1024);
        }
        gles = glesVersion();
        cpu = (soc(hw) + " · " + cores + " núcleos" + (mhz > 0 ? String.format(Locale.ROOT, " · %.1f GHz", mhz / 1000f) : "")).trim();
        int t = cpuScore;
        // A memória que o Android diz ter é um pouco menos que a da caixa (2 GB aparecem como ~1,8).
        if (ramMb > 0 && ramMb < 2600) t = Math.min(t, 0);
        else if (ramMb > 0 && ramMb < 3600) t = Math.min(t, 1);
        else if (ramMb > 0 && ramMb < 5500) t = Math.min(t, 2);
        if (!is64) t = Math.min(t, 1);
        tier = Math.max(0, t);
    }

    /** Nome do chip: Build.SOC_MODEL (Android 12+, por reflexão: o build compila contra o 23), o "Hardware" do cpuinfo ou o Build.HARDWARE. */
    private static String soc(String hw) {
        try {
            String m = (String) Build.class.getField("SOC_MODEL").get(null);
            if (m != null && !m.isEmpty() && !"unknown".equals(m)) return m;
        } catch (Exception ignored) {
        }
        return hw.isEmpty() ? Build.HARDWARE : hw;
    }

    static int tier() {
        detect();
        return tier;
    }

    /** ro.opengles.version: 0x30002 = 3.2. Sem ela, 2.0. */
    private static int glesVersion() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("getInt", String.class, int.class).invoke(null, "ro.opengles.version", 0x20000);
            return (Integer) v;
        } catch (Exception e) {
            return 0x20000;
        }
    }

    /* ---------------- Mínimo de cada console ---------------- */

    /** Faixa mínima e OpenGL ES mínimo (0x30002 = 3.2). Os de 2D rodam em qualquer um. */
    static int minTier(String console) {
        if ("gc".equals(console) || "wii".equals(console)) return 2;
        if ("ps2".equals(console)) return 3;
        return 0;
    }

    private static int minGles(String console) {
        if ("gc".equals(console) || "wii".equals(console) || "ps2".equals(console)) return 0x30002;
        if ("psp".equals(console) || "n64".equals(console)) return 0x20000;
        return 0;
    }

    /** O aparelho chega ao mínimo do console. */
    static boolean runs(String console) {
        detect();
        return tier >= minTier(console) && gles >= minGles(console);
    }

    /** "ok" roda bem, "slow" roda mas pode ficar lento (bem no mínimo), "no" abaixo do mínimo. */
    static String status(String console) {
        if (!runs(console)) return "no";
        boolean heavy = "n64".equals(console) || "ds".equals(console) || "psp".equals(console)
                || "gc".equals(console) || "wii".equals(console) || "ps2".equals(console);
        return heavy && tier() == minTier(console) ? "slow" : "ok";
    }

    /**
     * Resolução e afins escolhidos pela faixa, como o AUTO_RES do PC. Valem só quando a pessoa não
     * escolheu outra coisa nas opções do console.
     */
    static String[][] coreDefaults(String console) {
        int t = tier();
        if ("psp".equals(console))
            return new String[][]{{"ppsspp_internal_resolution", t >= 3 ? "1440x816" : t == 2 ? "960x544" : "480x272"}};
        if ("n64".equals(console))
            return new String[][]{{"parallel-n64-screensize", t == 0 ? "320x240" : "640x480"}};
        if ("gc".equals(console) || "wii".equals(console))
            return new String[][]{{"dolphin_efb_scale", t >= 3 ? "2" : "1"}};
        if ("ps2".equals(console))
            return new String[][]{{"pcsx2_upscale_multiplier", "1x (Native)"}};
        return new String[0][];
    }

    /* ---------------- Para a tela (Configurações → Este aparelho) ---------------- */

    static JSONObject json() {
        detect();
        JSONObject o = new JSONObject();
        try {
            o.put("tier", tier).put("tierName", TIERS[tier]).put("cpu", cpu).put("ramMb", ramMb).put("is64", is64)
                    .put("gles", (gles >> 16) + "." + (gles & 0xFFFF)).put("gpu", gpu())
                    .put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim());
            JSONArray list = new JSONArray();
            for (String id : new String[]{"n64", "ds", "psp", "gc", "wii", "ps2"}) {
                list.put(new JSONObject().put("id", id).put("status", status(id))
                        .put("min", TIERS[minTier(id)]).put("bits", "gc".equals(id) || "wii".equals(id) || "ps2".equals(id)));
            }
            o.put("consoles", list);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static String gpuName;

    /** Nome da placa de vídeo, por um contexto OpenGL de 1x1 que some logo depois. */
    static synchronized String gpu() {
        if (gpuName != null) return gpuName;
        gpuName = "";
        EGLDisplay d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        try {
            int[] v = new int[2];
            if (!EGL14.eglInitialize(d, v, 0, v, 1)) return gpuName;
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            EGL14.eglChooseConfig(d, new int[]{EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE}, 0, cfg, 0, 1, n, 0);
            if (n[0] < 1) return gpuName;
            EGLContext ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            EGLSurface s = EGL14.eglCreatePbufferSurface(d, cfg[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (EGL14.eglMakeCurrent(d, s, s, ctx)) {
                String r = GLES20.glGetString(GLES20.GL_RENDERER);
                gpuName = r == null ? "" : r.trim();
            }
            EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(d, s);
            EGL14.eglDestroyContext(d, ctx);
        } catch (Exception ignored) {
        }
        // Sem eglTerminate: a tela do app (HWUI) usa a mesma conexão com o EGL neste processo.
        return gpuName;
    }

    private static String read(String path) {
        File f = new File(path);
        if (!f.canRead()) return null;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            try {
                StringBuilder s = new StringBuilder();
                for (String l; (l = r.readLine()) != null; ) s.append(l).append('\n');
                return s.toString();
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
