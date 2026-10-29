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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        /** Fim do "Atualizar capas": quantas capas vieram, de quantos jogos. */
        void coversRefreshed(int updated, int total);
    }

    private static final String HOST = "https://thumbnails.libretro.com/";
    private static final int MAX_BYTES = 3 * 1024 * 1024, HEIGHT = 420, WIDTH = HEIGHT * 3 / 4, TRIES = 3;
    /** Sobe quando o jeito de montar a capa muda: as capas antigas são refeitas uma vez. */
    private static final int FIT = 2;
    private static final long RETRY_MS = 12 * 60 * 60 * 1000L;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    static volatile Listener listener;
    private static volatile boolean refreshing;

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
                    byte[] png = boxart(console, game);
                    ok = png != null && save(png, new File(Files.covers(c), id + ".jpg"));
                    if (ok) Library.update(c, id, new JSONObject().put("coverFit", FIT).put("coverTried", System.currentTimeMillis()));
                } catch (Exception ignored) {
                }
                Listener l = listener;
                if (ok && l != null) l.coverReady(id);
            }
        });
    }

    /**
     * Botão "Atualizar capas": baixa de novo a capa de todos os jogos, inclusive as que já tinham
     * desistido (3 tentativas). Quem não achar capa fica com a que tinha. Devolve quantos jogos
     * entram (-1 se já estiver atualizando); no fim avisa coversRefreshed.
     */
    static int refreshAll(Context ctx) {
        if (refreshing) return -1;
        final Context c = ctx.getApplicationContext();
        final JSONArray g = Library.games(c);
        if (g.length() == 0) return 0;
        refreshing = true;
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                int updated = 0;
                try {
                    for (int i = 0; i < g.length(); i++) {
                        JSONObject game = g.optJSONObject(i);
                        Consoles.Console console = game == null ? null : Consoles.byId(game.optString("console"));
                        if (console == null) continue;
                        String id = game.optString("id");
                        try {
                            byte[] png = boxart(console, game);
                            if (png != null && save(png, new File(Files.covers(c), id + ".jpg"))) {
                                updated++;
                                Library.update(c, id, new JSONObject().put("cover", true).put("coverFit", FIT)
                                        .put("coverTries", 0).put("coverTried", System.currentTimeMillis()));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } finally {
                    refreshing = false;
                }
                Listener l = listener;
                if (l != null) l.coversRefreshed(updated, g.length());
            }
        });
        return g.length();
    }

    private static void fetch(Context c, String id) {
        JSONObject game = Library.game(c, id);
        if (game == null || game.optBoolean("cover")) return;
        Consoles.Console console = Consoles.byId(game.optString("console"));
        if (console == null) return;
        boolean ok = false;
        try {
            byte[] png = boxart(console, game);
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

    /** A caixa do jogo pelo nome exato ou, sem ela, pelo nome parecido (findByName). */
    private static byte[] boxart(Consoles.Console console, JSONObject game) throws Exception {
        String dir = HOST + enc(console.thumbs) + "/Named_Boxarts/";
        byte[] png = download(dir + enc(safe(game.optString("title"))) + ".png");
        if (png != null) return png;
        String other = findByName(console, game);
        return other == null ? null : download(dir + enc(other) + ".png");
    }

    /*
     * Sem capa com o nome exato (outra região, nome fora do No-Intro, jogo renomeado): procura na
     * lista de capas do console pelo nome sem região e versão. A lista vem uma vez por abertura;
     * tudo roda na fila única (POOL), então o mapa não precisa de trava.
     */
    private static final int LIST_BYTES = 8 * 1024 * 1024;
    private static final Map<String, List<String>> LISTS = new HashMap<String, List<String>>();
    private static final Pattern HREF = Pattern.compile("href=\"([^\"?/]+)\\.png\"");
    private static final Pattern TAGS = Pattern.compile("\\s*[(\\[][^)\\]]*[)\\]]");
    private static final Pattern BAD = Pattern.compile("(?i)\\((?:[^)]*\\b)?(beta|proto|demo|sample|hack|pirate|unl|kiosk|program|aftermarket)\\b");
    private static final Pattern REGION = Pattern.compile("\\(([^)]*)\\)");
    private static final String[] REGIONS = {"usa", "world", "europe", "brazil", "japan"};

    private static List<String> listOf(Consoles.Console console) throws Exception {
        List<String> names = LISTS.get(console.id);
        if (names != null) return names;
        byte[] html = download(HOST + enc(console.thumbs) + "/Named_Boxarts/", LIST_BYTES);
        if (html == null) return null;
        names = new ArrayList<String>();
        Matcher m = HREF.matcher(new String(html, "UTF-8"));
        while (m.find()) {
            try {
                // O "+" vem sem codificar na lista; o URLDecoder o trocaria por espaço.
                names.add(URLDecoder.decode(m.group(1).replace("&amp;", "&").replace("+", "%2B"), "UTF-8"));
            } catch (Exception ignored) {
            }
        }
        LISTS.put(console.id, names);
        return names;
    }

    /** O nome para comparar: sem (região), [versão], acentos, "The" e pontuação. */
    static String base(String s) {
        s = Normalizer.normalize(TAGS.matcher(s).replaceAll(""), Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "").toLowerCase(java.util.Locale.ROOT);
        return s.replaceAll("^the\\s+|,\\s*the\\b", "").replaceAll("[^a-z0-9]", "");
    }

    private static String region(String n) {
        Matcher m = REGION.matcher(n);
        return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : "";
    }

    /** A melhor capa com o mesmo nome: a mesma região do jogo, depois EUA, Mundo, Europa, Brasil, Japão. */
    private static String findByName(Consoles.Console console, JSONObject game) throws Exception {
        List<String> names = listOf(console);
        if (names == null) return null;
        String title = game.optString("title");
        Set<String> keys = new HashSet<String>();
        for (String k : new String[]{base(title), base(game.optString("name"))}) if (k.length() > 0) keys.add(k);
        String want = region(title), best = null;
        double bestScore = Double.MAX_VALUE;
        for (String n : names) {
            if (!keys.contains(base(n))) continue;
            String r = region(n);
            int i = REGIONS.length;
            for (int k = 0; k < REGIONS.length; k++) if (r.contains(REGIONS[k])) { i = k; break; }
            double score = (BAD.matcher(n).find() ? 100 : 0) + (want.length() > 0 && r.equals(want) ? 0 : 2 + i * 2) + n.length() / 1000.0;
            if (score < bestScore) { bestScore = score; best = n; }
        }
        return best;
    }

    /** O repositório troca &*\/:`<>?\| por _ nos nomes dos arquivos. */
    static String safe(String title) {
        return title.replaceAll("[&*/:`<>?\\\\|\"]", "_");
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static byte[] download(String link) throws Exception {
        return download(link, MAX_BYTES);
    }

    private static byte[] download(String link, int max) throws Exception {
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
                if (o.size() + n > max) return null;
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
