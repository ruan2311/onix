package com.onix.gamehub;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
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
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;

import javax.microedition.khronos.egl.EGLConfig;
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
    private long started, playedMs;
    private int frames, sramTick;
    private long fpsAt;

    /* Jogadores: id do aparelho -> porta (0..3). O controle remoto e o teclado são o jogador 1. */
    private final SparseIntArray ports = new SparseIntArray();
    private JSONObject players;   // controle 1 e 2 definidos nas Configurações
    boolean sounds = true, motion = true;
    private final int[] keyBits = new int[4], hatBits = new int[4], stickBits = new int[4];
    private boolean ffHold, rwHold, selectDown, startDown;
    private final SparseIntArray remap = new SparseIntArray();

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
        rom = new File(Files.roms(this), game.optString("file"));
        saveDir = Files.saves(this, profile);
        stateDir = Files.states(this, profile, id);
        settings = Config.effective(this, console.id, id);
        readRemap();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        gl = new GLSurfaceView(this);
        gl.setEGLContextClientVersion(2);
        gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(new Renderer());
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(gl, new FrameLayout.LayoutParams(-1, -1));

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
        fps.setVisibility(settings.optBoolean("showFps") ? View.VISIBLE : View.GONE);
        setContentView(root);
        gamepads = new Gamepads(this, this);
        started = System.currentTimeMillis();
    }

    private Gamepads gamepads;

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
            }
        }
    }

    private File sramFile() {
        return new File(saveDir, id + ".srm");
    }

    private File stateFile(String slot) {
        return new File(stateDir, slot + ".state");
    }

    private void load() {
        Native.clearOptions();
        JSONObject core = settings.optJSONObject("core");
        if (core != null) {
            Iterator<String> it = core.keys();
            while (it.hasNext()) {
                String k = it.next();
                Native.setOption(k, core.optString(k));
            }
        }
        String lib = getApplicationInfo().nativeLibraryDir + "/lib" + console.core + ".so";
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
                    b.recycle();
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    t.compress(Bitmap.CompressFormat.JPEG, 82, o);
                    t.recycle();
                    Files.write(new File(stateDir, slot + ".jpg"), o.toByteArray());
                } catch (Exception ignored) {
                }
            }
        }
        return ok;
    }

    boolean loadStateNow(String slot) {
        File f = stateFile(slot);
        return f.isFile() && Native.loadState(f.getPath());
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
        if (added >= 0) {
            InputDevice d = InputDevice.getDevice(added);
            toast(tr("Controle conectado: ", "Control conectado: ") + (d != null ? Gamepads.name(d) : ""));
            if (menuOpen) Web.emit(menu, "gamepads", Gamepads.list(this).toString());
        }
        if (removed >= 0 && ports.indexOfKey(removed) >= 0) {
            ports.delete(removed);
            if (!menuOpen) openMenu("disconnected");
            else Web.emit(menu, "gamepads", Gamepads.list(this).toString());
        }
    }

    private int portFor(InputDevice d) {
        if (d == null || !Gamepads.isGamepad(d)) return 0;
        int dev = d.getId();
        int p = ports.get(dev, -1);
        if (p >= 0) return p;
        boolean[] used = new boolean[4];
        for (int i = 0; i < ports.size(); i++) used[ports.valueAt(i)] = true;
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

    private void push(int port) {
        Native.setButtons(port, keyBits[port] | hatBits[port] | stickBits[port]);
    }

    private void releaseAll() {
        for (int p = 0; p < 4; p++) {
            keyBits[p] = hatBits[p] = stickBits[p] = 0;
            Native.setButtons(p, 0);
            Native.setAnalog(p, 0, 0, 0);
            Native.setAnalog(p, 1, 0, 0);
        }
        ffHold = rwHold = selectDown = startDown = false;
        Native.setHotkeys(false, false);
    }

    /* Configurações mudadas no menu: relê e aplica na hora. */
    void reloadSettings() {
        settings = Config.effective(this, console.id, id);
        readRemap();
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
}
