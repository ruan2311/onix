package com.onix.gamehub;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.FrameLayout;

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
    static final int REQ_GAME = 1, REQ_FOLDER = 2, REQ_FILES = 3;

    WebView web;
    HubBridge bridge;
    Gamepads gamepads;
    UploadServer upload;
    RemoteGamepadServer gamepadServer;
    private int prevWebBits = 0;
    SharedPreferences prefs;
    String profile = "default", profileName = "", lang = "pt", open = "home";
    boolean kids, fromOnix, direct, autoDownload;
    volatile boolean capture;
    volatile int capturePlayer;
    /** Som de navegação e animações do Onix (null: aberto sem o Onix, ou um Onix antigo). */
    Boolean onixSound, onixMotion;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("hub", MODE_PRIVATE);
        // Na TV, deitado; no celular, a lista em pé (o jogo gira como a pessoa segurar).
        setRequestedOrientation(Device.mobile(this) ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        readIntent(getIntent());

        web = new WebView(this);
        // Preto, como a abertura: sem troca de cor entre abrir o app e a página aparecer.
        web.setBackgroundColor(Color.BLACK);
        Web.setup(web, this);
        bridge = new HubBridge(this);
        web.addJavascriptInterface(bridge, "Hub");
        web.setFocusable(true);
        if (Device.mobile(this)) {
            // Celular: as telas do GameHub com a barra de status e a de navegação (tela cheia só no
            // jogo). Desde o Android 15 o app desenha por baixo delas, então a página ganha
            // margem do tamanho de cada barra.
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(Color.rgb(13, 7, 34));
                getWindow().setNavigationBarColor(Color.rgb(7, 4, 15));
            }
            FrameLayout box = new FrameLayout(this);
            box.setBackgroundColor(Color.rgb(13, 7, 34));
            box.addView(web, new FrameLayout.LayoutParams(-1, -1));
            box.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                    v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
                    return in;
                }
            });
            setContentView(box);
        } else {
            setContentView(web);
        }
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
                final Updater up = Updater.get(HubActivity.this);
                // Celular: a versão nova já desce sozinha; o hub só pergunta se pode instalar.
                if (Device.mobile(HubActivity.this) && !autoDownload && "available".equals(up.state())) {
                    autoDownload = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            up.download();
                        }
                    }, "gamehub-update").start();
                }
                Web.emit(web, "update", up.json());
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

    /** Celular: escolher a pasta dos jogos (ou arquivos soltos) no seletor do Android. */
    boolean pick(boolean folder) {
        Intent i;
        if (folder) {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        } else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try {
            startActivityForResult(i, folder ? REQ_FOLDER : REQ_FILES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    String gamepadUrl() {
        return gamepadServer != null ? gamepadServer.url() : "";
    }

    int gamepadClients() {
        return gamepadServer != null ? gamepadServer.clientCount() : 0;
    }

    void startGamepadServer() {
        if (Device.mobile(this) || gamepadServer != null) return;
        gamepadServer = new RemoteGamepadServer(this, new RemoteGamepadServer.Listener() {
            @Override
            public void onButtons(int port, int bits) {
                if (port == 0) handleWebButtons(bits);
            }

            @Override
            public void onMenu() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
                    }
                });
            }

            @Override
            public void onFastForward(boolean on) {}

            @Override
            public void onClientsChanged(final int count) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (web != null) {
                                Web.emit(web, "gamepadClients", new JSONObject().put("count", count).toString());
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }, "snes", "Cobalt GameHub");
        gamepadServer.start();
    }

    void stopGamepadServer() {
        if (gamepadServer != null) {
            gamepadServer.stop();
            gamepadServer = null;
        }
    }

    private void handleWebButtons(final int bits) {
        final int pressed = bits & ~prevWebBits;
        final int released = ~bits & prevWebBits;
        prevWebBits = bits;

        if (pressed == 0 && released == 0) return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Seta Cima (bit 4)
                if ((pressed & (1 << 4)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
                if ((released & (1 << 4)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP));

                // Seta Baixo (bit 5)
                if ((pressed & (1 << 5)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
                if ((released & (1 << 5)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN));

                // Seta Esquerda (bit 6)
                if ((pressed & (1 << 6)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                if ((released & (1 << 6)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));

                // Seta Direita (bit 7)
                if ((pressed & (1 << 7)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                if ((released & (1 << 7)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));

                // Botão A (bit 8) -> Enter (Confirmar / Selecionar)
                if ((pressed & (1 << 8)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                if ((released & (1 << 8)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));

                // Botão B (bit 0) -> Back (Voltar)
                if ((pressed & (1 << 0)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
                if ((released & (1 << 0)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));

                // Botão X (bit 9) -> Menu
                if ((pressed & (1 << 9)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                if ((released & (1 << 9)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));

                // Botão START (bit 3) -> Enter
                if ((pressed & (1 << 3)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                if ((released & (1 << 3)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));

                // Botão SELECT (bit 2) -> Tab
                if ((pressed & (1 << 2)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB));
                if ((released & (1 << 2)) != 0) dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB));
            }
        });
    }

    /** Abre o jogo no processo dele (um núcleo travado não derruba o GameHub). */
    boolean startGame(String id, String mode) {
        stopGamepadServer();
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
        if (req == REQ_FOLDER || req == REQ_FILES) {
            if (result != RESULT_OK || data == null) return;
            if (req == REQ_FOLDER && data.getData() != null) {
                FolderImport.tree(this, data.getData(), this);
            } else {
                java.util.List<android.net.Uri> uris = new java.util.ArrayList<android.net.Uri>();
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
                if (!uris.isEmpty()) FolderImport.files(this, uris, this);
            }
            return;
        }
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
        startGamepadServer();
        Web.emit(web, "resume", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        gamepads.stop();
        upload.stop();
        stopGamepadServer();
    }

    @Override
    protected void onDestroy() {
        stopGamepadServer();
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
                    Web.emit(web, "key", new JSONObject().put("code", code).put("name", Gamepads.keyName(code)).toString());
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

    @Override
    public void coversRefreshed(int updated, int total) {
        try {
            Web.emit(web, "covers", new JSONObject().put("updated", updated).put("total", total).toString());
        } catch (Exception ignored) {
        }
    }

    void hideSystemUi() {
        if (Device.mobile(this)) return;   // no celular, as barras do sistema ficam
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }
}
