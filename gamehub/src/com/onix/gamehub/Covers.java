package com.onix.gamehub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Capas: só a imagem da caixa, do repositório de capas do libretro (thumbnails.libretro.com),
 * pelo nome No-Intro do jogo. Baixa uma vez, reduz e guarda em covers/<id>.jpg.
 * Nada de ano, descrição ou outras informações.
 */
final class Covers {

    interface Listener {
        void coverReady(String id);
    }

    private static final String HOST = "https://thumbnails.libretro.com/";
    private static final int MAX_BYTES = 3 * 1024 * 1024, HEIGHT = 420, TRIES = 3;
    private static final long RETRY_MS = 12 * 60 * 60 * 1000L;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    static volatile Listener listener;

    private Covers() {
    }

    static void fetchLater(final Context ctx, final String id) {
        final Context c = ctx.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                fetch(c, id);
            }
        });
    }

    /** Ao abrir o Game Hub: tenta de novo as capas que faltaram (sem internet na hora, por ex.). */
    static void fetchMissing(Context ctx) {
        JSONArray g = Library.games(ctx);
        long now = System.currentTimeMillis();
        for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o == null || o.optBoolean("cover") || o.optInt("coverTries") >= TRIES) continue;
            if (now - o.optLong("coverTried") < RETRY_MS && o.optInt("coverTries") > 0) continue;
            fetchLater(ctx, o.optString("id"));
        }
    }

    private static void fetch(Context c, String id) {
        JSONObject game = Library.game(c, id);
        if (game == null || game.optBoolean("cover")) return;
        Consoles.Console console = Consoles.byId(game.optString("console"));
        if (console == null) return;
        boolean ok = false;
        try {
            byte[] png = download(HOST + enc(console.thumbs) + "/Named_Boxarts/" + enc(safe(game.optString("title"))) + ".png");
            if (png != null) ok = save(png, new File(Files.covers(c), id + ".jpg"));
        } catch (Exception ignored) {
        }
        try {
            JSONObject f = new JSONObject();
            f.put("cover", ok);
            f.put("coverTries", game.optInt("coverTries") + 1);
            f.put("coverTried", System.currentTimeMillis());
            Library.update(c, id, f);
        } catch (Exception ignored) {
        }
        Listener l = listener;
        if (ok && l != null) l.coverReady(id);
    }

    /** O repositório troca &*\/:`<>?\| por _ nos nomes dos arquivos. */
    static String safe(String title) {
        return title.replaceAll("[&*/:`<>?\\\\|\"]", "_");
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static byte[] download(String link) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(link).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Onix-GameHub");
        try {
            if (c.getResponseCode() != 200) return null;
            if (!c.getURL().getHost().equals("thumbnails.libretro.com")) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (o.size() + n > MAX_BYTES) return null;
                o.write(buf, 0, n);
            }
            in.close();
            return o.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static boolean save(byte[] png, File out) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(png, 0, png.length, b);
        if (b.outWidth <= 0 || b.outHeight <= 0 || b.outWidth > 4096 || b.outHeight > 4096) return false;
        b.inJustDecodeBounds = false;
        b.inSampleSize = 1;
        while (b.outHeight / (b.inSampleSize * 2) >= HEIGHT) b.inSampleSize *= 2;
        Bitmap bmp = BitmapFactory.decodeByteArray(png, 0, png.length, b);
        if (bmp == null) return false;
        if (bmp.getHeight() > HEIGHT) {
            Bitmap s = Bitmap.createScaledBitmap(bmp, Math.max(1, bmp.getWidth() * HEIGHT / bmp.getHeight()), HEIGHT, true);
            bmp.recycle();
            bmp = s;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 86, o);
        bmp.recycle();
        return Files.write(out, o.toByteArray());
    }
}
