package com.onix.launcher;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Atualizações pela página de Releases do GitHub (ruan2311/onix).
 * Procura uma versão nova a cada 6 h (com o Onix aberto e no PicksJob), baixa a APK da mesma
 * variante instalada (Onix App ou Onix Launcher), confere o SHA-256 publicado pelo GitHub e o
 * pacote, e instala pelo instalador do sistema. O Android sempre pede uma confirmação na tela
 * (apps comuns não instalam nada sozinhos) e só aceita a APK assinada com a mesma chave.
 */
final class Updater {

    private static final String API = "https://api.github.com/repos/ruan2311/onix/releases/latest";
    private static final Pattern TAG = Pattern.compile("^v?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(?:-[a-z0-9.]{1,20})?$");
    private static final Pattern VERSION = Pattern.compile("^(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})");
    private static final Pattern DIGEST = Pattern.compile("^sha256:([0-9a-f]{64})$");
    private static final Pattern DOWNLOAD = Pattern.compile(
            "^https://github\\.com/ruan2311/onix/releases/download/[A-Za-z0-9._-]{1,40}/onix-(app|launcher)\\.apk$");
    // Hosts por onde o download passa (github.com redireciona para o armazenamento de arquivos).
    private static final Pattern DOWNLOAD_HOST = Pattern.compile("^(github\\.com|(release-assets|objects)\\.githubusercontent\\.com)$");
    private static final long CHECK_MS = 6 * 60 * 60 * 1000L;
    /** Atualização automática: pede a confirmação no máximo uma vez por dia (se a pessoa recusou). */
    private static final long PROMPT_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_JSON = 512 * 1024, MAX_APK = 40 * 1024 * 1024;

    private static Updater instance;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final File apk;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable listener;

    // "none" (em dia), "checking", "available", "downloading", "ready", "installing", "error"
    private String state = "none";
    private String error = "";
    private String latest, url, sha256;

    static synchronized Updater get(Context ctx) {
        if (instance == null) instance = new Updater(ctx.getApplicationContext());
        return instance;
    }

    private Updater(Context ctx) {
        this.ctx = ctx;
        prefs = ctx.getSharedPreferences("update", Context.MODE_PRIVATE);
        apk = new File(ctx.getCacheDir(), "update.apk");
        latest = prefs.getString("latest", null);
        url = prefs.getString("url", null);
        sha256 = prefs.getString("sha256", null);
        if (latest != null && newer(latest, current()) && url != null) {
            state = apk.isFile() && sha256 != null && sha256.equals(hash(apk)) ? "ready" : "available";
        } else {
            forget();   // já está na versão do GitHub (ou numa mais nova): nada pendente
        }
    }

    /** Avisa a página (Launcher.onUpdate) sempre que o estado muda. */
    synchronized void setListener(Runnable r) {
        listener = r;
    }

    private void changed() {
        final Runnable r;
        synchronized (this) {
            r = listener;
        }
        if (r != null) ui.post(r);
    }

    private synchronized void set(String s, String err) {
        state = s;
        error = err == null ? "" : err;
    }

    String current() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    boolean auto() {
        return prefs.getBoolean("auto", true);
    }

    void setAuto(boolean on) {
        prefs.edit().putBoolean("auto", on).apply();
    }

    synchronized String json() {
        try {
            JSONObject o = new JSONObject();
            o.put("current", current());
            o.put("latest", "available".equals(state) || "downloading".equals(state) || "ready".equals(state)
                    || "installing".equals(state) ? latest : "");
            o.put("state", state);
            o.put("error", error);
            o.put("auto", auto());
            o.put("checked", prefs.getLong("checked", 0));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    synchronized boolean hasUpdate() {
        return "available".equals(state) || "ready".equals(state);
    }

    synchronized boolean isReady() {
        return "ready".equals(state);
    }

    /** "13.6.0 beta" > "13.5.0 beta"? Compara só os três números. */
    static boolean newer(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        if (x == null || y == null) return false;
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return x[i] > y[i];
        return false;
    }

    private static int[] numbers(String v) {
        Matcher m = VERSION.matcher(v == null ? "" : v.trim().replaceFirst("^v", ""));
        if (!m.find()) return null;
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    /** Variante instalada: o Onix Launcher atende HOME; o Onix App, não. */
    private String variant() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list = ctx.getPackageManager().queryIntentActivities(home, 0);
        for (ResolveInfo ri : list) {
            if (ri.activityInfo != null && ctx.getPackageName().equals(ri.activityInfo.packageName)) return "launcher";
        }
        return "app";
    }

    private void forget() {
        latest = url = sha256 = null;
        prefs.edit().remove("latest").remove("url").remove("sha256").apply();
        if (apk.exists()) apk.delete();
        set("none", null);
    }

    /**
     * Procura a versão mais nova no GitHub. Bloqueia (rede): chamar fora da UI thread.
     * force = botão "Procurar atualizações" (sem esperar as 6 h).
     */
    boolean check(boolean force) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if ("checking".equals(state) || "downloading".equals(state) || "installing".equals(state)) return false;
            long last = prefs.getLong("checked", 0);
            if (!force && now >= last && now - last < CHECK_MS) return false;
            set("checking", null);
        }
        changed();
        try {
            JSONObject rel = new JSONObject(new String(get(API, MAX_JSON), "UTF-8"));
            String tag = rel.getString("tag_name");
            if (!TAG.matcher(tag).matches()) throw new Exception("versão inválida no GitHub");
            String version = tag.replaceFirst("^v", "").replace('-', ' ');
            prefs.edit().putLong("checked", now).apply();
            if (!newer(version, current())) {
                forget();
                changed();
                return true;
            }
            String want = "onix-" + variant() + ".apk", link = null, digest = null;
            JSONArray assets = rel.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (!want.equals(a.optString("name"))) continue;
                link = a.optString("browser_download_url");
                Matcher d = DIGEST.matcher(a.optString("digest"));
                digest = d.matches() ? d.group(1) : null;
            }
            // Sem o SHA-256 publicado não dá para conferir o arquivo: melhor não instalar.
            if (link == null || !DOWNLOAD.matcher(link).matches() || digest == null) throw new Exception("arquivo da atualização não encontrado");
            synchronized (this) {
                boolean same = version.equals(latest) && digest.equals(sha256);
                latest = version;
                url = link;
                sha256 = digest;
                prefs.edit().putString("latest", version).putString("url", link).putString("sha256", digest).apply();
                set(same && apk.isFile() && digest.equals(hash(apk)) ? "ready" : "available", null);
            }
        } catch (Exception e) {
            // Sem internet ou GitHub fora do ar: mantém o que já se sabia.
            set(latest != null && newer(latest, current()) ? (apk.isFile() ? "ready" : "available") : "error", e.getMessage());
        }
        changed();
        return true;
    }

    /** Baixa e confere a APK. Bloqueia (rede): chamar fora da UI thread. */
    boolean download() {
        String link, digest;
        synchronized (this) {
            if (!"available".equals(state) && !"error".equals(state)) return "ready".equals(state);
            if (url == null || sha256 == null) return false;
            link = url;
            digest = sha256;
            set("downloading", null);
        }
        changed();
        File tmp = new File(apk.getPath() + ".tmp");
        try {
            HttpsURLConnection c = open(link);
            try {
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                if (!DOWNLOAD_HOST.matcher(c.getURL().getHost()).matches()) throw new Exception("download fora do GitHub");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                InputStream in = c.getInputStream();
                OutputStream out = new FileOutputStream(tmp);
                try {
                    byte[] buf = new byte[32 * 1024];
                    long total = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_APK) throw new Exception("arquivo grande demais");
                        md.update(buf, 0, n);
                        out.write(buf, 0, n);
                    }
                } finally {
                    out.close();
                    in.close();
                }
                if (!digest.equals(hex(md.digest()))) throw new Exception("o arquivo baixado não confere com o GitHub");
            } finally {
                c.disconnect();
            }
            // Mesmo pacote e versão mais nova; a assinatura o próprio Android confere ao instalar.
            PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(tmp.getPath(), 0);
            if (info == null || !ctx.getPackageName().equals(info.packageName) || !newer(info.versionName, current())) {
                throw new Exception("a APK baixada não é uma versão nova do Onix");
            }
            if (apk.exists()) apk.delete();
            if (!tmp.renameTo(apk)) throw new Exception("sem espaço para a atualização");
            set("ready", null);
            changed();
            return true;
        } catch (Exception e) {
            tmp.delete();
            set("error", e.getMessage());
            changed();
            return false;
        }
    }

    /**
     * Abre a confirmação de instalação do sistema. Retorna "ok", "permission" (abriu a tela para
     * permitir que o Onix instale apps; a pessoa volta e aperta de novo) ou "fail".
     */
    String install(Activity activity) {
        synchronized (this) {
            if (!"ready".equals(state)) return "fail";
        }
        PackageManager pm = ctx.getPackageManager();
        if (Build.VERSION.SDK_INT >= 26 && !canInstall(pm)) {
            try {
                activity.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                        Uri.parse("package:" + ctx.getPackageName())));
                return "permission";
            } catch (Exception ignored) {
                // TV sem essa tela: segue, e o próprio instalador explica o que falta.
            }
        }
        PackageInstaller.Session session = null;
        try {
            PackageInstaller installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(ctx.getPackageName());
            int id = installer.createSession(params);
            session = installer.openSession(id);
            OutputStream out = session.openWrite("onix.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            } finally {
                in.close();
                out.close();
            }
            // FLAG_MUTABLE (API 31): o instalador preenche o resultado no Intent.
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0);
            PendingIntent done = PendingIntent.getBroadcast(ctx, id, new Intent(ctx, UpdateReceiver.class), flags);
            session.commit(done.getIntentSender());
            session = null;
            prefs.edit().putLong("prompted", System.currentTimeMillis()).apply();
            set("installing", null);
            changed();
            return "ok";
        } catch (Exception e) {
            if (session != null) session.abandon();
            set("ready", e.getMessage());
            changed();
            return "fail";
        }
    }

    /** Atualização automática: já pediu a confirmação nas últimas 24 h? */
    boolean promptedRecently() {
        long now = System.currentTimeMillis(), last = prefs.getLong("prompted", 0);
        return now >= last && now - last < PROMPT_MS;
    }

    /** Resposta do instalador (UpdateReceiver): recusada ou com erro, a APK continua pronta. */
    void installFinished(boolean ok, String message) {
        if (ok) return;   // o Android reinicia o Onix na versão nova
        set(isApkReady() ? "ready" : "available", message);
        changed();
    }

    private boolean isApkReady() {
        return apk.isFile() && sha256 != null && sha256.equals(hash(apk));
    }

    private static boolean canInstall(PackageManager pm) {
        try {
            return (Boolean) pm.getClass().getMethod("canRequestPackageInstalls").invoke(pm);
        } catch (Exception e) {
            return true;
        }
    }

    private static HttpsURLConnection open(String link) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(link).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Onix-Launcher");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        return c;
    }

    private static byte[] get(String link, int max) throws Exception {
        HttpsURLConnection c = open(link);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            return WeeklyPicks.readAll(c.getInputStream(), max);
        } finally {
            c.disconnect();
        }
    }

    private static String hash(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            } finally {
                in.close();
            }
            return hex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        return sb.toString();
    }
}
