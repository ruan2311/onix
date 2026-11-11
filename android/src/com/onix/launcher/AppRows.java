package com.onix.launcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Continuar nos seus apps" (opcional, desligado de início): o que o CloudStream e o Stremio
 * publicam na TV pelo TvProvider do Android, como o Projectivy faz. A fileira "Continuar
 * assistindo" (watch_next_program) e os canais com capas (preview_program).
 *
 * Precisa da permissão READ_TV_LISTINGS (o Android pergunta na tela). Com ela, o TvProvider deixa
 * ler os programas "pesquisáveis" de outros apps (o padrão de quem publica). O Onix só lê: não
 * acessa os sites desses apps; as capas vêm do endereço que o próprio app publicou.
 * As tabelas são do Android 8 (API 26); o build usa a base do Android 6, por isso os nomes ficam
 * escritos aqui.
 */
final class AppRows {

    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final int REQUEST = 4711;

    /** Pacote -> nome mostrado. CloudStream tem a versão normal e a "prerelease". */
    private static final String[][] APPS = {
            {"com.lagradost.cloudstream3", "CloudStream"},
            {"com.lagradost.cloudstream3.prerelease", "CloudStream"},
            {"com.stremio.one", "Stremio"},
    };
    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    private static final Uri PREVIEW = Uri.parse("content://android.media.tv/preview_program");
    private static final int MAX_ITEMS = 24, MAX_PER_APP_PREVIEW = 12;
    private static final int POSTER_W = 240, MAX_IMG_BYTES = 3 * 1024 * 1024;
    private static final long FRESH_MS = 60 * 1000L;

    private static AppRows instance;

    static synchronized AppRows get() {
        if (instance == null) instance = new AppRows();
        return instance;
    }

    /** Um item da fileira. O intent fica só aqui: a página só manda o número. */
    private static final class Item {
        String pkg, app, title, sub, intentUri, posterUri;
        int progress = -1;
    }

    private final Map<String, String> posters = new HashMap<String, String>();
    private volatile List<Item> items = new ArrayList<Item>();
    private volatile String json = "[]";
    private volatile long loadedAt;
    private volatile boolean loading;

    private AppRows() {
    }

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 26;
    }

    static boolean allowed(Activity a) {
        return supported() && a.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private static String appName(String pkg) {
        for (String[] a : APPS) if (a[0].equals(pkg)) return a[1];
        return null;
    }

    /** O último resultado na hora; se estiver velho, relê em segundo plano e chama done se mudou. */
    String json(final Activity a, final Runnable done) {
        if (!allowed(a)) return "[]";
        long now = System.currentTimeMillis();
        if (!loading && now - loadedAt > FRESH_MS) {
            loading = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String before = json;
                    try {
                        load(a);
                    } catch (Exception ignored) {
                    } finally {
                        loadedAt = System.currentTimeMillis();
                        loading = false;
                    }
                    if (!json.equals(before) && done != null) done.run();
                }
            }, "onix-approws").start();
        }
        return json;
    }

    /** Força uma releitura na próxima chamada (ao voltar para o Onix, por exemplo). */
    void stale() {
        loadedAt = 0;
    }

    private void load(Activity a) throws Exception {
        ContentResolver cr = a.getContentResolver();
        List<Item> out = new ArrayList<Item>();
        // 1) Continuar assistindo: o mais recente primeiro.
        read(cr, WATCH_NEXT, true, out);
        // 2) Canais com capas (a tela inicial do app), só os que estão à mostra.
        read(cr, PREVIEW, false, out);
        JSONArray arr = new JSONArray();
        List<Item> kept = new ArrayList<Item>();
        for (Item it : out) {
            if (kept.size() >= MAX_ITEMS) break;
            String poster = poster(cr, it.posterUri);
            JSONObject o = new JSONObject();
            o.put("id", kept.size());
            o.put("app", it.app);
            o.put("title", it.title);
            if (it.sub != null) o.put("sub", it.sub);
            if (it.progress >= 0) o.put("progress", it.progress);
            if (poster != null) o.put("poster", poster);
            arr.put(o);
            kept.add(it);
        }
        items = kept;
        json = arr.toString();
    }

    private static void read(ContentResolver cr, Uri uri, boolean watchNext, List<Item> out) {
        Cursor c = null;
        try {
            // Sem seleção: o TvProvider recusa "selection" de quem não tem acesso total.
            c = cr.query(uri, null, null, null, null);
            if (c == null) return;
            List<Item> found = new ArrayList<Item>();
            List<Long> order = new ArrayList<Long>();
            Map<String, Integer> perApp = new HashMap<String, Integer>();
            while (c.moveToNext()) {
                String pkg = str(c, "package_name");
                String app = pkg == null ? null : appName(pkg);
                if (app == null) continue;
                if (!watchNext && num(c, "browsable", 1) == 0) continue;
                String title = trim(str(c, "title"), 100);
                String intentUri = str(c, "intent_uri");
                if (title == null || intentUri == null || intentUri.length() > 4000) continue;
                if (!watchNext) {
                    Integer n = perApp.get(pkg);
                    if (n != null && n >= MAX_PER_APP_PREVIEW) continue;
                    perApp.put(pkg, n == null ? 1 : n + 1);
                }
                Item it = new Item();
                it.pkg = pkg;
                it.app = app;
                it.title = title;
                it.intentUri = intentUri;
                it.posterUri = str(c, "poster_art_uri");
                if (it.posterUri == null) it.posterUri = str(c, "thumbnail_uri");
                long season = num(c, "season_display_number", -1), episode = num(c, "episode_display_number", -1);
                String ep = trim(str(c, "episode_title"), 60);
                if (season > 0 && episode > 0) it.sub = "T" + season + " E" + episode + (ep != null ? " · " + ep : "");
                else if (ep != null) it.sub = ep;
                long dur = num(c, "duration_millis", 0), pos = num(c, "last_playback_position_millis", 0);
                if (watchNext && dur > 0 && pos > 0) it.progress = (int) Math.max(1, Math.min(100, pos * 100 / dur));
                found.add(it);
                order.add(watchNext ? num(c, "last_engagement_time_utc_millis", 0) : 0L);
            }
            if (watchNext) {
                // Ordena pelo último uso (do mais novo para o mais velho).
                for (int i = 1; i < found.size(); i++) {
                    for (int j = i; j > 0 && order.get(j) > order.get(j - 1); j--) {
                        Long t = order.get(j);
                        order.set(j, order.get(j - 1));
                        order.set(j - 1, t);
                        Item x = found.get(j);
                        found.set(j, found.get(j - 1));
                        found.set(j - 1, x);
                    }
                }
            }
            out.addAll(found);
        } catch (Exception ignored) {
            // Sem permissão, TV sem TvProvider ou tabela diferente: a fileira só não aparece.
        } finally {
            if (c != null) c.close();
        }
    }

    /** Capa como data: URL (JPEG pequeno), guardada por endereço. */
    private String poster(ContentResolver cr, String uri) {
        if (uri == null || uri.length() > 2000) return null;
        synchronized (posters) {
            if (posters.containsKey(uri)) return posters.get(uri);
        }
        String data = null;
        try {
            byte[] raw = null;
            if (uri.startsWith("https://") || uri.startsWith("http://")) {
                HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Onix-Launcher");
                try {
                    if (c.getResponseCode() == 200) raw = WeeklyPicks.readAll(c.getInputStream(), MAX_IMG_BYTES);
                } finally {
                    c.disconnect();
                }
            } else if (uri.startsWith("content://") || uri.startsWith("android.resource://")) {
                InputStream in = cr.openInputStream(Uri.parse(uri));
                if (in != null) try {
                    raw = WeeklyPicks.readAll(in, MAX_IMG_BYTES);
                } finally {
                    in.close();
                }
            }
            if (raw != null) data = toJpeg(raw);
        } catch (Exception ignored) {
        }
        synchronized (posters) {
            if (posters.size() > 80) posters.clear();
            posters.put(uri, data);
        }
        return data;
    }

    private static String toJpeg(byte[] raw) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= POSTER_W) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, d);
        if (b == null) return null;
        if (b.getWidth() > POSTER_W) {
            Bitmap s = Bitmap.createScaledBitmap(b, POSTER_W, Math.max(1, b.getHeight() * POSTER_W / b.getWidth()), true);
            if (s != b) b.recycle();
            b = s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 82, out);
        b.recycle();
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /**
     * Abre o item no app que o publicou. O intent vem do TvProvider (escrito pelo próprio app) e
     * é sempre preso ao pacote dele, sem permissões de arquivo nem componente de outro app.
     */
    boolean open(Activity a, int id) {
        List<Item> list = items;
        if (id < 0 || id >= list.size()) return false;
        Item it = list.get(id);
        Intent i;
        try {
            i = it.intentUri.startsWith("intent:")
                    ? Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(it.intentUri));
        } catch (Exception e) {
            i = null;
        }
        if (i != null) {
            i.setSelector(null);
            if (i.getComponent() != null && !it.pkg.equals(i.getComponent().getPackageName())) i.setComponent(null);
            i.setPackage(it.pkg);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                a.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        // O link não abriu: abre só o app.
        PackageManager pm = a.getPackageManager();
        Intent l = pm.getLeanbackLaunchIntentForPackage(it.pkg);
        if (l == null) l = pm.getLaunchIntentForPackage(it.pkg);
        if (l == null) return false;
        l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(l);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return null;
        String s = c.getString(i);
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static long num(Cursor c, String col, long def) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return def;
        try {
            return c.getLong(i);
        } catch (Exception e) {
            return def;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        s = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
