package com.onix.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.provider.Settings;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    static final String START_URL = "file:///android_asset/index.html";
    static final int BG = Color.rgb(6, 7, 10);

    private WebView webView;
    private AndroidBridge bridge;
    private RemoteServer remote;
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

        // Controle pelo celular: o servidor fica de pé enquanto o Onix está aberto; só obedece com ele na frente.
        remote = new RemoteServer(this, new RemoteServer.Host() {
            @Override
            public void command(String json) {
                js("window.Launcher&&Launcher.onRemote&&Launcher.onRemote(" + AndroidBridge.quote(json) + ")");
            }

            @Override
            public boolean front() {
                return resumed;
            }

            @Override
            public void changed(String event) {
                js("window.Launcher&&Launcher.onRemoteInfo&&Launcher.onRemoteInfo(" + AndroidBridge.quote(event) + ")");
            }
        });
        remote.start();
        bridge = new AndroidBridge(this, remote);
        bridge.preload();
        webView.setWebViewClient(new SafeClient());
        webView.addJavascriptInterface(bridge, "AndroidBridge");

        setContentView(webView);
        webView.requestFocus();
        // A ponte já nasce liberada para a nossa página: o onPageStarted chega depois de os scripts
        // começarem a rodar, e a página aberta antes dele recebia tudo vazio (sem apps, entradas,
        // indicações nem notícias). Qualquer outra URL é bloqueada pelo SafeClient.
        bridge.setTrusted(true);
        webView.loadUrl(START_URL);

        registerPackageReceiver();
        PicksJob.schedule(this);
        Updater.get(this).setListener(new Runnable() {
            @Override
            public void run() {
                if (webView != null) webView.evaluateJavascript("window.Launcher&&Launcher.onUpdate&&Launcher.onUpdate()", null);
            }
        });
    }

    /**
     * Atualizações: procura a cada 6 h; com a automática ligada, baixa sozinho e, com o Onix na
     * frente, abre a confirmação de instalação do sistema (no máximo uma vez por dia se a pessoa
     * recusar). Instalar sem confirmação o Android não deixa apps comuns fazerem.
     */
    private void checkUpdate() {
        final Updater up = Updater.get(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                up.check(false);
                if (!up.auto() || !up.hasUpdate()) return;
                if (!up.download() || up.promptedRecently()) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (resumed && up.isReady()) up.install(MainActivity.this);
                    }
                });
            }
        }, "onix-update").start();
    }

    /** Se a semana virou (ou os serviços escolhidos mudaram), baixa as novas indicações e avisa a página. */
    void refreshPicks(final boolean networkBack) {
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

    /**
     * Botão "Limpar memória": pede ao Android para fechar os apps da grade que estão parados em
     * segundo plano. Nunca o próprio Onix (ele é quem está na tela) nem o teclado; apps tocando
     * música ou com tarefa ativa o Android não fecha. No Android 14 ou mais novo o sistema só deixa
     * cada app fechar a si mesmo, então lá nada muda. No fim avisa a página:
     * Launcher.onMemoryCleared(MB liberados, MB livres, MB no total, limitado pelo Android).
     */
    void clearMemory() {
        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(before);
                final boolean limited = Build.VERSION.SDK_INT >= 34;
                if (!limited) {
                    for (String pkg : backgroundCandidates()) {
                        try { am.killBackgroundProcesses(pkg); } catch (Exception ignored) { }
                    }
                }
                // O Android leva um instante para devolver a memória dos processos fechados.
                try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
                ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(after);
                final long mb = 1024 * 1024;
                final String js = "window.Launcher&&Launcher.onMemoryCleared&&Launcher.onMemoryCleared("
                        + Math.max(0, (after.availMem - before.availMem) / mb) + "," + after.availMem / mb + ","
                        + after.totalMem / mb + "," + limited + ")";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) webView.evaluateJavascript(js, null);
                    }
                });
            }
        }, "onix-memory").start();
    }

    /** Roda um aviso na página (de qualquer thread). */
    void js(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webView != null) webView.evaluateJavascript(code, null);
            }
        });
    }

    /** Apps da grade (Leanback e celular), tirando o próprio Onix e o teclado em uso. */
    private Set<String> backgroundCandidates() {
        Set<String> out = new HashSet<String>();
        PackageManager pm = getPackageManager();
        for (String category : new String[] { "android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER }) {
            try {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) out.add(ri.activityInfo.packageName);
                }
            } catch (Exception ignored) { }
        }
        out.remove(getPackageName());
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null && ime.indexOf('/') > 0) out.remove(ime.substring(0, ime.indexOf('/')));
        return out;
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
            // Rede de segurança: se algo chegou vazio na abertura, a página relê tudo da ponte.
            if (START_URL.equals(url)) view.evaluateJavascript("window.Launcher&&Launcher.onResync&&Launcher.onResync()", null);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            // A ponte só responde enquanto a página carregada for a nossa.
            bridge.setTrusted(START_URL.equals(url));
            super.onPageStarted(view, url, favicon);
        }
    }

    /**
     * Conexão: o sistema avisa quando a rede muda e, com o launcher na frente, o Onix confere
     * a cada 30 s se a internet responde de verdade (o Wi-Fi pode continuar conectado com o
     * roteador sem internet, e o Android demora a perceber). Em segundo plano, nada roda.
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

    private static final long NET_CHECK_MS = 30 * 1000L;
    private boolean probing;
    private final Runnable netTick = new Runnable() {
        @Override
        public void run() {
            checkNetwork();
            ui.postDelayed(this, NET_CHECK_MS);
        }
    };

    /**
     * Sem rede: desconectado na hora. Com rede: a resposta do teste de internet decide
     * (até ela chegar, vale o que o Android diz, para o chip não ficar errado na abertura).
     */
    private void checkNetwork() {
        if (!hasNetwork()) {
            setOnline(false);
            return;
        }
        if (online == null) setOnline(isValidated());
        if (probing) return;
        probing = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = probe();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        probing = false;
                        if (hasNetwork()) setOnline(ok);
                    }
                });
            }
        }, "onix-net").start();
    }

    /** O mesmo teste do Android: um 204 vazio do Google (portal de login ou sem internet não responde 204). */
    private static boolean probe() {
        String[] urls = {"https://connectivitycheck.gstatic.com/generate_204", "https://www.google.com/generate_204"};
        for (String u : urls) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setUseCaches(false);
                c.setInstanceFollowRedirects(false);
                try {
                    if (c.getResponseCode() == 204) return true;
                } finally {
                    c.disconnect();
                }
            } catch (Exception ignored) {
                // tenta o próximo
            }
        }
        return false;
    }

    private void setOnline(boolean now) {
        if (online != null && online == now) return;
        boolean cameBack = online != null && now;
        online = now;
        pushNetwork();
        if (cameBack) {   // internet voltou: baixa na hora o que faltou
            refreshPicks(true);
            refreshNews(true);
        }
    }

    /** Há uma rede ativa (Wi-Fi ou cabo), com ou sem internet de verdade. */
    @SuppressWarnings("deprecation")
    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /** VALIDATED: o sistema confirmou acesso à internet (não só Wi-Fi conectado). */
    @SuppressWarnings("deprecation")
    private boolean isValidated() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        return hasNetwork();
    }

    private void pushNetwork() {
        if (webView == null || online == null) return;
        webView.evaluateJavascript("window.Launcher&&Launcher.onNetwork(" + online + ")", null);
        // O IP da TV pode ter mudado: o QR do controle pelo celular é refeito.
        webView.evaluateJavascript("window.Launcher&&Launcher.onRemoteInfo&&Launcher.onRemoteInfo('network')", null);
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
        ui.removeCallbacks(netTick);
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
        ui.removeCallbacks(netTick);
        ui.postDelayed(netTick, NET_CHECK_MS);
        checkUpdate();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(appsChanged);
        ui.removeCallbacks(netTick);
        Updater.get(this).setListener(null);
        remote.stop();
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
