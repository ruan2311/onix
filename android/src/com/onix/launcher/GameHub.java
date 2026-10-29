package com.onix.launcher;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

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
 * Onix GameHub, o app opcional de emuladores: instalar, atualizar, abrir e desinstalar pela aba
 * Jogos, e o "Continuar jogando" que ele mostra.
 *
 * As versões do GameHub têm releases próprias no GitHub (tag gamehub-vX.Y.Z, arquivo
 * onix-gamehub.apk), nunca marcadas como "latest" (que é a do Onix). A APK é conferida pelo
 * SHA-256 do GitHub e pelo pacote, e o Android pede confirmação antes de instalar.
 * O resumo vem do provedor do GameHub, protegido por uma permissão de assinatura: só o Onix lê.
 */
final class GameHub {

    static final String PKG = "com.onix.gamehub";
    private static final Uri PROVIDER = Uri.parse("content://com.onix.gamehub.hub");
    private static final String API = "https://api.github.com/repos/ruan2311/onix/releases?per_page=40";
    private static final Pattern TAG = Pattern.compile("^gamehub-v(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})$");
    private static final Pattern VERSION = Pattern.compile("^(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})");
    private static final Pattern DIGEST = Pattern.compile("^sha256:([0-9a-f]{64})$");
    private static final Pattern DOWNLOAD = Pattern.compile(
            "^https://github\\.com/ruan2311/onix/releases/download/gamehub-v[0-9.]{5,14}/onix-gamehub\\.apk$");
    private static final Pattern DOWNLOAD_HOST = Pattern.compile("^(github\\.com|(release-assets|objects)\\.githubusercontent\\.com)$");
    private static final Pattern OPEN = Pattern.compile("^(home|console:(nes|snes|gba|md)|(game|resume):[0-9a-f]{16})$");
    private static final Pattern PROFILE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final int MAX_JSON = 1024 * 1024, MAX_APK = 60 * 1024 * 1024;
    private static final long CHECK_MS = 6 * 60 * 60 * 1000L;

    interface Listener {
        void changed();
    }

    private static GameHub instance;

    private final Context ctx;
    private final File apk;
    private volatile Listener listener;
    // "none", "checking", "downloading", "ready", "installing", "error"
    private String state = "none", error = "", latest, url, sha256;
    private int progress;
    private long checked;

    static synchronized GameHub get(Context ctx) {
        if (instance == null) instance = new GameHub(ctx.getApplicationContext());
        return instance;
    }

    private GameHub(Context ctx) {
        this.ctx = ctx;
        apk = new File(ctx.getCacheDir(), "gamehub.apk");
    }

    void setListener(Listener l) {
        listener = l;
    }

    private void changed() {
        Listener l = listener;
        if (l != null) l.changed();
    }

    String installedVersion() {
        try {
            return ctx.getPackageManager().getPackageInfo(PKG, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Para a aba Jogos: {installed, version, latest, state, progress, error, recent, counts}.
     * O resumo (recentes com capa) só vem com o GameHub instalado e não vem no Modo Infantil
     * para jogos que não foram liberados (o GameHub filtra).
     */
    synchronized String json(String profile, boolean kids) {
        JSONObject o = new JSONObject();
        try {
            String v = installedVersion();
            o.put("installed", v != null);
            o.put("version", v == null ? "" : v);
            o.put("latest", latest != null && (v == null || newer(latest, v)) ? latest : "");
            o.put("state", state);
            o.put("progress", progress);
            o.put("error", error);
            if (v != null) {
                JSONObject s = summary(profile, kids);
                if (s != null) {
                    o.put("recent", s.optJSONArray("recent") != null ? s.optJSONArray("recent") : new JSONArray());
                    o.put("counts", s.optJSONObject("counts") != null ? s.optJSONObject("counts") : new JSONObject());
                }
            }
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    private JSONObject summary(String profile, boolean kids) {
        try {
            Bundle extras = new Bundle();
            extras.putBoolean("kids", kids);
            Bundle b = ctx.getContentResolver().call(PROVIDER, "summary", PROFILE.matcher(profile == null ? "" : profile).matches() ? profile : "default", extras);
            String s = b == null ? null : b.getString("json");
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Abre o GameHub no perfil do Onix (open = home, console:<id>, game:<id> ou resume:<id>).
     * sound/motion: os sons do menu e as animações do Onix, que o GameHub segue por padrão.
     */
    boolean open(Activity a, String profile, String name, boolean kids, String lang, String open, boolean sound, boolean motion) {
        if (installedVersion() == null) return false;
        if (profile == null || !PROFILE.matcher(profile).matches()) return false;
        if (open == null || !OPEN.matcher(open).matches()) open = "home";
        Intent i = new Intent().setClassName(PKG, PKG + ".HubActivity");
        i.putExtra("profile", profile);
        i.putExtra("profileName", name == null ? "" : name.length() > 40 ? name.substring(0, 40) : name);
        i.putExtra("kids", kids);
        i.putExtra("lang", "es".equals(lang) ? "es" : "pt");
        i.putExtra("open", open);
        i.putExtra("sound", sound);
        i.putExtra("motion", motion);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Abre a confirmação do Android para desinstalar (ele apaga os jogos e saves junto). */
    boolean uninstall(Activity a) {
        if (installedVersion() == null) return false;
        try {
            a.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + PKG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /* ---------------- Instalar / atualizar ---------------- */

    /** Procura a versão mais nova do GameHub. Bloqueia (rede): chamar fora da UI thread. */
    boolean check(boolean force) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if ("checking".equals(state) || "downloading".equals(state) || "installing".equals(state)) return false;
            if (!force && now - checked < CHECK_MS && latest != null) return false;
            state = "checking";
            error = "";
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
                    if (!"onix-gamehub.apk".equals(a.optString("name"))) continue;
                    Matcher d = DIGEST.matcher(a.optString("digest"));
                    version = t.group(1) + "." + t.group(2) + "." + t.group(3);
                    link = a.optString("browser_download_url");
                    digest = d.matches() ? d.group(1) : null;
                }
            }
            synchronized (this) {
                checked = now;
                if (version == null || link == null || !DOWNLOAD.matcher(link).matches() || digest == null) {
                    latest = null;
                    state = "error";
                    error = "missing";   // ainda não há GameHub publicado
                } else {
                    latest = version;
                    url = link;
                    sha256 = digest;
                    state = "none";
                }
            }
        } catch (Exception e) {
            synchronized (this) {
                state = "error";
                error = "network";
            }
        }
        changed();
        return true;
    }

    /** Instala (ou atualiza): procura, baixa, confere e abre a confirmação do Android. */
    void install(final Activity a) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                check(true);
                if (!download()) return;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        commit(a);
                    }
                });
            }
        }, "onix-gamehub").start();
    }

    private boolean download() {
        String link, digest;
        synchronized (this) {
            if (latest == null || url == null || sha256 == null) return false;
            String v = installedVersion();
            if (v != null && !newer(latest, v)) {
                state = "none";
                return false;
            }
            link = url;
            digest = sha256;
            state = "downloading";
            progress = 0;
        }
        changed();
        File tmp = new File(apk.getPath() + ".tmp");
        try {
            if (apk.isFile() && digest.equals(hash(apk))) {
                synchronized (this) {
                    state = "ready";
                }
                return true;
            }
            HttpsURLConnection c = open(link);
            try {
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                if (!DOWNLOAD_HOST.matcher(c.getURL().getHost()).matches()) throw new Exception("download fora do GitHub");
                long length = c.getContentLength();
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                InputStream in = c.getInputStream();
                OutputStream o = new FileOutputStream(tmp);
                try {
                    byte[] buf = new byte[64 * 1024];
                    long total = 0, lastTick = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_APK) throw new Exception("arquivo grande demais");
                        md.update(buf, 0, n);
                        o.write(buf, 0, n);
                        long now = System.currentTimeMillis();
                        if (length > 0 && now - lastTick > 400) {
                            lastTick = now;
                            synchronized (this) {
                                progress = (int) (total * 100 / length);
                            }
                            changed();
                        }
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
            if (info == null || !PKG.equals(info.packageName)) throw new Exception("a APK baixada não é o Onix GameHub");
            if (apk.exists()) apk.delete();
            if (!tmp.renameTo(apk)) throw new Exception("sem espaço para baixar o GameHub");
            synchronized (this) {
                state = "ready";
                progress = 100;
            }
            changed();
            return true;
        } catch (Exception e) {
            tmp.delete();
            synchronized (this) {
                state = "error";
                error = e.getMessage() == null ? "download" : e.getMessage();
            }
            changed();
            return false;
        }
    }

    private void commit(Activity activity) {
        PackageManager pm = ctx.getPackageManager();
        if (Build.VERSION.SDK_INT >= 26 && !canInstall(pm)) {
            try {
                activity.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + ctx.getPackageName())));
                synchronized (this) {
                    state = "none";
                }
                changed();
                return;
            } catch (Exception ignored) {
                // sem essa tela: o próprio instalador explica o que falta
            }
        }
        PackageInstaller.Session session = null;
        try {
            PackageInstaller installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(PKG);
            int id = installer.createSession(params);
            session = installer.openSession(id);
            OutputStream o = session.openWrite("onix-gamehub.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                session.fsync(o);
            } finally {
                in.close();
                o.close();
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0);
            PendingIntent done = PendingIntent.getBroadcast(ctx, id, new Intent(ctx, GameHubReceiver.class), flags);
            session.commit(done.getIntentSender());
            session = null;
            synchronized (this) {
                state = "installing";
            }
        } catch (Exception e) {
            if (session != null) session.abandon();
            synchronized (this) {
                state = "error";
                error = e.getMessage() == null ? "install" : e.getMessage();
            }
        }
        changed();
    }

    void installFinished(boolean ok, String message) {
        synchronized (this) {
            state = ok ? "none" : "error";
            error = ok ? "" : message == null ? "" : message;
            if (ok && apk.exists()) apk.delete();
        }
        changed();
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
        c.setRequestProperty("User-Agent", "Onix");
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
                byte[] buf = new byte[64 * 1024];
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
