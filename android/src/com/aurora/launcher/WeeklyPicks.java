package com.aurora.launcher;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * "Indicações da semana": prioriza o catálogo da Netflix Brasil (populares no JustWatch,
 * que informa o ID do título na Netflix para abrir direto nele) e completa com o ranking
 * da iTunes Store Brasil se faltar. Nenhuma das fontes exige chave de API.
 * Sorteio com semente = semana ISO, então a seleção muda toda segunda-feira.
 * A rede é feita só aqui (HTTPS, hosts fixos); a página recebe capas já em data: URL.
 */
final class WeeklyPicks {

    private static final String FEED = "https://itunes.apple.com/br/rss/topmovies/limit=50/json";
    private static final String JUSTWATCH = "https://apis.justwatch.com/graphql";
    private static final String JW_IMAGES = "https://images.justwatch.com";
    private static final String JW_QUERY =
            "query($c:Country!,$l:Language!,$f:TitleFilter){popularTitles(country:$c,first:40,filter:$f,sortBy:POPULAR)"
            + "{edges{node{objectType content(country:$c,language:$l){title originalReleaseYear shortDescription posterUrl genres{shortName}}"
            + " offers(country:$c,platform:WEB,filter:{packages:[\"nfx\"]}){standardWebURL}}}}}";
    private static final Pattern POSTER = Pattern.compile(
            "^https://[a-z0-9-]+\\.mzstatic\\.com/image/thumb/[^?#\\s\"]+/\\d+x\\d+bb\\.(png|jpg)$");
    private static final Pattern JW_POSTER = Pattern.compile("^/poster/\\d{1,12}/\\{profile\\}/[a-z0-9-]{1,120}\\.\\{format\\}$");
    private static final Pattern NETFLIX_URL = Pattern.compile("^https://www\\.netflix\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?title/(\\d{1,12})/?$");
    /** Sobe quando o formato do cache muda: força baixar de novo mesmo na mesma semana. */
    private static final int FORMAT = 3;   // 3: títulos em pt-BR (cache antigo é baixado de novo)
    private static final int COUNT = 8;
    private static final int MAX_FEED = 2 * 1024 * 1024, MAX_POSTER = 300 * 1024;
    private static final long RETRY_MS = 30 * 60 * 1000L;   // após falha (sem rede), tenta de novo em 30 min

    private static WeeklyPicks instance;

    private final File file;
    private final AssetManager assets;
    private boolean loaded;
    private String week;
    private String json = "[]";
    private final Map<String, String> netflixIds = new HashMap<String, String>();
    private long lastAttempt;
    private boolean inFlight;

    static synchronized WeeklyPicks get(Context ctx) {
        if (instance == null) instance = new WeeklyPicks(ctx.getApplicationContext());
        return instance;
    }

    private WeeklyPicks(Context ctx) {
        file = new File(ctx.getFilesDir(), "picks.json");
        assets = ctx.getAssets();
    }

    /** Semana ISO 8601 (segunda a domingo), ex.: "2026-W43". */
    static String currentWeek() {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.setMinimalDaysInFirstWeek(4);
        Calendar thursday = (Calendar) c.clone();
        int fromMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        thursday.add(Calendar.DAY_OF_MONTH, 3 - fromMonday);
        return thursday.get(Calendar.YEAR) + "-W" + c.get(Calendar.WEEK_OF_YEAR);
    }

    synchronized String json() {
        ensureLoaded();
        return json;
    }

    synchronized boolean hasTitle(String title) {
        ensureLoaded();
        return netflixIds.containsKey(title);
    }

    /** ID do título na Netflix (só dígitos), ou null se a indicação não veio da Netflix. */
    synchronized String netflixId(String title) {
        ensureLoaded();
        return netflixIds.get(title);
    }

    boolean refreshIfStale() {
        return refreshIfStale(false);
    }

    /**
     * Baixa a seleção nova se a semana virou. Bloqueia (rede): chamar fora da UI thread.
     * networkBack = a internet acabou de voltar: ignora a espera de 30 min após uma falha.
     */
    boolean refreshIfStale(boolean networkBack) {
        return refresh(networkBack, false);
    }

    /** force = botão "Atualizar": baixa de novo mesmo com a semana em dia e sem esperar após uma falha. */
    boolean refresh(boolean networkBack, boolean force) {
        String now = currentWeek();
        synchronized (this) {
            ensureLoaded();
            if (inFlight || (!force && now.equals(week))) return false;
            long t = SystemClock.elapsedRealtime();
            if (!force && !networkBack && lastAttempt != 0 && t - lastAttempt < RETRY_MS) return false;
            lastAttempt = t;
            inFlight = true;
        }
        JSONArray items;
        try {
            items = fetch(now);
        } catch (Exception e) {
            return false;   // sem rede: mantém a semana anterior em cache
        } finally {
            synchronized (this) { inFlight = false; }
        }
        if (items.length() == 0) return false;
        synchronized (this) {
            try {
                JSONObject o = new JSONObject();
                o.put("v", FORMAT);
                o.put("week", now);
                o.put("items", items);
                File tmp = new File(file.getPath() + ".tmp");
                FileOutputStream out = new FileOutputStream(tmp);
                try {
                    out.write(o.toString().getBytes("UTF-8"));
                } finally {
                    out.close();
                }
                if (!tmp.renameTo(file)) return false;
            } catch (Exception e) {
                return false;
            }
            apply(now, items);
            return true;
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(new String(readFile(file), "UTF-8"));
            // Cache de formato antigo: mostra enquanto baixa o novo (semana "vencida").
            apply(o.optInt("v") == FORMAT ? o.getString("week") : null, o.getJSONArray("items"));
        } catch (Exception ignored) {
            loadSeed();   // primeira execução ou arquivo corrompido
        }
    }

    /**
     * Indicações embutidas no APK (tools/gen_seed.py): a tela já abre com capas mesmo sem
     * internet. Lidas direto do APK, sem cópia no armazenamento; a semana fica "vencida",
     * então são trocadas pelas da semana assim que houver conexão.
     */
    private void loadSeed() {
        try {
            InputStream in = assets.open("picks-seed.json");
            try {
                JSONObject o = new JSONObject(new String(readAll(in, MAX_FEED), "UTF-8"));
                apply(null, o.getJSONArray("items"));
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            // Sem seed no APK: começa vazio.
        }
    }

    private void apply(String w, JSONArray items) {
        week = w;
        json = items.toString();
        netflixIds.clear();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            String id = it.optString("netflixId");
            netflixIds.put(it.optString("title"), id.matches("\\d{1,12}") ? id : null);
        }
    }

    private static JSONArray fetch(String week) throws Exception {
        JSONArray out = new JSONArray();
        Set<String> franchises = new HashSet<String>();
        Random rnd = new Random(week.hashCode());
        Exception netflixError = null;
        try {
            fetchNetflix(rnd, out, franchises);
        } catch (Exception e) {
            netflixError = e;   // JustWatch fora do ar: segue só com a iTunes
        }
        if (out.length() < COUNT) {
            try {
                fetchItunes(rnd, out, franchises);
            } catch (Exception e) {
                if (out.length() == 0) throw netflixError != null ? netflixError : e;
            }
        }
        return out;
    }

    /** Populares da Netflix Brasil, com o ID do título para abrir direto no app. */
    private static void fetchNetflix(Random rnd, JSONArray out, Set<String> franchises) throws Exception {
        JSONObject vars = new JSONObject();
        vars.put("c", "BR");
        vars.put("l", "pt-BR");   // "pt" devolve nomes de Portugal (ex.: "Profundo" em vez de "Profundezas")
        JSONObject filter = new JSONObject();
        filter.put("packages", new JSONArray().put("nfx"));
        filter.put("objectTypes", new JSONArray().put("MOVIE").put("SHOW"));
        vars.put("f", filter);
        JSONObject body = new JSONObject();
        body.put("query", JW_QUERY);
        body.put("variables", vars);

        JSONObject res = new JSONObject(new String(post(JUSTWATCH, body.toString().getBytes("UTF-8"), MAX_FEED), "UTF-8"));
        JSONArray edges = res.getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges");

        for (int idx : weightedOrder(edges.length(), rnd, 15f)) {
            if (out.length() >= COUNT) break;
            try {
                JSONObject node = edges.getJSONObject(idx).getJSONObject("node");
                JSONObject c = node.getJSONObject("content");
                String title = cleanTitle(c.getString("title"));
                if (title.isEmpty() || title.length() > 120) continue;

                String id = null;
                JSONArray offers = node.optJSONArray("offers");
                for (int i = 0; offers != null && i < offers.length() && id == null; i++) {
                    java.util.regex.Matcher m = NETFLIX_URL.matcher(offers.getJSONObject(i).optString("standardWebURL"));
                    if (m.matches()) id = m.group(1);
                }
                if (id == null) continue;

                String path = c.optString("posterUrl");
                if (!JW_POSTER.matcher(path).matches()) continue;
                if (!franchises.add(franchise(title))) continue;
                byte[] jpg = download(JW_IMAGES + path.replace("{profile}", "s332").replace("{format}", "jpg"), MAX_POSTER);
                if (!isJpeg(jpg)) continue;

                JSONObject it = new JSONObject();
                it.put("title", title);
                it.put("provider", "netflix");
                it.put("netflixId", id);
                it.put("kind", "SHOW".equals(node.optString("objectType")) ? "Série" : "Filme");
                JSONArray genres = c.optJSONArray("genres");
                if (genres != null && genres.length() > 0) {
                    String g = GENRES.get(genres.getJSONObject(0).optString("shortName"));
                    if (g != null) it.put("genre", g);
                }
                int year = c.optInt("originalReleaseYear");
                if (year > 1900 && year < 3000) it.put("year", String.valueOf(year));
                it.put("summary", trim(c.optString("shortDescription").trim(), 260));
                putPoster(it, jpg);
                out.put(it);
            } catch (Exception ignored) {
                // Entrada incompleta: pula para a próxima.
            }
        }
    }

    /** Filmes do ranking da iTunes Store Brasil (usados para completar a seleção). */
    private static void fetchItunes(Random rnd, JSONArray out, Set<String> franchises) throws Exception {
        JSONObject feed = new JSONObject(new String(download(FEED, MAX_FEED), "UTF-8"));
        JSONArray entries = feed.getJSONObject("feed").getJSONArray("entry");

        for (int idx : weightedOrder(entries.length(), rnd, 25f)) {
            if (out.length() >= COUNT) break;
            try {
                JSONObject e = entries.getJSONObject(idx);
                String title = cleanTitle(e.getJSONObject("im:name").getString("label"));
                if (title.isEmpty() || !franchises.add(franchise(title))) continue;

                JSONArray imgs = e.getJSONArray("im:image");
                String src = imgs.getJSONObject(imgs.length() - 1).getString("label");
                if (!POSTER.matcher(src).matches()) continue;
                src = src.replaceFirst("/\\d+x\\d+bb\\.(png|jpg)$", "/300x450bb.jpg");
                byte[] jpg = download(src, MAX_POSTER);
                if (!isJpeg(jpg)) continue;

                JSONObject it = new JSONObject();
                it.put("title", title);
                it.put("genre", e.getJSONObject("category").getJSONObject("attributes").optString("label"));
                String date = e.getJSONObject("im:releaseDate").optString("label");
                if (date.length() >= 4) it.put("year", date.substring(0, 4));
                it.put("summary", trim(e.getJSONObject("summary").optString("label"), 260));
                putPoster(it, jpg);
                out.put(it);
            } catch (Exception ignored) {
                // Entrada incompleta: pula para a próxima.
            }
        }
    }

    /** Mais peso para o topo do ranking, mas embaralhado pela semana. */
    private static List<Integer> weightedOrder(int n, Random rnd, float spread) {
        final float[] score = new float[n];
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            score[i] = i + rnd.nextFloat() * spread;
            order.add(i);
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Float.compare(score[a], score[b]);
            }
        });
        return order;
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 4 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8;
    }

    private static void putPoster(JSONObject it, byte[] jpg) throws Exception {
        it.put("poster", "data:image/jpeg;base64," + Base64.encodeToString(jpg, Base64.NO_WRAP));
        String color = averageColor(jpg);
        if (color != null) it.put("color", color);
    }

    private static final Map<String, String> GENRES = new HashMap<String, String>();
    static {
        String[] g = {"act", "Ação", "ani", "Animação", "cmy", "Comédia", "crm", "Crime", "doc", "Documentário",
                "drm", "Drama", "fnt", "Fantasia", "hst", "História", "hrr", "Terror", "fml", "Família",
                "msc", "Música", "trl", "Suspense", "rma", "Romance", "scf", "Ficção científica",
                "spt", "Esporte", "war", "Guerra", "wsn", "Faroeste", "rly", "Reality"};
        for (int i = 0; i < g.length; i += 2) GENRES.put(g[i], g[i + 1]);
    }

    private static String cleanTitle(String t) {
        return t.replaceAll("\\s*\\[[^\\]]*\\]", "")
                .replaceAll("(?i)\\s*\"vers[aã]o[^\"]*\"", "")
                .replaceAll("(?i)\\s*\\(vers[aã]o[^)]*\\)", "")
                .trim();
    }

    /** Evita a semana inteira de uma franquia só (ex.: vários Harry Potter). */
    private static String franchise(String title) {
        String head = title.split(":| - | – ")[0].toLowerCase(Locale.ROOT);
        StringBuilder key = new StringBuilder();
        int words = 0;
        for (String w : head.split("\\s+")) {
            if (w.matches("o|a|os|as|um|uma|the")) continue;
            key.append(w).append(' ');
            if (++words == 2) break;
        }
        return key.toString();
    }

    private static String trim(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max - 1);
        return s.substring(0, cut > max / 2 ? cut : max - 1) + "…";
    }

    /** Cor média da capa (para tingir o fundo do destaque sem desfoque). */
    private static String averageColor(byte[] jpg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 16;
        Bitmap b = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, o);
        if (b == null) return null;
        Bitmap one = Bitmap.createScaledBitmap(b, 1, 1, true);
        int px = one.getPixel(0, 0);
        if (one != b) one.recycle();
        b.recycle();
        return String.format(Locale.ROOT, "#%06x", px & 0xffffff);
    }

    static byte[] download(String url, int max) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Onix-Launcher");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            return readAll(c.getInputStream(), max);
        } finally {
            c.disconnect();
        }
    }

    private static byte[] post(String url, byte[] body, int max) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "Onix-Launcher");
        c.setFixedLengthStreamingMode(body.length);
        try {
            java.io.OutputStream o = c.getOutputStream();
            try {
                o.write(body);
            } finally {
                o.close();
            }
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            return readAll(c.getInputStream(), max);
        } finally {
            c.disconnect();
        }
    }

    static byte[] readFile(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            return readAll(in, 8 * 1024 * 1024);
        } finally {
            in.close();
        }
    }

    static byte[] readAll(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > max) throw new Exception("resposta grande demais");
        }
        return out.toByteArray();
    }
}
