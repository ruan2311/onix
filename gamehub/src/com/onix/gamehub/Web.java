package com.onix.gamehub;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * As telas são páginas dos assets servidas num endereço próprio (https://hub.local/), sem
 * internet e sem acesso a arquivos: capas e miniaturas passam por aqui, com o caminho validado.
 *   /hub.html /menu.html /common.js /common.css /licenses/*.txt   (assets)
 *   /cover/<id>.jpg                                               (covers/)
 *   /shot/<perfil>/<id>/<slot>.jpg                                (miniaturas dos estados)
 */
final class Web {

    static final String BASE = "https://hub.local/";
    private static final Pattern ASSET = Pattern.compile("^/(hub\\.html|menu\\.html|gamepad\\.html|upload\\.html|common\\.js|common\\.css|touch\\.js|licenses/[a-z0-9_]{1,40}\\.txt)$");
    private static final Pattern COVER = Pattern.compile("^/cover/([0-9a-f]{16})\\.jpg$");
    private static final Pattern SHOT = Pattern.compile("^/shot/([A-Za-z0-9_-]{1,40})/([0-9a-f]{16})/(auto|[1-4])\\.jpg$");
    private static final Map<String, String> TYPES = new HashMap<String, String>();

    static {
        TYPES.put("html", "text/html");
        TYPES.put("js", "application/javascript");
        TYPES.put("css", "text/css");
        TYPES.put("txt", "text/plain");
    }

    private Web() {
    }

    static void setup(final WebView w, final Context c) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                Log.println(
                    msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR ? Log.ERROR : Log.DEBUG,
                    "GameHubWeb",
                    msg.message() + " (" + msg.sourceId() + ":" + msg.lineNumber() + ")"
                );
                return true;
            }
        });
        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return serve(c, req.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return serve(c, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith(BASE);
            }

            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !req.getUrl().toString().startsWith(BASE);
            }
        });
    }

    static WebResourceResponse serve(Context c, String url) {
        if (!url.startsWith(BASE)) return new WebResourceResponse("text/plain", "utf-8", null);   // nada fora daqui
        String path = url.substring(BASE.length() - 1);
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int h = path.indexOf('#');
        if (h >= 0) path = path.substring(0, h);
        try {
            if (ASSET.matcher(path).matches()) {
                String ext = path.substring(path.lastIndexOf('.') + 1);
                return response(TYPES.get(ext), c.getAssets().open(path.substring(1)));
            }
            Matcher m = COVER.matcher(path);
            if (m.matches()) return file(new File(Files.covers(c), m.group(1) + ".jpg"));
            m = SHOT.matcher(path);
            if (m.matches()) return file(new File(Files.states(c, m.group(1), m.group(2)), m.group(3) + ".jpg"));
        } catch (Exception ignored) {
        }
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", null);
        if (Build.VERSION.SDK_INT >= 21) {
            r.setStatusCodeAndReasonPhrase(404, "Not Found");
        }
        return r;
    }

    private static WebResourceResponse file(File f) throws Exception {
        if (!f.isFile()) {
            WebResourceResponse r = new WebResourceResponse("image/jpeg", null, null);
            if (Build.VERSION.SDK_INT >= 21) {
                r.setStatusCodeAndReasonPhrase(404, "Not Found");
            }
            return r;
        }
        return response("image/jpeg", new FileInputStream(f));
    }

    private static WebResourceResponse response(String type, InputStream in) {
        WebResourceResponse r = new WebResourceResponse(type, type.startsWith("image") ? null : "utf-8", in);
        if (Build.VERSION.SDK_INT >= 21) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Cache-Control", type.startsWith("image") ? "no-cache" : "no-store");
            r.setResponseHeaders(headers);
        }
        return r;
    }

    /** Manda um evento para a página: window.onHub(tipo, dados). */
    static void emit(final WebView w, final String type, final String json) {
        if (w == null) return;
        final String js = "window.onHub&&window.onHub(" + quote(type) + "," + (json == null ? "null" : json) + ")";
        w.post(new Runnable() {
            @Override
            public void run() {
                w.evaluateJavascript(js, null);
            }
        });
    }

    static String quote(String s) {
        return org.json.JSONObject.quote(s);
    }
}
