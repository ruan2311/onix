package com.aurora.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.Collections;

public class MainActivity extends Activity {

    static final String START_URL = "file:///android_asset/index.html";
    static final int BG = Color.rgb(6, 7, 10);

    private WebView webView;
    private AndroidBridge bridge;
    private BroadcastReceiver packageReceiver;
    private ConnectivityManager.NetworkCallback netCallback;
    private Boolean online;   // null = ainda não verificado
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean resumed, appsDirty;

    /**
     * Instalar/atualizar um app dispara vários avisos seguidos (REMOVED, ADDED, CHANGED):
     * espera eles pararem, refaz a lista em segundo plano e só então avisa a página.
     * Com outro app na frente, deixa para quando o launcher voltar.
     */
    private final Runnable appsChanged = new Runnable() {
        @Override
        public void run() {
            if (!resumed) {
                appsDirty = true;
                return;
            }
            appsDirty = false;
            bridge.refresh(new Runnable() {
                @Override
                public void run() {
                    if (webView != null) webView.evaluateJavascript("window.Launcher&&Launcher.onAppsChanged()", null);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        WebView.setWebContentsDebuggingEnabled(false);
        webView = new WebView(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        webView.setBackgroundColor(BG);
        // Sem setLayerType(HARDWARE): o WebView já desenha na GPU e uma camada extra
        // custaria uma cópia de tela inteira por quadro na GPU modesta da TV.
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);           // preferências da página (localStorage)
        s.setAllowFileAccess(false);            // assets continuam acessíveis
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setBlockNetworkLoads(true);
        s.setGeolocationEnabled(false);
        s.setDatabaseEnabled(false);
        s.setSaveFormData(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setTextZoom(100);

        bridge = new AndroidBridge(this);
        bridge.preload();
        webView.setWebViewClient(new SafeClient());
        webView.addJavascriptInterface(bridge, "AndroidBridge");

        setContentView(webView);
        webView.requestFocus();
        webView.loadUrl(START_URL);

        registerPackageReceiver();
        PicksJob.schedule(this);
    }

    /** Se a semana virou, baixa as novas indicações e avisa a página. */
    private void refreshPicks(final boolean networkBack) {
        final WeeklyPicks picks = WeeklyPicks.get(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!picks.refreshIfStale(networkBack)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) webView.evaluateJavascript("window.Launcher&&Launcher.onPicksChanged()", null);
                    }
                });
            }
        }, "onix-picks").start();
    }

    /** Notícias dos games: no máximo a cada 3 h, e só com o launcher aberto. */
    private void refreshNews(final boolean networkBack) {
        final GameNews news = GameNews.get(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!news.refreshIfStale(networkBack)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) webView.evaluateJavascript("window.Launcher&&Launcher.onNewsChanged&&Launcher.onNewsChanged()", null);
                    }
                });
            }
        }, "onix-news").start();
    }

    /**
     * Botão "Atualizar": baixa notícias e indicações agora (sem esperar os prazos) e refaz a lista
     * de apps. No fim avisa a página com o que deu certo: Launcher.onRefreshed(notícias, indicações).
     */
    void forceRefresh() {
        final GameNews news = GameNews.get(this);
        final WeeklyPicks picks = WeeklyPicks.get(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean n = news.refresh(true, true);
                final boolean p = picks.refresh(true, true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) webView.evaluateJavascript("window.Launcher&&Launcher.onRefreshed&&Launcher.onRefreshed(" + n + "," + p + ")", null);
                    }
                });
            }
        }, "onix-refresh").start();
        appsDirty = false;
        bridge.refresh(new Runnable() {
            @Override
            public void run() {
                if (webView != null) webView.evaluateJavascript("window.Launcher&&Launcher.onAppsChanged()", null);
            }
        });
    }

    /** Bloqueia qualquer navegação ou requisição que não seja a página empacotada. */
    private final class SafeClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !START_URL.equals(url);
        }

        // API 24+: sem @Override porque compilamos contra o android.jar da API 23.
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !START_URL.equals(request.getUrl().toString());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (START_URL.equals(url) || url.startsWith("data:")) return null;
            return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden",
                    Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            pushNetwork();
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            // A ponte só responde enquanto a página carregada for a nossa.
            bridge.setTrusted(START_URL.equals(url));
            super.onPageStarted(view, url, favicon);
        }
    }

    /**
     * Conexão: o sistema avisa quando a rede muda (sem verificação periódica, nada roda
     * enquanto nada muda). Só fica registrado com o launcher em primeiro plano; em segundo
     * plano o PicksJob cuida disso.
     */
    private void registerNetworkCallback() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || netCallback != null) return;
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { postNetworkCheck(); }
            @Override public void onLost(Network n) { postNetworkCheck(); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { postNetworkCheck(); }
        };
        try {
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), netCallback);
        } catch (RuntimeException e) {
            netCallback = null;   // alguns firmwares limitam o número de callbacks
        }
        checkNetwork();
    }

    private void unregisterNetworkCallback() {
        if (netCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            if (cm != null) cm.unregisterNetworkCallback(netCallback);
        } catch (RuntimeException ignored) {
        }
        netCallback = null;
    }

    // Os callbacks chegam numa thread do sistema; o estado é tratado na UI thread.
    private void postNetworkCheck() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (netCallback != null) checkNetwork();
            }
        });
    }

    private void checkNetwork() {
        boolean now = isOnline();
        if (online != null && online == now) return;
        boolean cameBack = online != null && now;
        online = now;
        pushNetwork();
        if (cameBack) {   // internet voltou: baixa na hora o que faltou
            refreshPicks(true);
            refreshNews(true);
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            // VALIDATED: o sistema confirmou acesso real à internet (não só Wi-Fi conectado).
            NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void pushNetwork() {
        if (webView == null || online == null) return;
        webView.evaluateJavascript("window.Launcher&&Launcher.onNetwork(" + online + ")", null);
    }

    private void registerPackageReceiver() {
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ui.removeCallbacks(appsChanged);
                ui.postDelayed(appsChanged, 800);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
    }

    /** true se o Onix é a tela inicial padrão (Onix Launcher escolhido como HOME). */
    private boolean isDefaultHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = getPackageManager().resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return !!(window.Launcher&&Launcher.onBack());}catch(e){return false;}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String handled) {
                        // Como tela inicial, Voltar na raiz não fecha nada (o sistema só reabriria o Onix);
                        // como app comum, volta para quem o abriu.
                        if (!"true".equals(handled) && !isDefaultHome()) finish();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        unregisterNetworkCallback();
        if (webView != null) {
            webView.onPause();
            // Com outro app na frente (Netflix, YouTube…), o JS e os timers da página param:
            // a CPU fica toda para o app aberto.
            webView.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (webView != null) {
            webView.resumeTimers();
            webView.onResume();
            // Tempo passado em outro app não conta para a tela de descanso.
            webView.evaluateJavascript("window.Launcher&&Launcher.onResume&&Launcher.onResume()", null);
        }
        if (appsDirty) appsChanged.run();
        refreshPicks(false);
        refreshNews(false);
        registerNetworkCallback();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(appsChanged);
        if (packageReceiver != null) unregisterReceiver(packageReceiver);
        if (webView != null) {
            bridge.setTrusted(false);
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
