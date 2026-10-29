package com.onix.gamehub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

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
 * Toda capa sai em 3:4, a moldura das capas do GameHub. Caixa deitada (SNES americano, Mega
 * Drive japonês…) fica inteira no meio, sobre ela mesma ampliada e desfocada: nada cortado e
 * sem faixas vazias.
 */
final class Covers {

    interface Listener {
        void coverReady(String id);
    }

    private static final String HOST = "https://thumbnails.libretro.com/";
    private static final int MAX_BYTES = 3 * 1024 * 1024, HEIGHT = 420, WIDTH = HEIGHT * 3 / 4, TRIES = 3;
    /** Sobe quando o jeito de montar a capa muda: as capas antigas são refeitas uma vez. */
    private static final int FIT = 2;
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

    /** Ao abrir o GameHub: tenta de novo as capas que faltaram (sem internet na hora, por ex.). */
    static void fetchMissing(Context ctx) {
        JSONArray g = Library.games(ctx);
        long now = System.currentTimeMillis();
        for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o != null && o.optBoolean("cover") && o.optInt("coverFit") < FIT) refitLater(ctx, o.optString("id"));
            if (o == null || o.optBoolean("cover") || o.optInt("coverTries") >= TRIES) continue;
            if (now - o.optLong("coverTried") < RETRY_MS && o.optInt("coverTries") > 0) continue;
            fetchLater(ctx, o.optString("id"));
        }
    }

    /**
     * Capas montadas de outro jeito (antes da moldura 3:4, ou numa versão anterior dela): baixa
     * a original de novo e monta. Sem internet, fica a atual e tenta na próxima abertura.
     */
    private static void refitLater(final Context ctx, final String id) {
        final Context c = ctx.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject game = Library.game(c, id);
                Consoles.Console console = game == null ? null : Consoles.byId(game.optString("console"));
                if (console == null) return;
                boolean ok = false;
                try {
                    byte[] png = download(HOST + enc(console.thumbs) + "/Named_Boxarts/" + enc(safe(game.optString("title"))) + ".png");
                    ok = png != null && save(png, new File(Files.covers(c), id + ".jpg"));
                    if (ok) Library.update(c, id, new JSONObject().put("coverFit", FIT).put("coverTried", System.currentTimeMillis()));
                } catch (Exception ignored) {
                }
                Listener l = listener;
                if (ok && l != null) l.coverReady(id);
            }
        });
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
            if (ok) f.put("coverFit", FIT);
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
        Bitmap fitted = fit34(bmp);
        bmp.recycle();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        fitted.compress(Bitmap.CompressFormat.JPEG, 86, o);
        fitted.recycle();
        return Files.write(out, o.toByteArray());
    }

    private static Bitmap step(Bitmap b, int w, int h, boolean recycle) {
        Bitmap out = Bitmap.createScaledBitmap(b, Math.max(1, w), Math.max(1, h), true);
        if (recycle && out != b) b.recycle();
        return out;
    }

    /**
     * Põe a capa na moldura 3:4. Quase 3:4 (até 10% de diferença): preenche e corta a sobra.
     * Fora disso: a capa inteira no centro e, atrás, ela mesma cobrindo tudo, desfocada (reduz e
     * amplia em passos) e escurecida.
     */
    private static Bitmap fit34(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        float w = src.getWidth(), h = src.getHeight();
        float ratio = (w / h) / (WIDTH / (float) HEIGHT);
        float cover = Math.max(WIDTH / w, HEIGHT / h);
        RectF full = new RectF((WIDTH - w * cover) / 2, (HEIGHT - h * cover) / 2, (WIDTH + w * cover) / 2, (HEIGHT + h * cover) / 2);
        if (ratio > 0.9f && ratio < 1.1f) {
            cv.drawBitmap(src, null, full, p);
            return out;
        }
        // Reduz a ~1/16 em passos e amplia de volta em passos: cada passo bilinear suaviza (de uma
        // vez só, a ampliação deixa quadrados visíveis).
        Bitmap blur = src;
        for (int i = 0; i < 4; i++) blur = step(blur, blur.getWidth() / 2, blur.getHeight() / 2, blur != src);
        for (int i = 0; i < 3; i++) blur = step(blur, blur.getWidth() * 2, blur.getHeight() * 2, true);
        cv.drawBitmap(blur, null, full, p);
        if (blur != src) blur.recycle();
        cv.drawColor(0x8C0E0820);
        float fit = Math.min(WIDTH / w, HEIGHT / h);
        RectF in = new RectF((WIDTH - w * fit) / 2, (HEIGHT - h * fit) / 2, (WIDTH + w * fit) / 2, (HEIGHT + h * fit) / 2);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x66000000);
        cv.drawRect(in.left, in.top + 4, in.right, in.bottom + 8, shadow);
        cv.drawBitmap(src, null, in, p);
        return out;
    }
}
