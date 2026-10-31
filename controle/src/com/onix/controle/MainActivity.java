package com.onix.controle;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Onix Controle: a tela é o controle.html dos assets (offline, no visual do Onix) e o Java faz a
 * parte de rede: acha as TVs (Finder), pareia e manda os comandos (Tv) e se atualiza (Updater).
 * TVs pareadas ficam nas preferências por nome (o IP pode mudar), com o pin do certificado.
 */
public class MainActivity extends Activity implements Tv.Listener, Finder.Listener, Updater.Listener {

    private static final int BG = 0xFF0B0C0F;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;
    private Tv tv;
    private Finder finder;
    private SharedPreferences prefs;
    private volatile boolean volumeKeys = true;
    /** Último estado, app na tela, volume e campo de texto: a página lê ao abrir (snapshot). */
    private final Map<String, String> last = new ConcurrentHashMap<String, String>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        hideSystemUi();
        prefs = getSharedPreferences("controle", MODE_PRIVATE);
        volumeKeys = prefs.getBoolean("volumeKeys", true);
        tv = new Tv(this, this);
        finder = new Finder(this, this);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;   // a página nunca sai do controle.html
            }

            // Android 7+ (não existe no SDK 23 do build, por isso sem @Override).
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Onix");
        web.loadUrl("file:///android_asset/controle.html");
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        Updater.get(this).setListener(this);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Object c = getWindow().getClass().getMethod("getInsetsController").invoke(getWindow());
                if (c != null) {
                    Class<?> insetsType = Class.forName("android.view.WindowInsets$Type");
                    int sb = (Integer) insetsType.getMethod("statusBars").invoke(null);
                    int nb = (Integer) insetsType.getMethod("navigationBars").invoke(null);
                    c.getClass().getMethod("hide", int.class).invoke(c, sb | nb);
                    c.getClass().getMethod("setSystemBarsBehavior", int.class).invoke(c, 2); // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        finder.start();
        String last = prefs.getString("last", null);
        JSONObject p = paired().optJSONObject(last == null ? "" : last);
        if (p != null) tv.connect(p.optString("host"), last, p.optString("pin"));
        new Thread(new Runnable() {
            public void run() {
                final Updater up = Updater.get(MainActivity.this);
                up.check(false);
                if (!up.auto() || !up.isReady() || up.promptedRecently()) return;
                ui.post(new Runnable() {
                    public void run() {
                        if (!isFinishing() && up.isReady() && up.canInstall()) {
                            up.install(MainActivity.this);
                        }
                    }
                });
            }
        }, "update").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Fora da tela, nada de conexão aberta com a TV (nem bateria gasta).
        tv.close();
        finder.stop();
    }

    @Override
    protected void onDestroy() {
        Updater.get(this).setListener(null);
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onixBack ? onixBack() : false", new ValueCallback<String>() {
            public void onReceiveValue(String handled) {
                if (!"true".equals(handled)) finish();
            }
        });
    }

    /** Botões de volume do celular mudam o volume da TV (dá para desligar nas configurações). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (volumeKeys && tv.ready() && (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) tv.key(k);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // --- eventos para a página ---

    private void emit(final String type, final Object data) {
        ui.post(new Runnable() {
            public void run() {
                if (web == null) return;
                web.evaluateJavascript("window.onixEvent && onixEvent(" + JSONObject.quote(type) + "," + data + ")", null);
            }
        });
    }

    @Override
    public void on(String type, JSONObject data) {
        if ("paired".equals(type)) {
            String name = data.optString("name");
            savePaired(name, data.optString("host"), data.optString("pin"));
            prefs.edit().putString("last", name).apply();
            emit("tvs", tvsJson());
            return;
        }
        if ("state".equals(type) || "app".equals(type) || "volume".equals(type) || "field".equals(type)) {
            last.put(type, data.toString());
        }
        emit(type, data);
    }

    @Override
    public void found(Map<String, String> tvs) {
        // TV pareada que trocou de IP: atualiza sem pedir novo pareamento.
        JSONObject p = paired();
        for (Map.Entry<String, String> e : tvs.entrySet()) {
            JSONObject t = p.optJSONObject(e.getKey());
            if (t != null && !e.getValue().equals(t.optString("host"))) {
                savePaired(e.getKey(), e.getValue(), t.optString("pin"));
            }
        }
        synchronized (this) {
            nearby = new JSONObject(tvs);
        }
        emit("tvs", tvsJson());
    }

    @Override
    public void changed() {
        emit("update", Updater.get(this).json());
    }

    private JSONObject nearby = new JSONObject();

    private synchronized JSONObject paired() {
        try {
            return new JSONObject(prefs.getString("tvs", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private synchronized void savePaired(String name, String host, String pin) {
        try {
            JSONObject p = paired();
            JSONObject t = new JSONObject();
            t.put("host", host);
            t.put("pin", pin);
            p.put(name, t);
            prefs.edit().putString("tvs", p.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private synchronized void forget(String name) {
        JSONObject p = paired();
        p.remove(name);
        SharedPreferences.Editor ed = prefs.edit().putString("tvs", p.toString());
        if (name.equals(prefs.getString("last", null))) ed.remove("last");
        ed.apply();
    }

    private static boolean pairedHost(JSONObject p, String host) {
        for (Iterator<String> it = p.keys(); it.hasNext(); ) {
            JSONObject t = p.optJSONObject(it.next());
            if (t != null && host.equals(t.optString("host"))) return true;
        }
        return false;
    }

    /** {paired: [{name, host, near}], nearby: [{name, host}], last} */
    private synchronized String tvsJson() {
        try {
            JSONObject o = new JSONObject();
            JSONArray pa = new JSONArray(), ne = new JSONArray();
            JSONObject p = paired();
            for (Iterator<String> it = p.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONObject t = new JSONObject();
                t.put("name", name);
                t.put("host", p.getJSONObject(name).optString("host"));
                t.put("near", nearby.has(name));
                pa.put(t);
            }
            for (Iterator<String> it = nearby.keys(); it.hasNext(); ) {
                String name = it.next();
                if (p.has(name) || pairedHost(p, nearby.getString(name))) continue;
                JSONObject t = new JSONObject();
                t.put("name", name);
                t.put("host", nearby.getString(name));
                ne.put(t);
            }
            o.put("paired", pa);
            o.put("nearby", ne);
            o.put("last", prefs.getString("last", ""));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Ponte com o controle.html (só a página dos assets é carregada nesta WebView). */
    final class Bridge {

        @JavascriptInterface
        public String tvs() {
            return tvsJson();
        }

        @JavascriptInterface
        public String snapshot() {
            return new JSONObject(last).toString();
        }

        @JavascriptInterface
        public void connect(String name, String host) {
            if (name == null || host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return;
            JSONObject t = paired().optJSONObject(name);
            if (t != null) prefs.edit().putString("last", name).apply();
            tv.connect(host, name, t == null ? null : t.optString("pin"));
        }

        /** Pareia de novo uma TV que recusou este celular (o pareamento antigo é trocado). */
        @JavascriptInterface
        public void repair(String name, String host) {
            if (name == null || host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return;
            tv.connect(host, name, null);
        }

        @JavascriptInterface
        public void disconnect() {
            tv.close();
        }

        @JavascriptInterface
        public void code(String c) {
            tv.code(c);
        }

        @JavascriptInterface
        public void forget(String name) {
            if (name == null) return;
            if (name.equals(prefs.getString("last", null))) tv.close();
            MainActivity.this.forget(name);
            emit("tvs", tvsJson());
        }

        @JavascriptInterface
        public void key(int code) {
            tv.key(code);
        }

        @JavascriptInterface
        public void press(int code, boolean down) {
            tv.press(code, down);
        }

        @JavascriptInterface
        public boolean text(String value) {
            return value != null && value.length() <= 200 && tv.text(value);
        }

        @JavascriptInterface
        public void link(String url) {
            if (url != null && url.length() < 300 && url.matches("^[a-z][a-z0-9+.-]*:.*")) tv.link(url);
        }

        @JavascriptInterface
        public boolean openApp(final String pkg) {
            if (pkg == null || pkg.isEmpty()) return false;
            tv.link("gamehub://open");
            try {
                android.content.Intent it = getPackageManager().getLaunchIntentForPackage(pkg);
                if (it != null) {
                    it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }

        @JavascriptInterface
        public void setOrientation(final String mode) {
            ui.post(new Runnable() {
                public void run() {
                    if ("landscape".equals(mode)) {
                        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    } else if ("portrait".equals(mode)) {
                        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                    } else {
                        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    }
                }
            });
        }

        @JavascriptInterface
        public void haptic() {
            ui.post(new Runnable() {
                public void run() {
                    web.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
            });
        }

        @JavascriptInterface
        public void setVolumeKeys(boolean on) {
            volumeKeys = on;
            prefs.edit().putBoolean("volumeKeys", on).apply();
        }

        @JavascriptInterface
        public boolean volumeKeys() {
            return volumeKeys;
        }

        @JavascriptInterface
        public String update() {
            return Updater.get(MainActivity.this).json();
        }

        @JavascriptInterface
        public void setAutoUpdate(boolean on) {
            Updater.get(MainActivity.this).setAuto(on);
            emit("update", Updater.get(MainActivity.this).json());
        }

        @JavascriptInterface
        public boolean autoUpdate() {
            return Updater.get(MainActivity.this).auto();
        }

        @JavascriptInterface
        public void updateCheck() {
            new Thread(new Runnable() {
                public void run() {
                    Updater.get(MainActivity.this).check(true);
                }
            }, "update").start();
        }

        /** Baixa (se precisar) e abre a confirmação de instalação. */
        @JavascriptInterface
        public void updateInstall() {
            new Thread(new Runnable() {
                public void run() {
                    final Updater u = Updater.get(MainActivity.this);
                    if (!u.download()) return;
                    ui.post(new Runnable() {
                        public void run() {
                            String r = u.install(MainActivity.this);
                            emit("installResult", JSONObject.quote(r));
                        }
                    });
                }
            }, "update").start();
        }
    }
}
