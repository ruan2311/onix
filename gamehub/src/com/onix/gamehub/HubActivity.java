package com.onix.gamehub;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tela principal do GameHub (biblioteca, consoles, adicionar jogos, configurações), numa
 * WebView. Aberto pela aba Jogos do Onix (com o perfil e o que abrir) ou pelo ícone, para quem
 * instalou só o GameHub.
 *
 * Extras vindos do Onix: profile, profileName, kids, lang ("pt"/"es"), open ("home",
 * "console:<id>", "game:<id>" ou "resume:<id>", que já entra no jogo).
 */
public class HubActivity extends Activity implements Gamepads.Listener, UploadServer.Host, Covers.Listener {

    private static final Pattern OPEN = Pattern.compile("^(home|console:(nes|snes|gba|md)|(game|resume):[0-9a-f]{16})$");
    static final int REQ_GAME = 1;

    WebView web;
    HubBridge bridge;
    Gamepads gamepads;
    UploadServer upload;
    SharedPreferences prefs;
    String profile = "default", profileName = "", lang = "pt", open = "home";
    boolean kids, fromOnix, direct;
    volatile boolean capture;
    volatile int capturePlayer;
    /** Som de navegação e animações do Onix (null: aberto sem o Onix, ou um Onix antigo). */
    Boolean onixSound, onixMotion;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("hub", MODE_PRIVATE);
        readIntent(getIntent());

        web = new WebView(this);
        // Preto, como a abertura: sem troca de cor entre abrir o app e a página aparecer.
        web.setBackgroundColor(Color.BLACK);
        Web.setup(web, this);
        bridge = new HubBridge(this);
        web.addJavascriptInterface(bridge, "Hub");
        web.setFocusable(true);
        setContentView(web);
        web.requestFocus();
        web.loadUrl(Web.BASE + "hub.html");

        gamepads = new Gamepads(this, this);
        upload = new UploadServer(this, this);
        Covers.listener = this;
        Covers.fetchMissing(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Updater.get(HubActivity.this).check(false);
            }
        }, "gamehub-update").start();
        Updater.get(this).setListener(new Updater.Listener() {
            @Override
            public void changed() {
                Web.emit(web, "update", Updater.get(HubActivity.this).json());
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
        Web.emit(web, "open", bridge.init());
    }

    private void readIntent(Intent i) {
        fromOnix = i != null && i.hasExtra("profile");
        if (fromOnix) {
            profile = Files.profile(i.getStringExtra("profile"));
            String n = i.getStringExtra("profileName");
            profileName = n == null ? "" : n.length() > 40 ? n.substring(0, 40) : n;
            kids = i.getBooleanExtra("kids", false);
            String l = i.getStringExtra("lang");
            lang = "es".equals(l) ? "es" : "pt";
            String o = i.getStringExtra("open");
            open = o != null && OPEN.matcher(o).matches() ? o : "home";
            SharedPreferences.Editor ed = prefs.edit().putString("profile", profile).putString("profileName", profileName).putBoolean("kids", kids).putString("lang", lang);
            if (i.hasExtra("sound")) ed.putBoolean("onixSound", i.getBooleanExtra("sound", true));
            if (i.hasExtra("motion")) ed.putBoolean("onixMotion", i.getBooleanExtra("motion", true));
            ed.apply();
        } else {
            // Aberto pelo ícone: o último perfil que o Onix mandou (ou o padrão, sem o Onix).
            profile = Files.profile(prefs.getString("profile", "default"));
            profileName = prefs.getString("profileName", "");
            kids = prefs.getBoolean("kids", false);
            lang = prefs.getString("lang", "es".equals(Locale.getDefault().getLanguage()) ? "es" : "pt");
            open = "home";
        }
        onixSound = prefs.contains("onixSound") ? prefs.getBoolean("onixSound", true) : null;
        onixMotion = prefs.contains("onixMotion") ? prefs.getBoolean("onixMotion", true) : null;
        direct = open.startsWith("resume:");
        if (direct) startGame(open.substring(7), "resume");
    }

    /** Abre o jogo no processo dele (um núcleo travado não derruba o GameHub). */
    boolean startGame(String id, String mode) {
        JSONObject g = Library.game(this, id);
        if (g == null || (kids && !g.optBoolean("kids"))) return false;
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("id", id);
        i.putExtra("mode", mode);
        i.putExtra("profile", profile);
        i.putExtra("kids", kids);
        i.putExtra("lang", lang);
        JSONObject cfg = Config.effective(this, "", null);
        i.putExtra("sounds", ui(cfg.optString("sounds"), onixSound));
        i.putExtra("motion", ui(cfg.optString("motion"), onixMotion));
        startActivityForResult(i, REQ_GAME);
        overridePendingTransition(0, 0);
        return true;
    }

    /** "on"/"off" valem por si; "onix" segue o Onix (ligado se ele nunca mandou). */
    static boolean ui(String v, Boolean onix) {
        if ("on".equals(v)) return true;
        if ("off".equals(v)) return false;
        return onix == null || onix;
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_GAME) return;
        if (direct) {
            // Entrou direto pelo "Continuar jogando" do Onix: ao sair do jogo, volta para o Onix.
            direct = false;
            finish();
            return;
        }
        Web.emit(web, "library", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        gamepads.start();
        Web.emit(web, "resume", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        gamepads.stop();
        upload.stop();
    }

    @Override
    protected void onDestroy() {
        Covers.listener = null;
        upload.stop();
        web.destroy();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Tela de trocar botões: a próxima tecla vai para a página como código do Android.
        if (capture && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            int code = e.getKeyCode();
            if (code != KeyEvent.KEYCODE_HOME && code != KeyEvent.KEYCODE_POWER) {
                capture = false;
                try {
                    Web.emit(web, "key", new JSONObject().put("code", code).put("name", KeyEvent.keyCodeToString(code).replace("KEYCODE_", "")).toString());
                } catch (Exception ignored) {
                }
                return true;
            }
        }
        if (capture && e.getAction() == KeyEvent.ACTION_UP) return true;
        // Definir controle 1/2: o próximo botão de um controle de videogame escolhe o aparelho.
        // O controle remoto passa direto (dá para cancelar com ele).
        if (capturePlayer > 0 && Gamepads.isGamepad(e.getDevice())) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                int p = capturePlayer;
                capturePlayer = 0;
                boolean ok = Players.set(this, p, e.getDevice());
                try {
                    Web.emit(web, "padSet", new JSONObject().put("player", p).put("name", Gamepads.name(e.getDevice())).put("ok", ok).toString());
                } catch (Exception ignored) {
                }
            }
            return true;
        }
        // Botões do controle de videogame viram teclas que a página entende.
        int code = e.getKeyCode();
        int mapped = navKey(code);
        if (mapped != 0 && mapped != code) {
            return super.dispatchKeyEvent(new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), mapped, e.getRepeatCount(), e.getMetaState()));
        }
        return super.dispatchKeyEvent(e);
    }

    /** No menu do GameHub, A confirma, B volta e Start abre (como num console). */
    static int navKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_START:
                return KeyEvent.KEYCODE_ENTER;
            case KeyEvent.KEYCODE_BUTTON_B:
                return KeyEvent.KEYCODE_BACK;
            case KeyEvent.KEYCODE_BUTTON_X:
                return KeyEvent.KEYCODE_MENU;
            case KeyEvent.KEYCODE_BUTTON_L1:
                return KeyEvent.KEYCODE_PAGE_UP;
            case KeyEvent.KEYCODE_BUTTON_R1:
                return KeyEvent.KEYCODE_PAGE_DOWN;
            default:
                return code;
        }
    }

    @Override
    public void onBackPressed() {
        // Quem decide é a página (fecha o painel aberto ou sai); ela chama Hub.exit().
        Web.emit(web, "back", null);
    }

    @Override
    public void gamepadsChanged(int added, int removed) {
        Web.emit(web, "gamepads", Gamepads.list(this).toString());
    }

    @Override
    public void event(JSONObject e) {
        Web.emit(web, "upload", e.toString());
    }

    @Override
    public void coverReady(String id) {
        Web.emit(web, "cover", Web.quote(id));
    }

    void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }
}
