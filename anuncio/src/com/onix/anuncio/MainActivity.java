package com.onix.anuncio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/**
 * Onix Anúncio: a tela é o assets/editor.html (WebView). O Java só abre o seletor de imagens da
 * galeria, abre links no navegador e repassa a chave do GitHub vinda do adb
 * (am start -n com.onix.anuncio/.MainActivity --es chave ghp_…), que a página guarda.
 */
public class MainActivity extends Activity {

    private static final int PICK = 1;
    private WebView web;
    private ValueCallback<Uri[]> escolha;
    private String chaveAdb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0d0f13"));
        getWindow().setNavigationBarColor(Color.parseColor("#0d0f13"));
        chaveAdb = getIntent().getStringExtra("chave");
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0d0f13"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        web.addJavascriptInterface(new Ponte(), "Nativo");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("https://")) abrir(url);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (escolha != null) escolha.onReceiveValue(null);
                escolha = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "Escolha a imagem"), PICK);
                } catch (Exception e) {
                    escolha = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/editor.html");
        // Android 15+ desenha o app atrás das barras do sistema: a moldura afasta a tela delas
        // (e do teclado, que também entra nas margens).
        FrameLayout moldura = new FrameLayout(this);
        moldura.setBackgroundColor(Color.parseColor("#0d0f13"));
        moldura.addView(web);
        moldura.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                        in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
                return in;
            }
        });
        setContentView(moldura);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        String c = i.getStringExtra("chave");
        if (c != null) {
            chaveAdb = c;
            web.evaluateJavascript("window.chaveDoAdb&&chaveDoAdb()", null);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != PICK || escolha == null) return;
        Uri u = res == RESULT_OK && data != null ? data.getData() : null;
        escolha.onReceiveValue(u == null ? null : new Uri[]{u});
        escolha = null;
    }

    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("window.voltar?voltar():false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (!"true".equals(v)) finish();
            }
        });
    }

    private void abrir(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    final class Ponte {
        /** Chave do GitHub enviada pelo adb (uma vez); depois a página guarda no próprio app. */
        @JavascriptInterface
        public String chaveAdb() {
            String c = chaveAdb;
            chaveAdb = null;
            return c == null ? "" : c;
        }

        @JavascriptInterface
        public void abrirLink(final String url) {
            if (url == null || !url.startsWith("https://")) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    abrir(url);
                }
            });
        }
    }
}
