package com.onix.gamehub;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * Tela do jogo, num processo só dela (:game): imagem nativa (GLSurfaceView + jni/retro.c) e o
 * menu do GameHub numa WebView transparente por cima, que só aparece com o jogo pausado.
 *
 * Botões: cada controle vira um jogador (o primeiro a apertar é o 1). O controle remoto da TV
 * também joga (OK = A, Play = Start) e Voltar abre o menu. Em todos os 4 consoles, L2 e R2
 * sobram: segurar R2 acelera e segurar L2 rebobina (quando ligado).
 * Menu: Voltar, o botão do meio do controle (Guide/Mode) ou Select + Start juntos.
 */
public class GameActivity extends Activity implements Gamepads.Listener {

    private GLSurfaceView gl;
    private WebView menu;
    private TextView toast, fps;
    private final Handler ui = new Handler(Looper.getMainLooper());

    String id, mode, profile, lang;
    boolean kids;
    JSONObject game, settings;
    Consoles.Console console;
    private File rom, saveDir, stateDir;
    private volatile boolean loaded, failed, menuOpen, quitting;
    // DEMONSTRAÇÃO (vídeo do Threads): no Super Mario World, depois de abrir o jogo ou de carregar um estado, o 1º pulo
    // mostra a conquista comum, o 3º a rara e o 5º a platina. Não vai para release.
    private boolean demoGame;
    private volatile int demoJumps;   // pulos desde que abriu o jogo ou carregou o estado
    private boolean demoJumpDown;
    private int demoShown;
    private View demoCard, demoIcon, demoPts, demoShine;
    private TextView demoKicker, demoTitle, demoSub;
    private LinearLayout demoRow;
    private DemoStage demoStage;   // a conquista: aviso nativo (leve, sem página web)
    private static final int DEMO_SLIDE = 56;   // quanto o cartão desliza ao entrar (dp)
    private android.media.SoundPool demoSound;
    private final int[] demoSoundId = new int[3];
    private static final int COMUM = 0, RARA = 1, PLATINA = 2;
    /** Por que não abriu: "" (arquivo), "system" (sem os arquivos do emulador), "bios" (PS2). */
    volatile String failReason = "";
    /* Consoles 3D: a cruz que estava empurrando o analógico (para soltar só o que ela empurrou). */
    private final boolean[] dpadStick = new boolean[4], cStick = new boolean[4];
    volatile boolean capture;   // menu → Atribuir botões: a próxima tecla vai para a página
    private long started, playedMs;
    private int frames, sramTick;
    private long fpsAt;

    /* Jogadores: id do aparelho -> porta (0..3). O controle remoto e o teclado são o jogador 1. */
    private final SparseIntArray ports = new SparseIntArray();
    private JSONObject players;   // controle 1 e 2 definidos nas Configurações
    boolean sounds = true, motion = true;
    private final int[] keyBits = new int[4], hatBits = new int[4], stickBits = new int[4];
    /* Celular: controle na tela (jogador 1). Some com um controle de verdade conectado. */
    private TouchPad pad;
    private int touchBits;
    private boolean touchFast;
    boolean mobile;
    private boolean ffHold, rwHold, selectDown, startDown;
    private final SparseIntArray remap = new SparseIntArray();
    private final int[] webBits = new int[4];
    private RemoteGamepadServer gamepadServer;

    private static boolean webDataSeparated;

    /**
     * O jogo roda no processo ":game" e o GameHub no principal. Desde o Android 9, dois processos
     * não podem usar o WebView com a mesma pasta de dados: o menu do jogo fechava o app ao abrir
     * com o GameHub ainda vivo. O menu não guarda nada no WebView, então usa uma pasta só dele.
     * Por reflexão porque o build compila contra o android-23.
     */
    private static void separateWebData() {
        if (webDataSeparated || Build.VERSION.SDK_INT < 28) return;
        webDataSeparated = true;
        try {
            WebView.class.getMethod("setDataDirectorySuffix", String.class).invoke(null, "game");
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        id = getIntent().getStringExtra("id");
        mode = getIntent().getStringExtra("mode");
        profile = Files.profile(getIntent().getStringExtra("profile"));
        lang = "es".equals(getIntent().getStringExtra("lang")) ? "es" : "pt";
        kids = getIntent().getBooleanExtra("kids", false);
        sounds = getIntent().getBooleanExtra("sounds", true);
        motion = getIntent().getBooleanExtra("motion", true);
        players = Players.all(this);
        game = id != null && Files.ID.matcher(id).matches() ? Library.game(this, id) : null;
        console = game == null ? null : Consoles.byId(game.optString("console"));
        if (game == null || console == null) {
            finish();
            return;
        }
        rom = game.has("path") ? usbRom(game) : new File(Files.roms(this), game.optString("file"));
        if (rom == null || !rom.isFile()) {
            finish();
            return;
        }
        saveDir = Files.saves(this, profile);
        stateDir = Files.states(this, profile, id);
        settings = Config.effective(this, console.id, id);
        readRemap();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        gl = new GLSurfaceView(this);
        if (console.hw) {
            // 3D: OpenGL ES 3 quando o aparelho tem (PSP, N64, Dolphin), senão o 2.
            gl.setEGLConfigChooser(new Gles3Config());
            gl.setEGLContextFactory(new Gles3Context());
        } else {
            gl.setEGLContextClientVersion(2);
        }
        gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(new Renderer());
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(gl, new FrameLayout.LayoutParams(-1, -1));
        // DS: a tela de baixo. Wii: a mira do Wii Remote.
        if ("ds".equals(console.id) || "wii".equals(console.id)) gl.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                touchScreen(e.getX(), e.getY(), e.getActionMasked() != MotionEvent.ACTION_UP && e.getActionMasked() != MotionEvent.ACTION_CANCEL);
                return true;
            }
        });
        demoGame = "snes".equals(console.id) && game.optString("name").toLowerCase(java.util.Locale.ROOT).contains("mario world");
        mobile = Device.mobile(this);
        if (mobile) {
            // Em pé ou deitado, como a pessoa segurar o celular.
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            makePad(root, -1);
            root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    if (r - l != or - ol || b - t != ob - ot) layoutPad();
                }
            });
        } else {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        separateWebData();
        menu = new WebView(this);
        menu.setBackgroundColor(Color.TRANSPARENT);
        Web.setup(menu, this);
        menu.addJavascriptInterface(new MenuBridge(this), "Menu");
        menu.setVisibility(View.GONE);
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        menu.loadUrl(Web.BASE + "menu.html");

        toast = label(Gravity.BOTTOM | Gravity.START);
        root.addView(toast, labelParams(Gravity.BOTTOM | Gravity.START));
        fps = label(Gravity.TOP | Gravity.END);
        root.addView(fps, labelParams(Gravity.TOP | Gravity.END));
        if (demoGame) demoSetup(root);
        fps.setVisibility(settings.optBoolean("showFps") ? View.VISIBLE : View.GONE);
        setContentView(root);
        gamepads = new Gamepads(this, this);
        started = System.currentTimeMillis();

        gamepadServer = new RemoteGamepadServer(this, new RemoteGamepadServer.Listener() {
            @Override
            public void onButtons(int port, int bits) {
                if (port >= 0 && port < 4) {
                    webBits[port] = bits;
                    push(port);
                }
            }

            @Override
            public void onMenu() {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        openMenu("");
                    }
                });
            }

            @Override
            public boolean portTaken(int port) {
                // Controles já ligados ficam com a vaga deles antes do celular, mesmo sem ter
                // apertado nada ainda (o PS4 continua Jogador 1 e o celular vira o 2).
                synchronized (ports) {
                    for (int id : InputDevice.getDeviceIds()) {
                        InputDevice d = InputDevice.getDevice(id);
                        if (Gamepads.isGamepad(d) && portFor(d) == port) return true;
                    }
                    return false;
                }
            }

            @Override
            public void onFastForward(final boolean on) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        touchFast = on;
                        Native.setHotkeys(ffHold || touchFast, rwHold);
                    }
                });
            }

            @Override
            public void onClientsChanged(final int count) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (menu != null) {
                                Web.emit(menu, "gamepadClients", new JSONObject().put("count", count).toString());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        }, padConsole(), game.optString("name"));
        gamepadServer.start();
    }

    String gamepadUrl() {
        return gamepadServer != null ? gamepadServer.url() : "";
    }

    int gamepadClients() {
        return gamepadServer != null ? gamepadServer.clientCount() : 0;
    }

    private Gamepads gamepads;

    /**
     * Celular em pé: o jogo em cima (largura toda, na proporção 4:3) e o controle embaixo.
     * Deitado, ou com um controle de verdade: o jogo na tela toda (o controle fica por cima).
     */
    /** Controle na tela do console (o do Wii muda com o tipo de controle). index -1 = por cima de tudo. */
    private void makePad(FrameLayout root, int index) {
        pad = new TouchPad(this, padConsole(), new TouchPad.Listener() {
            @Override
            public void padButtons(int bits) {
                touchBits = bits;
                push(0);
            }

            @Override
            public void padFastForward(boolean on) {
                touchFast = on;
                Native.setHotkeys(ffHold || touchFast, rwHold);
            }

            @Override
            public void padMenu() {
                openMenu("");
            }

            @Override
            public void padTouch(float x, float y, boolean down) {
                touchScreen(x, y, down);
            }

            @Override
            public void padSwap() {
                swapWii();
            }

            @Override
            public void padEditDone() {
                // Saiu do editor (aberto pelo menu): volta ao jogo.
                if (!menuOpen) Native.setPaused(false);
            }
        });
        pad.setSpanish("es".equals(lang));
        if (index < 0) root.addView(pad, new FrameLayout.LayoutParams(-1, -1));
        else root.addView(pad, index, new FrameLayout.LayoutParams(-1, -1));
    }

    /* ---------------- Wii: Wii Remote + Nunchuk ou Classic Controller ---------------- */

    private boolean wiiClassic() {
        return "wii".equals(console.id) && "classic".equals(settings.optString("wiiPad"));
    }

    private String padConsole() {
        return wiiClassic() ? "wiicc" : console.id;
    }

    /** Tipo de controle para o núcleo: o do Wii (ids do Dolphin, DolphinLibretro/Input.cpp) ou o RetroPad. */
    private int portDevice() {
        if (!"wii".equals(console.id)) return Native.DEVICE_JOYPAD;
        return wiiClassic() ? Native.DEVICE_WII_CLASSIC : Native.DEVICE_WII_NUNCHUK;
    }

    /** Botão ⇄ (ou o menu): troca o tipo e guarda neste jogo. */
    void swapWii() {
        if (!"wii".equals(console.id)) return;
        Config.set(this, "game:" + id, "wiiPad", wiiClassic() ? "\"nunchuk\"" : "\"classic\"");
        reloadSettings();
        toast(wiiClassic() ? "Classic Controller" : "Wii Remote + Nunchuk");
    }

    /** O tipo de controle do Wii mudou (pelo ⇄ ou pelo menu): avisa o núcleo e troca os botões da tela. */
    private void applyWiiPad() {
        final int dev = portDevice();
        queue(new Runnable() {
            @Override
            public void run() {
                Native.setDevice(0, dev);
            }
        });
        if (gamepadServer != null) gamepadServer.updateState(padConsole(), game.optString("name"));   // o celular troca os botões junto
        if (pad == null || padConsole().equals(pad.console())) return;
        FrameLayout root = (FrameLayout) pad.getParent();
        int index = root.indexOfChild(pad);
        pad.release();
        root.removeView(pad);
        makePad(root, index);
        layoutPad();
    }

    private void layoutPad() {
        if (pad == null) return;
        View root = (View) gl.getParent();
        int w = root.getWidth(), h = root.getHeight();
        boolean show = Gamepads.count() == 0 || Device.padAlways();
        pad.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) pad.release();
        FrameLayout.LayoutParams g = (FrameLayout.LayoutParams) gl.getLayoutParams();
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) pad.getLayoutParams();
        if (show && h > w) {
            int gh = Math.min(w * 3 / 4, h / 2);
            int top = Math.max(0, (h / 2 - gh) / 3);
            g.height = gh;
            g.topMargin = top;
            g.gravity = android.view.Gravity.TOP;
            p.height = h - gh - top;
            p.topMargin = gh + top;
        } else {
            g.height = -1;
            g.topMargin = 0;
            p.height = -1;
            p.topMargin = 0;
        }
        gl.setLayoutParams(g);
        pad.setLayoutParams(p);
    }

    private TextView label(int gravity) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setPadding(28, 14, 28, 14);
        t.setBackgroundColor(Color.argb(170, 12, 8, 28));
        t.setGravity(gravity);
        t.setVisibility(View.GONE);
        return t;
    }

    private FrameLayout.LayoutParams labelParams(int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, gravity);
        p.setMargins(48, 36, 48, 36);
        return p;
    }

    /** Aviso curto no canto (estado salvo, jogador 2 conectado…). */
    void toast(final String text) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                toast.setText(text);
                toast.setVisibility(View.VISIBLE);
                ui.removeCallbacks(hideToast);
                ui.postDelayed(hideToast, 2600);
            }
        });
    }

    private final Runnable hideToast = new Runnable() {
        @Override
        public void run() {
            toast.setVisibility(View.GONE);
        }
    };

    String tr(String pt, String es) {
        return "es".equals(lang) ? es : pt;
    }

    private void readRemap() {
        remap.clear();
        JSONObject r = settings.optJSONObject("remap");
        if (r == null) return;
        Iterator<String> it = r.keys();
        while (it.hasNext()) {
            String k = it.next();
            try {
                remap.put(Integer.parseInt(k), r.getInt(k));
            } catch (Exception ignored) {
            }
        }
    }

    /* ---------------- Núcleo (thread do GL) ---------------- */

    private final class Renderer implements GLSurfaceView.Renderer {
        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            if (!loaded && !failed) load();
            Native.surfaceCreated();
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int w, int h) {
            Native.surfaceChanged(w, h);
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            if (!loaded) return;
            Native.frame();
            frames++;
            if (++sramTick >= 600) {   // ~10 s: grava a memória do cartucho se mudou
                sramTick = 0;
                Native.saveSram(sramFile().getPath());
            }
            long now = System.currentTimeMillis();
            if (now - fpsAt >= 1000) {
                final int n = frames;
                frames = 0;
                fpsAt = now;
                if (fps.getVisibility() == View.VISIBLE) ui.post(new Runnable() {
                    @Override
                    public void run() {
                        fps.setText(n + " FPS");
                    }
                });
                if (demoGame) android.util.Log.i("OnixDemo", "fps " + n);
            }
        }
    }

    private File sramFile() {
        return new File(saveDir, id + ".srm");
    }

    private File stateFile(String slot) {
        return new File(stateDir, slot + ".state");
    }

    /** Tela de toque do DS: o motor converte para a posição na imagem. */
    private void touchScreen(float x, float y, boolean down) {
        if (!menuOpen) Native.setPointer(x, y, down);
    }

    private boolean loadFail(String reason) {
        failed = true;
        failReason = reason;
        ui.post(new Runnable() {
            @Override
            public void run() {
                openMenu("error");
            }
        });
        return false;
    }

    private void load() {
        if ("ps2".equals(console.id) && !SystemFiles.hasPs2Bios(this)) {
            loadFail("bios");
            return;
        }
        if (!SystemFiles.ready(this, console.system)) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    toast(tr("Baixando os arquivos do emulador (só na primeira vez)…", "Descargando los archivos del emulador (solo la primera vez)…"));
                }
            });
            if (!SystemFiles.ensure(this, console.system)) {
                loadFail("system");
                return;
            }
        }
        Native.clearOptions();
        JSONObject core = settings.optJSONObject("core");
        // Consoles 3D: resolução pela faixa do aparelho, se a pessoa não escolheu outra.
        for (String[] kv : Hardware.coreDefaults(console.id)) if (core == null || !core.has(kv[0])) Native.setOption(kv[0], kv[1]);
        if (core != null) {
            Iterator<String> it = core.keys();
            while (it.hasNext()) {
                String k = it.next();
                Native.setOption(k, core.optString(k));
            }
        }
        String lib = getApplicationInfo().nativeLibraryDir + "/lib" + console.coreFor(rom.getName()) + ".so";
        Native.setDevice(0, portDevice());
        Native.setDevice(1, portDevice());
        boolean ok = rom.isFile() && Native.load(lib, rom.getPath(), Files.system(this).getPath(), saveDir.getPath(),
                "es".equals(lang) ? Native.LANG_ES : Native.LANG_PT_BR);
        if (!ok) {
            failed = true;
            ui.post(new Runnable() {
                @Override
                public void run() {
                    openMenu("error");
                }
            });
            return;
        }
        Native.loadSram(sramFile().getPath());
        // Alguns núcleos (FCEUmm) só contam as opções depois de abrir um jogo: guarda para a tela
        // de configurações do console.
        Files.write(new File(getFilesDir(), "options-" + console.id + "-" + lang + ".json"), Native.options());
        applySettings();
        applyCheats();
        if ("resume".equals(mode) && stateFile("auto").isFile()) Native.loadState(stateFile("auto").getPath());
        else if (mode != null && mode.matches("^[1-4]$") && stateFile(mode).isFile()) Native.loadState(stateFile(mode).getPath());
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int rate = 48000, burst = 256;
        try {
            rate = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
            burst = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        } catch (Exception ignored) {
        }
        Native.startAudio(rate, burst, settings.optInt("latency", 64));
        loaded = true;
        if (menuOpen) Native.setPaused(true);
        else if (Gamepads.count() == 0 && !"resume".equals(mode)) ui.post(new Runnable() {
            @Override
            public void run() {
                toast(tr("Sem controle: o controle remoto joga (OK = A). Voltar abre o menu.",
                        "Sin control: el control remoto juega (OK = A). Atrás abre el menú."));
            }
        });
    }

    /** Vídeo, som, acelerar/rebobinar/run-ahead e turbo, das configurações que valem para o jogo. */
    void applySettings() {
        String f = settings.optString("filter"), s = settings.optString("scale"), as = settings.optString("aspect");
        int filter = "pixel".equals(f) ? 1 : "smooth".equals(f) ? 2 : "crt".equals(f) ? 3 : "lcd".equals(f) ? 4 : 0;
        int scale = "integer".equals(s) ? 1 : "stretch".equals(s) ? 2 : 0;
        int aspect = "4:3".equals(as) ? 1 : "pixel".equals(as) ? 2 : "16:9".equals(as) ? 3 : 0;
        double hz = 60;
        try {
            hz = getWindowManager().getDefaultDisplay().getRefreshRate();
        } catch (Exception ignored) {
        }
        Native.configure(filter, scale, aspect, settings.optInt("ffspeed", 3), settings.optInt("runahead"),
                settings.optBoolean("rewind"), 48, hz);
        Native.setVolume(settings.optInt("volume", 100) / 100f);
        Native.setTurbo(settings.optInt("turbo"), settings.optInt("turboRate", 3));
    }

    void applyCheats() {
        Native.cheatReset();
        String s = Files.read(Files.cheats(this, id));
        if (s == null) return;
        try {
            JSONArray list = new JSONArray(s);
            int n = 0;
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                if (o.optBoolean("on")) Native.cheatSet(n++, true, o.optString("code"));
            }
        } catch (Exception ignored) {
        }
    }

    /** Roda na thread do GL. */
    void queue(Runnable r) {
        gl.queueEvent(r);
    }

    /** Salva no espaço (auto, 1–4) com a miniatura da tela. Chamar na thread do GL. */
    boolean saveStateNow(String slot) {
        boolean ok = Native.saveState(stateFile(slot).getPath());
        if (ok) {
            int[] px = new int[1024 * 1024];
            int wh = Native.snapshot(px);
            int w = wh >>> 16, h = wh & 0xFFFF;
            if (w > 0 && h > 0) {
                try {
                    Bitmap b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
                    int tw = 320, th = Math.max(1, h * tw / w);
                    Bitmap t = Bitmap.createScaledBitmap(b, tw, th, true);
                    // Mesmo tamanho (Mega Drive, 320 de largura): o Android devolve a própria b.
                    if (t != b) b.recycle();
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    t.compress(Bitmap.CompressFormat.JPEG, 82, o);
                    t.recycle();
                    Files.write(new File(stateDir, slot + ".jpg"), o.toByteArray());
                } catch (Exception e) {
                    android.util.Log.w("OnixRetro", "miniatura falhou", e);
                }
            }
        }
        return ok;
    }

    boolean loadStateNow(String slot) {
        File f = stateFile(slot);
        boolean ok = f.isFile() && Native.loadState(f.getPath());
        if (ok && demoGame) demoJumps = 0;
        return ok;
    }

    /* ---------------- Menu ---------------- */

    void openMenu(String reason) {
        if (quitting) return;
        menuOpen = true;
        Native.setPaused(true);
        releaseAll();
        if (loaded) queue(new Runnable() {
            @Override
            public void run() {
                Native.saveSram(sramFile().getPath());
            }
        });
        menu.setVisibility(View.VISIBLE);
        menu.requestFocus();
        Web.emit(menu, "open", Web.quote(reason == null ? "" : reason));
    }

    /** Menu → Controles na tela: fecha o menu e abre o editor, com o jogo pausado. */
    void editPad() {
        if (pad == null) return;
        ui.post(new Runnable() {
            @Override
            public void run() {
                menuOpen = false;
                menu.setVisibility(View.GONE);
                pad.setVisibility(View.VISIBLE);
                Native.setPaused(true);
                pad.setEditing(true);
            }
        });
    }

    void closeMenu() {
        if (failed) {
            quit();
            return;
        }
        menuOpen = false;
        menu.setVisibility(View.GONE);
        gl.requestFocus();
        Native.setPaused(false);
        fps.setVisibility(settings.optBoolean("showFps") ? View.VISIBLE : View.GONE);
    }

    /** Sair: guarda onde parou (estado auto, se ligado) e a memória do cartucho, depois fecha. */
    void quit() {
        if (quitting) return;
        quitting = true;
        Native.setPaused(true);
        if (!loaded) {
            finishGame();
            return;
        }
        queue(new Runnable() {
            @Override
            public void run() {
                Native.saveSram(sramFile().getPath());
                if (settings.optBoolean("autosave", true)) saveStateNow("auto");
                Native.unload();
                loaded = false;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        finishGame();
                    }
                });
            }
        });
    }

    private void finishGame() {
        long ms = playedMs + (menuOpen ? 0 : System.currentTimeMillis() - started);
        if (loaded || ms > 5000 || !failed) Library.recordPlay(this, profile, id, ms / 1000);
        setResult(RESULT_OK);
        finish();
        overridePendingTransition(0, 0);
    }

    /* ---------------- Ciclo de vida ---------------- */

    @Override
    protected void onResume() {
        super.onResume();
        if (gl == null) return;   // jogo não encontrado: a tela já está fechando
        gl.onResume();
        gamepads.start();
        started = System.currentTimeMillis();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        if (gl == null) {
            super.onPause();
            return;
        }
        playedMs += System.currentTimeMillis() - started;
        gamepads.stop();
        if (!quitting && loaded) {
            // Saiu pelo botão Início da TV: guarda tudo agora (o Android pode fechar o app depois).
            Native.setPaused(true);
            queue(new Runnable() {
                @Override
                public void run() {
                    Native.saveSram(sramFile().getPath());
                    if (settings.optBoolean("autosave", true)) saveStateNow("auto");
                }
            });
            if (!menuOpen) ui.post(new Runnable() {
                @Override
                public void run() {
                    openMenu("paused");
                }
            });
        }
        gl.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (demoSound != null) demoSound.release();
        if (demoStage != null) demoStage.stop();
        if (gamepadServer != null) {
            gamepadServer.stop();
            gamepadServer = null;
        }
        if (isFinishing()) {
            if (menu != null) menu.destroy();
            // Processo só do jogo: termina junto, e o próximo jogo começa com o núcleo limpo.
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Process.killProcess(Process.myPid());
                }
            }, 300);
        }
    }

    @Override
    public void onBackPressed() {
        // Voltar no menu: a página decide (fecha o submenu ou volta ao jogo).
        if (gl == null) super.onBackPressed();
        else if (menuOpen) Web.emit(menu, "back", null);
        else openMenu("");
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /* ---------------- Controles ---------------- */

    @Override
    public void gamepadsChanged(int added, int removed) {
        if (pad != null) layoutPad();
        if (added >= 0) {
            InputDevice d = InputDevice.getDevice(added);
            toast(tr("Controle conectado: ", "Control conectado: ") + (d != null ? Gamepads.name(d) : ""));
            if (menuOpen) Web.emit(menu, "gamepads", Gamepads.list(this).toString());
        }
        boolean had;
        synchronized (ports) {
            had = removed >= 0 && ports.indexOfKey(removed) >= 0;
            if (had) ports.delete(removed);
        }
        if (had) {
            if (!menuOpen) openMenu("disconnected");
            else Web.emit(menu, "gamepads", Gamepads.list(this).toString());
        }
    }

    private int portFor(InputDevice d) {
        if (d == null || !Gamepads.isGamepad(d)) return 0;
        synchronized (ports) {
            return portForLocked(d);
        }
    }

    private int portForLocked(InputDevice d) {
        int dev = d.getId();
        int p = ports.get(dev, -1);
        if (p >= 0) return p;
        boolean[] used = new boolean[4];
        for (int i = 0; i < ports.size(); i++) used[ports.valueAt(i)] = true;
        // Celulares conectados como controle também ocupam vaga
        RemoteGamepadServer srv = gamepadServer;
        if (srv != null) for (int i = 0; i < 4; i++) if (srv.hasClient(i)) used[i] = true;
        // Definido nas Configurações: sempre a mesma vaga. Quem não foi definido não pega a vaga
        // de um controle definido que está ligado.
        int fixed = Players.portOf(players, d);
        if (fixed >= 0) {
            for (int i = ports.size() - 1; i >= 0; i--) if (ports.valueAt(i) == fixed) ports.removeAt(i);
            ports.put(dev, fixed);
            if (fixed > 0) toast(tr("Jogador ", "Jugador ") + (fixed + 1) + ": " + Gamepads.name(d));
            return fixed;
        }
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice o = InputDevice.getDevice(id);
            if (id != dev && Gamepads.isGamepad(o)) {
                int q = Players.portOf(players, o);
                if (q >= 0) used[q] = true;
            }
        }
        for (p = 0; p < 4 && used[p]; p++) ;
        if (p >= 4) p = 3;
        ports.put(dev, p);
        if (p > 0) toast(tr("Jogador ", "Jugador ") + (p + 1) + ": " + Gamepads.name(d));
        return p;
    }

    /** Tecla do Android -> botão do RetroPad (-1 = nenhum). A troca de botões vale primeiro. */
    private int button(int code) {
        int r = remap.get(code, -2);
        if (r != -2) return r;
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return Native.B;          // embaixo
            case KeyEvent.KEYCODE_BUTTON_B: return Native.A;          // direita
            case KeyEvent.KEYCODE_BUTTON_X: return Native.Y;          // esquerda
            case KeyEvent.KEYCODE_BUTTON_Y: return Native.X;          // em cima
            case KeyEvent.KEYCODE_BUTTON_L1: return Native.L;
            case KeyEvent.KEYCODE_BUTTON_R1: return Native.R;
            case KeyEvent.KEYCODE_BUTTON_L2: return Native.L2;
            case KeyEvent.KEYCODE_BUTTON_R2: return Native.R2;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return Native.L3;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return Native.R3;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return Native.SELECT;
            case KeyEvent.KEYCODE_BUTTON_START: return Native.START;
            case KeyEvent.KEYCODE_DPAD_UP: return Native.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return Native.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return Native.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Native.RIGHT;
            // controle remoto da TV e teclado
            case KeyEvent.KEYCODE_DPAD_CENTER: return Native.A;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_ENTER: return Native.START;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return Native.SELECT;
            case KeyEvent.KEYCODE_X: return Native.A;
            case KeyEvent.KEYCODE_Z: return Native.B;
            case KeyEvent.KEYCODE_S: return Native.X;
            case KeyEvent.KEYCODE_A: return Native.Y;
            case KeyEvent.KEYCODE_Q: return Native.L;
            case KeyEvent.KEYCODE_W: return Native.R;
            default: return -1;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (gl == null) return super.dispatchKeyEvent(e);
        if (menuOpen && capture) {
            int code = e.getKeyCode();
            if (code != KeyEvent.KEYCODE_HOME && code != KeyEvent.KEYCODE_POWER) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                    capture = false;
                    try {
                        Web.emit(menu, "key", new JSONObject().put("code", code).put("name", Gamepads.keyName(code)).toString());
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }
        }
        if (menuOpen) {
            int mapped = HubActivity.navKey(e.getKeyCode());
            if (mapped != e.getKeyCode()) {
                return super.dispatchKeyEvent(new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), mapped, e.getRepeatCount(), e.getMetaState()));
            }
            return super.dispatchKeyEvent(e);
        }
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (e.getAction() == KeyEvent.ACTION_MULTIPLE) return true;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_MODE || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_ESCAPE) {
            if (pad != null && pad.isEditing()) {
                // Voltar no editor: guarda e volta ao jogo (como Concluir).
                if (!down) {
                    pad.setEditing(false);
                    Native.setPaused(false);
                    layoutPad();
                }
                return true;
            }
            if (!down && e.getRepeatCount() == 0) openMenu("");
            return true;
        }
        if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_POWER || code == KeyEvent.KEYCODE_VOLUME_UP
                || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        int b = button(code);
        if (b < 0) return true;
        if (e.getRepeatCount() > 0) return true;
        int port = portFor(e.getDevice());
        // Select + Start juntos abrem o menu.
        if (b == Native.SELECT) selectDown = down;
        if (b == Native.START) startDown = down;
        if (selectDown && startDown) {
            selectDown = startDown = false;
            openMenu("");
            return true;
        }
        if (b == Native.R2 && console.buttons.length > 0 && !uses(Native.R2)) {
            ffHold = down;
            Native.setHotkeys(ffHold, rwHold);
            return true;
        }
        if (b == Native.L2 && !uses(Native.L2)) {
            rwHold = down && settings.optBoolean("rewind");
            Native.setHotkeys(ffHold, rwHold);
            if (down && !settings.optBoolean("rewind")) toast(tr("Rebobinar está desligado (Configurações → Jogo).", "Rebobinar está apagado (Configuración → Juego)."));
            return true;
        }
        if (down) keyBits[port] |= 1 << b;
        else keyBits[port] &= ~(1 << b);
        push(port);
        return true;
    }

    private boolean uses(int b) {
        for (int x : console.buttons) if (x == b) return true;
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (gl == null || menuOpen || (e.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK || e.getAction() != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(e);
        }
        InputDevice d = e.getDevice();
        int port = portFor(d);
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        int hat = 0;
        if (hx < -0.5f) hat |= 1 << Native.LEFT;
        if (hx > 0.5f) hat |= 1 << Native.RIGHT;
        if (hy < -0.5f) hat |= 1 << Native.UP;
        if (hy > 0.5f) hat |= 1 << Native.DOWN;
        hatBits[port] = hat;

        float dz = settings.optInt("deadzone", 25) / 100f;
        float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
        float rx = e.getAxisValue(MotionEvent.AXIS_Z), ry = e.getAxisValue(MotionEvent.AXIS_RZ);
        Native.setAnalog(port, 0, axis(x, dz), axis(y, dz));
        Native.setAnalog(port, 1, axis(rx, dz), axis(ry, dz));
        int stick = 0;
        if (settings.optBoolean("stickDpad", true)) {
            float t = Math.max(0.5f, dz);
            if (x < -t) stick |= 1 << Native.LEFT;
            if (x > t) stick |= 1 << Native.RIGHT;
            if (y < -t) stick |= 1 << Native.UP;
            if (y > t) stick |= 1 << Native.DOWN;
        }
        stickBits[port] = stick;

        float lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        float rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        boolean ff = rt > 0.5f, rw = lt > 0.5f && settings.optBoolean("rewind");
        if (ff != ffHold || rw != rwHold) {
            ffHold = ff;
            rwHold = rw;
            Native.setHotkeys(ffHold, rwHold);
        }
        push(port);
        return true;
    }

    private static int axis(float v, float dz) {
        if (Math.abs(v) < dz) return 0;
        return Math.max(-32767, Math.min(32767, Math.round(v * 32767)));
    }

    private static final int DPAD = (1 << Native.UP) | (1 << Native.DOWN) | (1 << Native.LEFT) | (1 << Native.RIGHT);
    private static final int CBTN = (1 << Native.C_UP) | (1 << Native.C_DOWN) | (1 << Native.C_LEFT) | (1 << Native.C_RIGHT);

    private void push(int port) {
        int bits = keyBits[port] | hatBits[port] | stickBits[port] | webBits[port] | (port == 0 ? touchBits : 0);
        if (console.stick) {
            // N64, GameCube e Wii andam pelo analógico: a cruz (controle remoto, tela, Onix Controle)
            // empurra o analógico esquerdo até o fim. O analógico de verdade do controle continua.
            int d = bits & DPAD;
            bits &= ~DPAD;
            if (d != 0 || dpadStick[port]) {
                Native.setAnalog(port, 0, dir(d, Native.LEFT, Native.RIGHT), dir(d, Native.UP, Native.DOWN));
                dpadStick[port] = d != 0;
            }
        }
        int cb = bits & CBTN;
        bits &= ~CBTN;
        if (cb != 0 || cStick[port]) {
            Native.setAnalog(port, 1, dir(cb, Native.C_LEFT, Native.C_RIGHT), dir(cb, Native.C_UP, Native.C_DOWN));
            cStick[port] = cb != 0;
        }
        boolean jump = (bits & ((1 << Native.B) | (1 << Native.A))) != 0;
        if (demoGame && port == 0 && jump != demoJumpDown) {
            demoJumpDown = jump;
            final int n = jump ? ++demoJumps : 0;
            if (n == 1 || n == 3 || n == 5) ui.post(new Runnable() {
                @Override
                public void run() {
                    demoConquista(n == 1 ? COMUM : n == 3 ? RARA : PLATINA);
                }
            });
        }
        Native.setButtons(port, bits);
    }

    /** Como cada conquista aparece: textos, cores e o quanto ela se mexe na entrada. */
    private static final class DemoTier {
        String kicker, title, sub, pts, icon;
        int[] colors;          // borda e brilho (o primeiro se repete no fim, para a borda fechar o círculo)
        int accent, badgeInk;  // cor do título pequeno e do selo de pontos; cor do texto do selo
        float scale = 1, glowRest, glowPeak = 1;
        int stay, anim, sparks, waves = 1, rings, turns = 1, pad = 22;
        boolean flash, spin, twinkle, glints;
        int decor;             // enfeites parados da borda: 0 nenhum, 1 rara, 2 platina
    }

    private DemoTier demoTier(int tier) {
        DemoTier d = new DemoTier();
        if (tier == COMUM) {
            d.kicker = tr("CONQUISTA DESBLOQUEADA", "LOGRO DESBLOQUEADO");
            d.title = tr("Primeiro pulo", "Primer salto");
            d.sub = tr("Dê o primeiro pulo do Mario", "Da el primer salto de Mario");
            d.pts = "10 pts";
            d.icon = "⭐";   // estrela
            d.colors = new int[] { Color.rgb(255, 179, 71), Color.rgb(255, 216, 74), Color.rgb(255, 61, 154), Color.rgb(255, 179, 71) };
            d.accent = Color.rgb(255, 179, 71);
            d.badgeInk = Color.rgb(40, 22, 0);
            d.glowRest = 0.35f;
            d.glowPeak = 0.7f;
            d.stay = 5000;
            d.anim = 1300;
            d.sparks = 14;
        } else if (tier == RARA) {
            d.kicker = tr("CONQUISTA RARA · 3,2% DOS JOGADORES", "LOGRO RARO · 3,2% DE LOS JUGADORES");
            d.title = tr("Salto de fé", "Salto de fe");
            d.sub = tr("Dê 3 pulos com o Mario", "Da 3 saltos con Mario");
            d.pts = "50 pts";
            d.icon = "🏆";   // troféu
            d.colors = new int[] { Color.rgb(255, 61, 154), Color.rgb(61, 225, 255), Color.rgb(167, 139, 250), Color.rgb(255, 61, 154) };
            d.accent = Color.rgb(74, 222, 128);
            d.badgeInk = Color.rgb(10, 30, 20);
            d.glowRest = 0.55f;
            d.stay = 7000;
            d.anim = 1900;
            d.sparks = 40;
            d.flash = true;
            d.decor = 1;
        } else {
            d.kicker = tr("PLATINA · 100% COMPLETO", "PLATINO · 100% COMPLETADO");
            d.title = tr("Rei dos pulos", "Rey de los saltos");
            d.sub = tr("Dê 5 pulos com o Mario", "Da 5 saltos con Mario");
            d.pts = "+400 pts";
            d.icon = "👑";   // coroa
            d.colors = new int[] { Color.WHITE, Color.rgb(159, 232, 255), Color.rgb(255, 211, 107), Color.rgb(255, 154, 213), Color.WHITE };
            d.accent = Color.rgb(201, 241, 255);
            d.badgeInk = Color.rgb(16, 24, 44);
            d.scale = 1.15f;
            d.glowRest = 0.7f;
            d.stay = 60000;
            d.anim = 3400;
            d.sparks = 70;
            d.waves = 2;
            d.rings = 2;
            d.turns = 2;
            d.pad = 34;
            d.flash = d.spin = d.twinkle = d.glints = true;
            d.decor = 2;
        }
        return d;
    }

    /** Monta o cartão da conquista (escondido) e carrega os três sons. O texto e as cores mudam a cada nível. */
    private void demoSetup(FrameLayout root) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        demoRow = card;
        FrameLayout box = new FrameLayout(this);   // moldura com os cantos redondos: recorta o brilho
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(235, 14, 12, 34));
        bg.setCornerRadius(14 * dp);
        box.setBackground(bg);
        box.setClipToOutline(true);

        TextView icon = new TextView(this);
        icon.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable ibg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[] { Color.rgb(32, 92, 88), Color.rgb(18, 40, 60) });
        ibg.setCornerRadius(10 * dp);
        icon.setBackground(ibg);
        card.addView(icon, new LinearLayout.LayoutParams(0, 0));
        demoIcon = icon;

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Math.round(12 * dp), 0, 0, 0);
        demoKicker = new TextView(this);
        demoKicker.setLetterSpacing(0.08f);
        demoKicker.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        demoTitle = new TextView(this);
        demoTitle.setTextColor(Color.WHITE);
        demoTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        demoSub = new TextView(this);
        demoSub.setTextColor(Color.rgb(170, 170, 195));
        txt.addView(demoKicker);
        txt.addView(demoTitle);
        txt.addView(demoSub);
        card.addView(txt);

        TextView pts = new TextView(this);
        pts.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.setMargins(Math.round(14 * dp), 0, 0, 0);
        card.addView(pts, pp);
        demoPts = pts;
        box.addView(card, new FrameLayout.LayoutParams(-2, -2));
        View shine = new View(this);   // faixa de luz que atravessa o cartão
        shine.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.TRANSPARENT, Color.argb(70, 160, 255, 235), Color.TRANSPARENT }));
        shine.setRotation(18);
        shine.setVisibility(View.INVISIBLE);
        // Altura fixa igual à do cartão: com "ocupar a altura toda", a moldura esticava até a altura da tela.
        final FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Math.round(70 * dp), 1);
        box.addView(shine, sp);
        card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (sp.height != b - t) {
                    sp.height = b - t;
                    demoShine.setLayoutParams(sp);
                }
            }
        });
        demoShine = shine;

        // A janela "fura" a área do jogo do tamanho do que está na tela quando o aviso surge, e o que passa para
        // fora é cortado. O palco (quase invisível) reserva a área do deslize e do brilho; o cartão anda só dentro dele.
        DemoStage stage = new DemoStage(this);
        stage.setBackgroundColor(Color.argb(1, 0, 0, 0));
        stage.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.END));
        stage.setVisibility(View.GONE);
        root.addView(stage, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        demoStage = stage;
        demoCard = box;

        // Os sons ficam em arquivos prontos e tocam pelo SoundPool (não mexe no som do jogo).
        demoSound = new android.media.SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        String[] names = { "conquista-comum.ogg", "conquista-rara.ogg", "conquista-platina.ogg" };
        for (int i = 0; i < names.length; i++) {
            try {
                File f = new File(getCacheDir(), names[i]);
                java.io.InputStream in = getAssets().open(names[i]);
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0;) out.write(b, 0, n);
                out.close();
                in.close();
                demoSoundId[i] = demoSound.load(f.getPath(), 1);
            } catch (Exception e) {
                demoSoundId[i] = 0;
            }
        }
    }

    /** Põe os textos, cores e tamanhos do nível no cartão (com ele escondido, para a tela medir de novo). */
    private void demoApply(DemoTier d) {
        float dp = getResources().getDisplayMetrics().density, k = d.scale * 0.85f;   // 85%: um pouco menor
        demoRow.setPadding(Math.round(12 * k * dp), Math.round(10 * k * dp), Math.round(18 * k * dp), Math.round(10 * k * dp));
        TextView icon = (TextView) demoIcon;
        icon.setText(d.icon);
        icon.setTextSize(22 * k);
        int is = Math.round(46 * k * dp);
        icon.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        demoKicker.setText(d.kicker);
        demoKicker.setTextColor(d.accent);
        demoKicker.setTextSize(10 * k);
        demoTitle.setText(d.title);
        demoTitle.setTextSize(16 * k);
        demoSub.setText(d.sub);
        demoSub.setTextSize(12 * k);
        TextView pts = (TextView) demoPts;
        pts.setText(d.pts);
        pts.setTextColor(d.badgeInk);
        pts.setTextSize(11 * k);
        pts.setPadding(Math.round(8 * dp), Math.round(2 * dp), Math.round(8 * dp), Math.round(2 * dp));
        android.graphics.drawable.GradientDrawable pbg = new android.graphics.drawable.GradientDrawable();
        pbg.setColor(d.accent);
        pbg.setCornerRadius(20 * dp);
        pts.setBackground(pbg);
        // Celular: o cartão ocupa no máximo 80% da largura da tela (em pé, o texto quebra em duas linhas em vez de
        // crescer). Na TV sobra espaço e nada muda.
        int pad = Math.round(d.pad * dp), slide = Math.round((mobile ? 32 : DEMO_SLIDE) * dp);
        demoStage.setPadding(slide + pad, pad, pad, pad);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int card = Math.round((mobile ? 0.8f : 0.9f) * dm.widthPixels) - slide - 2 * pad;
        int text = card - is - Math.round((12 + 18 + 12 + 14 + 8) * k * dp) - Math.round((16 + 34 * k) * dp + d.pts.length() * 6 * k * dp);
        for (TextView v : new TextView[] { demoKicker, demoTitle, demoSub }) {
            v.setMaxWidth(Math.max(Math.round(120 * dp), text));
            v.setMaxLines(2);
        }
        demoStage.tier = d;
    }

    /** Mostra a conquista do nível. Se outra estiver na tela, ela sai rápido e a nova entra. */
    private void demoConquista(final int tier) {
        if (menuOpen || quitting || demoCard == null) return;
        final int n = ++demoShown;
        if (demoStage.getVisibility() == View.VISIBLE) {
            demoStage.stop();
            demoStage.animate().cancel();
            demoStage.animate().alpha(0).setDuration(180).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (n == demoShown) demoShow(tier, n);
                }
            }).start();
        } else demoShow(tier, n);
    }

    /** Entrada: o cartão desliza (a platina também cresce), o ícone pula, os pontos pipocam e o brilho passa.
     *  O resto (brilho em volta, borda, clarão, faíscas) é o palco que desenha. Depois fica parado até sair. */
    private void demoShow(final int tier, final int n) {
        final DemoTier d = demoTier(tier);
        demoStage.setVisibility(View.GONE);
        demoApply(d);
        final float dx = (mobile ? 32 : DEMO_SLIDE) * getResources().getDisplayMetrics().density;
        for (View v : new View[] { demoCard, demoIcon, demoPts, demoShine }) v.animate().cancel();
        demoCard.setAlpha(0);
        demoCard.setTranslationX(dx);
        float s0 = d.spin ? 0.85f : 1;
        demoCard.setScaleX(s0);
        demoCard.setScaleY(s0);
        demoIcon.setScaleX(0.3f);
        demoIcon.setScaleY(0.3f);
        demoIcon.setRotation(d.spin ? -360 : -25);
        demoPts.setScaleX(0);
        demoPts.setScaleY(0);
        demoShine.setVisibility(View.INVISIBLE);
        demoStage.animate().cancel();
        demoStage.setAlpha(1);
        demoStage.setVisibility(View.VISIBLE);
        demoCard.animate().alpha(1).translationX(0).scaleX(1).scaleY(1).setStartDelay(0).setDuration(d.spin ? 520 : 380)
                .setInterpolator(d.spin ? new android.view.animation.OvershootInterpolator(1.4f) : new android.view.animation.DecelerateInterpolator(2))
                .withLayer().start();
        demoIcon.animate().scaleX(1).scaleY(1).rotation(0).setStartDelay(180).setDuration(d.spin ? 800 : 420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3)).start();
        demoPts.animate().scaleX(1).scaleY(1).setStartDelay(d.spin ? 700 : 420).setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator(4)).start();
        demoStage.play();
        for (int at : d.glints ? new int[] { 500, 1600 } : new int[] { 500 }) ui.postDelayed(new Runnable() {   // já medido
            @Override
            public void run() {
                if (n == demoShown) demoSweep();
            }
        }, at);
        int id = demoSoundId[tier];
        if (id != 0) demoSound.play(id, 1, 1, 1, 0, 1);
        if (d.glints) ui.postDelayed(new Runnable() {   // platina: um reflexo de vez em quando, enquanto está na tela
            @Override
            public void run() {
                if (n != demoShown || demoStage.getVisibility() != View.VISIBLE) return;
                demoStage.glint();
                demoSweep();
                ui.postDelayed(this, 8000);
            }
        }, 8000);
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (n != demoShown) return;
                demoStage.stop();
                demoCard.animate().translationX(dx).setStartDelay(0).setDuration(320)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(2)).withLayer().start();
                demoStage.animate().alpha(0).setDuration(320).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (n == demoShown) demoStage.setVisibility(View.GONE);
                    }
                }).start();
            }
        }, d.stay);
    }

    /** A faixa de luz atravessa o cartão uma vez. */
    private void demoSweep() {
        demoShine.animate().cancel();
        demoShine.setTranslationX(-demoShine.getWidth() * 1.5f);
        demoShine.setVisibility(View.VISIBLE);
        demoShine.animate().translationX(demoCard.getWidth() + demoShine.getWidth()).setStartDelay(0).setDuration(650)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        demoShine.setVisibility(View.INVISIBLE);
                    }
                }).start();
    }

    /** Palco da conquista: brilho colorido em volta, borda em degradê que gira, clarão, ondas, faíscas em pixel e
     *  estrelinhas. Desenhado à mão e só anima na entrada (tier.anim ms) e nos reflexos da platina; no resto do tempo
     *  fica parado e não pesa no jogo. */
    private final class DemoStage extends FrameLayout {
        final String[] ALIEN = { "..#..#..", ".######.", "##.##.##", "########", ".#.##.#.", "#......#" };
        final String[] ALIEN2 = { "...##...", ".######.", "#.####.#", "########", "..#..#..", ".#.##.#." };
        final String[] CROWN = { "#...#...#", "##.###.##", "#########", "#.#.#.#.#", "#########", ".#######.", "" };
        static final int BURST = 250, WAVE2 = 950, MAX = 80, GLINT = 1000;
        final float dp = getResources().getDisplayMetrics().density;
        final android.graphics.Paint paint = new android.graphics.Paint();
        final android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Paint border = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Matrix m = new android.graphics.Matrix();
        final android.graphics.RectF r = new android.graphics.RectF();
        final float[] vx = new float[MAX], vy = new float[MAX], life = new float[MAX], size = new float[MAX];
        final float[] tx = new float[12], ty = new float[12];
        final int[] col = new int[MAX];
        DemoTier tier, glowTier;
        android.graphics.SweepGradient sweep;
        int glints;
        Bitmap glow, decor;
        long t0 = -1, g0 = -1;
        android.animation.ValueAnimator anim;

        DemoStage(Context c) {
            super(c);
            border.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStyle(android.graphics.Paint.Style.STROKE);
        }

        /** Começa a entrada: as faíscas saem do troféu. */
        void play() {
            java.util.Random rnd = new java.util.Random();
            int[] c = tier.colors;
            for (int i = 0; i < tier.sparks; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, v = (90 + rnd.nextDouble() * (tier.spin ? 380 : 260)) * dp;
                vx[i] = (float) (Math.cos(a) * v);
                vy[i] = (float) (Math.sin(a) * v) - 60 * dp;
                life[i] = 0.7f + rnd.nextFloat() * (tier.spin ? 1.1f : 0.7f);
                size[i] = Math.round((2 + rnd.nextInt(tier.spin ? 4 : 3)) * dp);
                col[i] = i % 5 == 0 ? Color.WHITE : c[i % (c.length - 1)];
            }
            for (int i = 0; i < tx.length; i++) {   // estrelinhas da platina: em volta do cartão
                tx[i] = rnd.nextFloat();
                ty[i] = rnd.nextFloat();
            }
            t0 = android.os.SystemClock.uptimeMillis();
            g0 = -1;
            glints = 0;
            run(tier.anim);
        }

        /** Platina parada na tela: o brilho pulsa e a borda dá um quarto de volta. */
        void glint() {
            glints++;
            g0 = android.os.SystemClock.uptimeMillis();
            run(GLINT);
        }

        void stop() {
            if (anim != null) anim.cancel();
        }

        private void run(int ms) {
            stop();
            anim = android.animation.ValueAnimator.ofFloat(0, 1).setDuration(ms);
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    invalidate();
                }
            });
            anim.start();
        }

        /** O brilho é desfocado uma vez num bitmap (o desfoque na hora seria pesado). */
        private void makeGlow(int w, int h) {
            int g = Math.round(tier.pad * dp);
            if (glow != null && glowTier == tier && glow.getWidth() == w + 2 * g && glow.getHeight() == h + 2 * g) return;
            glowTier = tier;
            glow = Bitmap.createBitmap(w + 2 * g, h + 2 * g, Bitmap.Config.ARGB_8888);
            android.graphics.Paint gp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            int[] c = tier.colors;
            gp.setShader(new android.graphics.LinearGradient(g, 0, g + w, 0, new int[] { c[0], c[2], c[1] }, null,
                    android.graphics.Shader.TileMode.CLAMP));
            gp.setMaskFilter(new android.graphics.BlurMaskFilter((tier.spin ? 16 : 12) * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
            new Canvas(glow).drawRoundRect(new android.graphics.RectF(g, g, g + w, g + h), 14 * dp, 14 * dp, gp);
            decor = tier.decor == 0 ? null : Bitmap.createBitmap(w + 2 * g, h + 2 * g, Bitmap.Config.ARGB_8888);
            if (decor != null) drawDecor(new Canvas(decor), g, g, w, h);
            sweep = new android.graphics.SweepGradient(0, 0, c, null);
            border.setShader(sweep);
            border.setStrokeWidth((tier.spin ? 3 : 2.5f) * dp);
        }

        @Override
        protected void dispatchDraw(Canvas c) {
            View b = demoCard;
            DemoTier d = tier;
            if (d == null) {
                super.dispatchDraw(c);
                return;
            }
            float bw = b.getWidth() * b.getScaleX(), bh = b.getHeight() * b.getScaleY(), a = b.getAlpha();
            float bx = b.getLeft() + b.getTranslationX() + (b.getWidth() - bw) / 2, by = b.getTop() + (b.getHeight() - bh) / 2;
            long now = android.os.SystemClock.uptimeMillis();
            float t = t0 < 0 ? d.anim : Math.min(d.anim, now - t0);
            float gt = g0 < 0 ? GLINT : Math.min(GLINT, now - g0), gk = (float) Math.sin(Math.PI * gt / GLINT);
            float pad = d.pad * dp, rad = 14 * dp;
            View ic = demoIcon, row = (View) ic.getParent();   // as faíscas saem do ícone (já no lugar final)
            float ox = b.getLeft() + row.getLeft() + ic.getLeft() + ic.getWidth() / 2f, oy = b.getTop() + row.getTop() + ic.getTop() + ic.getHeight() / 2f;
            if (bw > 0) {
                makeGlow(b.getWidth(), b.getHeight());
                float lv;   // acende forte e assenta (a platina pulsa duas vezes)
                if (t < 300) lv = d.glowPeak * t / 300f;
                else if (d.spin && t > 900 && t < 1500) lv = d.glowRest + (1 - d.glowRest) * (float) Math.sin(Math.PI * (t - 900) / 600);
                else if (t < 1200) lv = d.glowPeak - (d.glowPeak - d.glowRest) * (t - 300) / 900f;
                else lv = d.glowRest;
                lv = Math.min(1, lv + 0.3f * gk);
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(255 * a * lv));
                r.set(bx - pad, by - pad, bx + bw + pad, by + bh + pad);
                c.drawBitmap(glow, null, r, paint);
            }
            super.dispatchDraw(c);
            if (bw <= 0) return;
            float k = t / d.anim, ang = 360 * d.turns * (1 - (1 - k) * (1 - k) * (1 - k)) + 90 * (glints == 0 ? 0 : glints - 1 + gt / GLINT);   // cada reflexo gira mais um quarto
            m.setRotate(ang - 90);
            m.postTranslate(bx + bw / 2, by + bh / 2);
            sweep.setLocalMatrix(m);
            border.setAlpha(Math.round(255 * a));
            float in = border.getStrokeWidth() / 2;
            r.set(bx + in, by + in, bx + bw - in, by + bh - in);
            c.drawRoundRect(r, rad - in, rad - in, border);
            if (decor != null) {   // enfeites da borda: desenhados uma vez, aqui só colados
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(255 * a));
                r.set(bx - pad, by - pad, bx + bw + pad, by + bh + pad);
                c.drawBitmap(decor, null, r, paint);
            }
            float age = (t - BURST) / 1000f;
            if (d.flash && age > 0 && age < 0.4f) {   // clarão quando o cartão chega
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round((d.spin ? 120 : 80) * (1 - age / 0.4f) * a));
                r.set(bx, by, bx + bw, by + bh);
                c.drawRoundRect(r, rad, rad, paint);
            }
            for (int w = 0; w < d.rings; w++) {   // ondas que saem do cartão (platina)
                float ra = (t - BURST - w * (WAVE2 - BURST)) / 700f;
                if (ra <= 0 || ra >= 1) continue;
                float grow = pad * (1 - (1 - ra) * (1 - ra));
                line.setColor(w == 0 ? Color.WHITE : d.colors[2]);
                line.setStrokeWidth(2 * dp);
                line.setAlpha(Math.round(220 * (1 - ra)));
                r.set(bx - grow, by - grow, bx + bw + grow, by + bh + grow);
                c.drawRoundRect(r, rad + grow, rad + grow, line);
            }
            for (int w = 0; w < d.waves; w++) {   // faíscas em pixel (quadradinhos, sem suavizar)
                float ag = (t - (w == 0 ? BURST : WAVE2)) / 1000f;
                if (ag <= 0) continue;
                int from = w * d.sparks / d.waves, to = (w + 1) * d.sparks / d.waves;
                float px = w == 0 ? ox : bx + bw / 2, py = w == 0 ? oy : by + bh / 2;   // a 2ª onda sai do meio do cartão
                for (int i = from; i < to; i++) {
                    if (ag >= life[i]) continue;
                    float x = px + vx[i] * ag, y = py + vy[i] * ag + 260 * dp * ag * ag, s2 = size[i] / 2;
                    paint.setColor(col[i]);
                    paint.setAlpha(Math.round(255 * (1 - ag / life[i])));
                    c.drawRect(x - s2, y - s2, x + s2, y + s2, paint);
                    if (i % 7 == 0) star(c, x, y, s2);
                }
            }
            if (d.twinkle && t > 300 && t < d.anim) {   // estrelinhas piscando em volta do cartão
                for (int i = 0; i < tx.length; i++) {
                    float ph = (float) Math.sin(Math.PI * 2 * ((t - 300) / 900f + tx[i]));
                    if (ph <= 0) continue;
                    boolean side = i % 2 == 0;   // metade em cima/embaixo, metade nas laterais
                    float x = side ? bx + tx[i] * bw : (i % 4 == 1 ? bx - pad * 0.45f : bx + bw + pad * 0.45f);
                    float y = side ? (i % 4 == 0 ? by - pad * 0.45f : by + bh + pad * 0.45f) : by + ty[i] * bh;
                    paint.setColor(i % 3 == 0 ? d.colors[2] : Color.WHITE);
                    paint.setAlpha(Math.round(255 * ph * (1 - k * 0.5f)));
                    star(c, x, y, Math.round(1.5f * dp));
                }
            }
        }

        /** Enfeites em pixel da borda (cartão em x, y, w, h dentro do bitmap). Rara: cantoneiras, invasores sobre a
         *  borda, pontinhos e losangos. Platina: cantoneiras duplas douradas com gemas, coroa no alto, gemas, invasores. */
        private void drawDecor(Canvas c, float x, float y, float w, float h) {
            float u = Math.round(2 * dp), o = 4 * dp;   // tamanho do pixel e distância da borda
            int[] k = tier.colors;
            android.graphics.Paint q = new android.graphics.Paint();
            if (tier.decor == 1) {
                int cyan = k[1], pink = k[0], violet = k[2], green = tier.accent;
                corner(c, q, x - o, y - o, 1, 1, u, 9, cyan);
                corner(c, q, x + w + o, y - o, -1, 1, u, 9, pink);
                corner(c, q, x - o, y + h + o, 1, -1, u, 9, pink);
                corner(c, q, x + w + o, y + h + o, -1, -1, u, 9, cyan);
                sprite(c, q, ALIEN, x + w * 0.3f - 4 * u, y - 6 * u - u, u, green);
                sprite(c, q, ALIEN2, x + w * 0.7f - 4 * u, y - 6 * u - u, u, cyan);
                q.setColor(violet);
                q.setAlpha(150);
                for (float px = x + w * 0.22f; px < x + w * 0.78f; px += 5 * u) c.drawRect(px, y + h + o, px + u, y + h + o + u, q);
                gem(c, q, x - o - u, y + h / 2, u, pink, Color.WHITE);
                gem(c, q, x + w + o + u, y + h / 2, u, pink, Color.WHITE);
            } else {
                int gold = k[2], ice = k[1], pink = k[3];
                for (int i = 0; i < 4; i++) {
                    int sx = i % 2 == 0 ? 1 : -1, sy = i < 2 ? 1 : -1;
                    float cx = sx > 0 ? x - o : x + w + o, cy = sy > 0 ? y - o : y + h + o;
                    corner(c, q, cx, cy, sx, sy, u, 14, gold);
                    corner(c, q, cx + sx * 2 * u, cy + sy * 2 * u, sx, sy, u, 7, Color.WHITE);
                    gem(c, q, cx - sx * u, cy - sy * u, u, ice, Color.WHITE);
                }
                sprite(c, q, CROWN, x + w / 2 - 4.5f * u, y - 7 * u - u, u, gold);
                gem(c, q, x + w / 2 - 9 * u, y - 3 * u, u, pink, Color.WHITE);
                gem(c, q, x + w / 2 + 9 * u, y - 3 * u, u, pink, Color.WHITE);
                q.setColor(ice);
                q.setAlpha(170);
                for (float px = x + w * 0.2f; px < x + w * 0.8f; px += 4 * u)
                    if (Math.abs(px - (x + w / 2)) > 5 * u) c.drawRect(px, y + h + o, px + u, y + h + o + u, q);
                gem(c, q, x + w / 2, y + h + o + u / 2, u, gold, Color.WHITE);
                sprite(c, q, ALIEN, x + w * 0.14f - 4 * u, y + h + o + 2 * u, u, ice);
                sprite(c, q, ALIEN2, x + w * 0.86f - 4 * u, y + h + o + 2 * u, u, pink);
                gem(c, q, x - o - u, y + h / 2, u, gold, Color.WHITE);
                gem(c, q, x + w + o + u, y + h / 2, u, gold, Color.WHITE);
            }
        }

        /** Cantoneira em L (n pixels de braço) com um pontinho na diagonal; sx/sy = para onde os braços vão. */
        private void corner(Canvas c, android.graphics.Paint q, float x, float y, int sx, int sy, float u, int n, int color) {
            q.setColor(color);
            for (int i = 0; i < n; i++) {
                cell(c, q, x + sx * i * u, y, u);
                if (i > 0) cell(c, q, x, y + sy * i * u, u);
            }
            cell(c, q, x - sx * 2 * u, y - sy * 2 * u, u);
        }

        /** Losango de 5×5 pixels com o miolo claro, centrado em (x, y). */
        private void gem(Canvas c, android.graphics.Paint q, float x, float y, float u, int color, int core) {
            q.setColor(color);
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -2 + Math.abs(dy); dx <= 2 - Math.abs(dy); dx++) cell(c, q, x + dx * u - u / 2, y + dy * u - u / 2, u);
            q.setColor(core);
            cell(c, q, x - u / 2, y - u / 2, u);
            cell(c, q, x - u / 2 - u, y - u / 2 - u, u);
        }

        private void sprite(Canvas c, android.graphics.Paint q, String[] m, float x, float y, float u, int color) {
            q.setColor(color);
            for (int j = 0; j < m.length; j++)
                for (int i = 0; i < m[j].length(); i++) if (m[j].charAt(i) == '#') cell(c, q, x + i * u, y + j * u, u);
        }

        private void cell(Canvas c, android.graphics.Paint q, float x, float y, float u) {
            c.drawRect(x, y, x + u, y + u, q);
        }

        private void star(Canvas c, float x, float y, float s2) {
            c.drawRect(x - s2 * 3, y - s2 / 2, x + s2 * 3, y + s2 / 2, paint);
            c.drawRect(x - s2 / 2, y - s2 * 3, x + s2 / 2, y + s2 * 3, paint);
        }
    }

    private static int dir(int bits, int minus, int plus) {
        return ((bits >> plus & 1) - (bits >> minus & 1)) * 32767;
    }

    private void releaseAll() {
        for (int p = 0; p < 4; p++) {
            keyBits[p] = hatBits[p] = stickBits[p] = webBits[p] = 0;
            Native.setButtons(p, 0);
            Native.setAnalog(p, 0, 0, 0);
            Native.setAnalog(p, 1, 0, 0);
        }
        ffHold = rwHold = selectDown = startDown = false;
        touchBits = 0;
        touchFast = false;
        if (pad != null) pad.release();
        Native.setHotkeys(false, false);
    }

    /* Configurações mudadas no menu: relê e aplica na hora. */
    void reloadSettings() {
        settings = Config.effective(this, console.id, id);
        readRemap();
        if ("wii".equals(console.id)) ui.post(new Runnable() {
            @Override
            public void run() {
                applyWiiPad();
            }
        });
        queue(new Runnable() {
            @Override
            public void run() {
                applySettings();
                JSONObject core = settings.optJSONObject("core");
                if (core != null) {
                    Iterator<String> it = core.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        Native.setOption(k, core.optString(k));
                    }
                }
            }
        });
    }

    WebView menuView() {
        return menu;
    }

    boolean isLoaded() {
        return loaded;
    }

    boolean isFailed() {
        return failed;
    }

    /* ---------------- OpenGL ES 3 (consoles 3D) ---------------- */

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098, EGL_OPENGL_ES2_BIT = 4, EGL_OPENGL_ES3_BIT = 0x40;

    /** RGB 888 sem profundidade (o núcleo desenha no framebuffer dele); ES 3 se tiver, senão ES 2. */
    private static final class Gles3Config implements GLSurfaceView.EGLConfigChooser {
        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            for (int bit : new int[]{EGL_OPENGL_ES3_BIT, EGL_OPENGL_ES2_BIT}) {
                int[] attrs = {EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_RENDERABLE_TYPE, bit, EGL10.EGL_NONE};
                EGLConfig[] out = new EGLConfig[1];
                int[] n = new int[1];
                if (egl.eglChooseConfig(display, attrs, out, 1, n) && n[0] > 0) return out[0];
            }
            throw new IllegalArgumentException("sem configuração OpenGL ES");
        }
    }

    private static final class Gles3Context implements GLSurfaceView.EGLContextFactory {
        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            for (int v : new int[]{3, 2}) {
                EGLContext c = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, new int[]{EGL_CONTEXT_CLIENT_VERSION, v, EGL10.EGL_NONE});
                if (c != null && c != EGL10.EGL_NO_CONTEXT) return c;
            }
            return EGL10.EGL_NO_CONTEXT;
        }

        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
        }
    }

    /**
     * Jogo do pendrive (UsbDrives): abre direto de lá. O .zip de NES, SNES… vira uma cópia
     * temporária no cache, apagada no próximo jogo (os núcleos não abrem .zip).
     */
    private File usbRom(JSONObject g) {
        File f = new File(g.optString("path"));
        if (!f.isFile()) return null;
        if (!g.optBoolean("zip")) return f;
        File dir = new File(getCacheDir(), "usb-run");
        Files.delete(dir);
        dir.mkdirs();
        try {
            File tmp = new File(dir, "rom");
            String inner = Importer.unzip(f, tmp);
            if (inner == null) return null;
            File out = new File(dir, g.optString("id") + "." + Consoles.extension(inner));
            return tmp.renameTo(out) ? out : null;
        } catch (Exception e) {
            return null;
        }
    }
}
