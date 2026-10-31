package com.onix.controle;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Atualizações do Onix Controle pela página de Releases do GitHub (ruan2311/onix).
 * As versões do controle têm releases próprias, com tag controle-vX.Y.Z e o arquivo
 * onix-controle.apk; elas nunca são marcadas como "latest", que continua sendo a do Onix da TV.
 * Confere o SHA-256 publicado pelo GitHub e o pacote, e instala pelo instalador do sistema
 * (o Android sempre pede confirmação e só aceita a APK assinada com a mesma chave).
 */
final class Updater {

    private static final String API = "https://api.github.com/repos/ruan2311/onix/releases?per_page=40";
    private static final Pattern TAG = Pattern.compile("^controle-v(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})$");
    private static final Pattern VERSION = Pattern.compile("^(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})");
    private static final Pattern DIGEST = Pattern.compile("^sha256:([0-9a-f]{64})$");
    private static final Pattern DOWNLOAD = Pattern.compile(
            "^https://github\\.com/ruan2311/onix/releases/download/controle-v[0-9.]{5,14}/onix-controle\\.apk$");
    private static final Pattern DOWNLOAD_HOST = Pattern.compile("^(github\\.com|(release-assets|objects)\\.githubusercontent\\.com)$");
    private static final long CHECK_MS = 6 * 60 * 60 * 1000L;
    /** Atualização automática: pede a confirmação no máximo uma vez por dia (se a pessoa recusou). */
    private static final long PROMPT_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_JSON = 1024 * 1024, MAX_APK = 20 * 1024 * 1024;

    interface Listener {
        void changed();
    }

    private static Updater instance;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final File apk;
    private volatile Listener listener;

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
            state = isApkReady() ? "ready" : "available";
        } else {
            forget();
        }
    }

    void setListener(Listener l) {
        listener = l;
    }

    private void changed() {
        Listener l = listener;
        if (l != null) l.changed();
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

    synchronized boolean hasUpdate() {
        return "available".equals(state) || "ready".equals(state);
    }

    synchronized boolean isReady() {
        return "ready".equals(state);
    }

    boolean canInstall() {
        if (Build.VERSION.SDK_INT < 26) return true;
        return canInstall(ctx.getPackageManager());
    }

    boolean promptedRecently() {
        long now = System.currentTimeMillis(), last = prefs.getLong("prompted", 0);
        return now >= last && now - last < PROMPT_MS;
    }

    synchronized String json() {
        try {
            JSONObject o = new JSONObject();
            o.put("current", current());
            boolean pending = "available".equals(state) || "downloading".equals(state) || "ready".equals(state) || "installing".equals(state);
            o.put("latest", pending ? latest : "");
            o.put("state", state);
            o.put("error", error);
            o.put("auto", auto());
            o.put("checked", prefs.getLong("checked", 0));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static boolean newer(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        if (x == null || y == null) return false;
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return x[i] > y[i];
        return false;
    }

    private static int[] numbers(String v) {
        Matcher m = VERSION.matcher(v == null ? "" : v.trim());
        if (!m.find()) return null;
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    private void forget() {
        latest = url = sha256 = null;
        prefs.edit().remove("latest").remove("url").remove("sha256").apply();
        if (apk.exists()) apk.delete();
        set("none", null);
    }

    /** Procura uma versão nova. Bloqueia (rede): chamar fora da UI thread. force = botão. */
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
            JSONArray rels = new JSONArray(new String(get(API, MAX_JSON), "UTF-8"));
            String version = null, link = null, digest = null;
            for (int i = 0; i < rels.length() && version == null; i++) {
                JSONObject rel = rels.getJSONObject(i);
                if (rel.optBoolean("draft")) continue;
                Matcher t = TAG.matcher(rel.optString("tag_name"));
                if (!t.matches()) continue;
                JSONArray assets = rel.getJSONArray("assets");
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject a = assets.getJSONObject(j);
                    if (!"onix-controle.apk".equals(a.optString("name"))) continue;
                    Matcher d = DIGEST.matcher(a.optString("digest"));
                    version = t.group(1) + "." + t.group(2) + "." + t.group(3);
                    link = a.optString("browser_download_url");
                    digest = d.matches() ? d.group(1) : null;
                }
            }
            prefs.edit().putLong("checked", now).apply();
            if (version == null || !newer(version, current())) {
                forget();
                changed();
                return true;
            }
            if (link == null || !DOWNLOAD.matcher(link).matches() || digest == null) throw new Exception("arquivo da atualização não encontrado");
            boolean needDownload = false;
            synchronized (this) {
                boolean same = version.equals(latest) && digest.equals(sha256);
                latest = version;
                url = link;
                sha256 = digest;
                prefs.edit().putString("latest", version).putString("url", link).putString("sha256", digest).apply();
                boolean ready = same && isApkReady();
                set(ready ? "ready" : "available", null);
                if (!ready && auto()) needDownload = true;
            }
            changed();
            if (needDownload) {
                download();
            }
            return true;
        } catch (Exception e) {
            set(latest != null && newer(latest, current()) ? (isApkReady() ? "ready" : "available") : "error", e.getMessage());
            changed();
            return false;
        }
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
                OutputStream o = new FileOutputStream(tmp);
                try {
                    byte[] buf = new byte[32 * 1024];
                    long total = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_APK) throw new Exception("arquivo grande demais");
                        md.update(buf, 0, n);
                        o.write(buf, 0, n);
                    }
                } finally {
                    o.close();
                    in.close();
                }
                if (!digest.equals(hex(md.digest()))) throw new Exception("o arquivo baixado não confere com o GitHub");
            } finally {
                c.disconnect();
            }
            PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(tmp.getPath(), 0);
            if (info == null || !ctx.getPackageName().equals(info.packageName) || !newer(info.versionName, current())) {
                throw new Exception("a APK baixada não é uma versão nova do Onix Controle");
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

    /** Abre a confirmação de instalação. "ok", "permission" (abriu a tela de permitir) ou "fail". */
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
                // sem essa tela: o próprio instalador explica o que falta
            }
        }
        PackageInstaller.Session session = null;
        try {
            PackageInstaller installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(ctx.getPackageName());
            int id = installer.createSession(params);
            session = installer.openSession(id);
            OutputStream o = session.openWrite("onix-controle.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                session.fsync(o);
            } finally {
                in.close();
                o.close();
            }
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

    void installFinished(boolean ok, String message) {
        if (ok) return;   // o Android reinicia o app na versão nova
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
        c.setRequestProperty("User-Agent", "Onix-Controle");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        return c;
    }

    private static byte[] get(String link, int max) throws Exception {
        HttpsURLConnection c = open(link);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (o.size() + n > max) throw new Exception("resposta grande demais");
                o.write(buf, 0, n);
            }
            in.close();
            return o.toByteArray();
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
