package com.aurora.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * "Indicações da semana": populares no JustWatch de cada serviço que a pessoa usa (a Netflix
 * sempre, e Disney+, HBO Max e Globoplay quando abertos mais de 3 vezes na última semana; ver
 * AppUsage), com o link do título para abrir direto no app. Completa com o ranking da iTunes
 * Store Brasil se faltar. Nenhuma das fontes exige chave de API.
 * Sorteio com semente = semana ISO, então a seleção muda toda segunda-feira (ou quando os
 * serviços da pessoa mudam). A rede é feita só aqui (HTTPS, hosts fixos); a página recebe
 * capas já em data: URL.
 */
final class WeeklyPicks {

    private static final String FEED = "https://itunes.apple.com/br/rss/topmovies/limit=50/json";
    private static final String JUSTWATCH = "https://apis.justwatch.com/graphql";
    private static final String JW_IMAGES = "https://images.justwatch.com";
    private static final String JW_QUERY =
            "query($c:Country!,$l:Language!,$f:TitleFilter,$p:[String!]!){popularTitles(country:$c,first:40,filter:$f,sortBy:POPULAR)"
            + "{edges{node{objectType content(country:$c,language:$l){title originalReleaseYear shortDescription posterUrl genres{shortName}}"
            + " offers(country:$c,platform:WEB,filter:{packages:$p}){standardWebURL}}}}}";
    private static final Pattern POSTER = Pattern.compile(
            "^https://[a-z0-9-]+\\.mzstatic\\.com/image/thumb/[^?#\\s\"]+/\\d+x\\d+bb\\.(png|jpg)$");
    private static final Pattern JW_POSTER = Pattern.compile("^/poster/\\d{1,12}/\\{profile\\}/[a-z0-9-]{1,120}\\.\\{format\\}$");
    private static final Pattern NETFLIX_URL = Pattern.compile("^https://www\\.netflix\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?title/(\\d{1,12})/?$");

    /** Um serviço de streaming: código no JustWatch, links aceitos e pacotes do app na TV. */
    static final class Provider {
        final String key, jw;
        final Pattern link;
        final String[] pkgs;

        Provider(String key, String jw, Pattern link, String... pkgs) {
            this.key = key;
            this.jw = jw;
            this.link = link;
            this.pkgs = pkgs;
        }
    }

    /** Ordem da tabela = desempate. A Netflix é o padrão: entra mesmo sem uso. */
    static final Provider NETFLIX = new Provider("netflix", "nfx", NETFLIX_URL, "com.netflix.ninja", "com.netflix.mediaclient");
    static final Provider[] PROVIDERS = {
            NETFLIX,
            new Provider("disney", "dnp",
                    Pattern.compile("^https://www\\.disneyplus\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?browse/entity-[0-9a-f-]{36}$"),
                    "com.disney.disneyplus"),
            new Provider("max", "mxx",
                    Pattern.compile("^https://play\\.(?:hbo)?max\\.com/(?:video/watch|show|movie)/[0-9a-f-]{36}(?:/[0-9a-f-]{36})?$"),
                    "com.wbd.stream", "com.hbo.hbonow"),
            // /t/ é a página do título; /v/ já dá play num episódio, então fica como reserva.
            new Provider("globoplay", "gop",
                    Pattern.compile("^https://globoplay\\.globo\\.com/(?:[a-z0-9-]{1,120}/t/[A-Za-z0-9]{1,20}/?|v/\\d{1,12}/?)$"),
                    "com.globo.globotv"),
    };

    static Provider provider(String key) {
        for (Provider p : PROVIDERS) if (p.key.equals(key)) return p;
        return null;
    }

    /** Serviço do app (para contar aberturas), ou null se não for um dos serviços das indicações. */
    static Provider providerForPackage(String pkg) {
        for (Provider p : PROVIDERS) for (String k : p.pkgs) if (k.equals(pkg)) return p;
        return null;
    }

    static boolean installed(Context ctx, Provider p) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : p.pkgs) {
            if (pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null) return true;
        }
        return false;
    }

    private static android.content.SharedPreferences choice(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("picks", Context.MODE_PRIVATE);
    }

    /**
     * Configurações → Indicações. auto = pelo uso (padrão); senão, só os serviços escolhidos.
     * Retorna true se a escolha mudou (e a seleção precisa ser baixada de novo).
     */
    static synchronized boolean setChoice(Context ctx, boolean auto, Set<String> services) {
        android.content.SharedPreferences p = choice(ctx);
        Set<String> old = p.getStringSet("services", Collections.<String>emptySet());
        if (p.getBoolean("auto", true) == auto && (auto || old.equals(services))) return false;
        p.edit().putBoolean("auto", auto).putStringSet("services", new HashSet<String>(services)).apply();
        return true;
    }

    /**
     * Serviços das indicações agora. Pelo uso (padrão): a Netflix e os outros abertos mais de
     * 3 vezes na última semana (e ainda instalados), os mais abertos primeiro; no empate, a ordem
     * da tabela. Escolha manual: os serviços marcados, na ordem da tabela (nenhum = Netflix).
     */
    static List<Provider> activeProviders(Context ctx) {
        android.content.SharedPreferences c = choice(ctx);
        if (!c.getBoolean("auto", true)) {
            Set<String> keys = c.getStringSet("services", Collections.<String>emptySet());
            List<Provider> out = new ArrayList<Provider>();
            for (Provider p : PROVIDERS) if (keys.contains(p.key)) out.add(p);
            if (out.isEmpty()) out.add(NETFLIX);
            return out;
        }
        final Map<Provider, Integer> opens = new LinkedHashMap<Provider, Integer>();
        for (Provider p : PROVIDERS) {
            if (p == NETFLIX || (AppUsage.active(ctx, p.key) && installed(ctx, p))) opens.put(p, AppUsage.opens(ctx, p.key));
        }
        List<Provider> out = new ArrayList<Provider>(opens.keySet());
        Collections.sort(out, new Comparator<Provider>() {
            @Override
            public int compare(Provider a, Provider b) {
                return opens.get(b) - opens.get(a);   // sort estável: empate mantém a tabela
            }
        });
        return out;
    }

    private static String signature(List<Provider> list) {
        StringBuilder sb = new StringBuilder();
        for (Provider p : list) sb.append(sb.length() > 0 ? "," : "").append(p.key);
        return sb.toString();
    }

    /** Indicação da seleção atual que o JS pode abrir: serviço e link do título (nunca vêm do JS). */
    static final class Pick {
        final Provider provider;
        final String link, netflixId;

        Pick(Provider provider, String link, String netflixId) {
            this.provider = provider;
            this.link = link;
            this.netflixId = netflixId;
        }
    }
    /** Sobe quando o formato do cache muda: força baixar de novo mesmo na mesma semana. */
    private static final int FORMAT = 3;   // 3: títulos em pt-BR (cache antigo é baixado de novo)
    private static final int COUNT = 8;
    private static final int MAX_FEED = 2 * 1024 * 1024, MAX_POSTER = 300 * 1024;
    private static final long RETRY_MS = 30 * 60 * 1000L;   // após falha (sem rede), tenta de novo em 30 min

    private static WeeklyPicks instance;

    private final Context ctx;
    private final File file;
    private final AssetManager assets;
    private boolean loaded;
    private String week;
    private String providers = "netflix";   // serviços da seleção em cache (ex.: "netflix,disney")
    private String json = "[]";
    private final Map<String, Pick> titles = new HashMap<String, Pick>();
    private long lastAttempt;
    private boolean inFlight;

    static synchronized WeeklyPicks get(Context ctx) {
        if (instance == null) instance = new WeeklyPicks(ctx.getApplicationContext());
        return instance;
    }

    private WeeklyPicks(Context ctx) {
        this.ctx = ctx;
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
        return titles.containsKey(title);
    }

    /** Serviço e link do título, ou null se a indicação não veio de um serviço (ex.: iTunes). */
    synchronized Pick pick(String title) {
        ensureLoaded();
        Pick p = titles.get(title);
        return p != null && p.provider != null ? p : null;
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

    /**
     * force = botão "Atualizar": baixa de novo mesmo com a semana em dia e sem esperar após uma falha.
     * Também baixa de novo quando os serviços da pessoa mudam (um entrou na 4ª abertura ou saiu
     * depois de uma semana parado).
     */
    boolean refresh(boolean networkBack, boolean force) {
        String now = currentWeek();
        List<Provider> active = activeProviders(ctx);
        String sig = signature(active);
        synchronized (this) {
            ensureLoaded();
            boolean changed = !sig.equals(providers);
            if (inFlight || (!force && !changed && now.equals(week))) return false;
            long t = SystemClock.elapsedRealtime();
            if (!force && !networkBack && !changed && lastAttempt != 0 && t - lastAttempt < RETRY_MS) return false;
            lastAttempt = t;
            inFlight = true;
        }
        JSONArray items;
        try {
            items = fetch(now, active);
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
                o.put("providers", sig);
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
            apply(now, sig, items);
            return true;
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(new String(readFile(file), "UTF-8"));
            // Cache de formato antigo: mostra enquanto baixa o novo (semana "vencida").
            apply(o.optInt("v") == FORMAT ? o.getString("week") : null, o.optString("providers", "netflix"), o.getJSONArray("items"));
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
                apply(null, "netflix", o.getJSONArray("items"));
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            // Sem seed no APK: começa vazio.
        }
    }

    private void apply(String w, String sig, JSONArray items) {
        week = w;
        providers = sig;
        json = items.toString();
        titles.clear();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            Provider p = provider(it.optString("provider"));
            String id = it.optString("netflixId"), link = it.optString("link");
            if (p == NETFLIX && !id.matches("\\d{1,12}")) p = null;
            if (p != null && p != NETFLIX && !p.link.matcher(link).matches()) p = null;
            titles.put(it.optString("title"), new Pick(p, p == null || p == NETFLIX ? null : link, p == NETFLIX ? id : null));
        }
    }

    private static JSONArray fetch(String week, List<Provider> active) throws Exception {
        Set<String> franchises = new HashSet<String>();
        Random rnd = new Random(week.hashCode());
        Exception jwError = null;
        // Vagas divididas entre os serviços (8 só da Netflix; 4 + 4 com dois; 3 + 3 + 2 com três…).
        // O que um serviço não preencher passa para os próximos.
        List<List<JSONObject>> lists = new ArrayList<List<JSONObject>>();
        int taken = 0;
        for (int i = 0; i < active.size(); i++) {
            int left = active.size() - i, quota = (COUNT - taken + left - 1) / left;
            List<JSONObject> got = new ArrayList<JSONObject>();
            try {
                fetchProvider(active.get(i), rnd, quota, got, franchises);
            } catch (Exception e) {
                jwError = e;   // JustWatch fora do ar: segue com os outros e com a iTunes
            }
            lists.add(got);
            taken += got.size();
        }
        // Intercala os serviços para o destaque não mostrar um só por vários minutos.
        JSONArray out = new JSONArray();
        for (int round = 0; out.length() < taken; round++) {
            for (List<JSONObject> l : lists) if (round < l.size()) out.put(l.get(round));
        }
        if (out.length() < COUNT) {
            try {
                fetchItunes(rnd, out, franchises);
            } catch (Exception e) {
                if (out.length() == 0) throw jwError != null ? jwError : e;
            }
        }
        return out;
    }

    /** Link do título no serviço; no Globoplay, a página do título (/t/) vence o vídeo (/v/). */
    private static String bestLink(Provider p, JSONArray offers) {
        String best = null;
        for (int i = 0; offers != null && i < offers.length(); i++) {
            JSONObject o = offers.optJSONObject(i);
            if (o == null) continue;
            String u = o.optString("standardWebURL");
            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);   // tira utm_source & cia.
            if (!p.link.matcher(u).matches()) continue;
            if (best == null || (u.contains("/t/") && !best.contains("/t/"))) best = u;
        }
        return best;
    }

    /** Populares do serviço no Brasil, com o link do título para abrir direto no app. */
    private static void fetchProvider(Provider p, Random rnd, int quota, List<JSONObject> out, Set<String> franchises) throws Exception {
        JSONObject vars = new JSONObject();
        vars.put("c", "BR");
        vars.put("l", "pt-BR");   // "pt" devolve nomes de Portugal (ex.: "Profundo" em vez de "Profundezas")
        vars.put("p", new JSONArray().put(p.jw));
        JSONObject filter = new JSONObject();
        filter.put("packages", new JSONArray().put(p.jw));
        filter.put("objectTypes", new JSONArray().put("MOVIE").put("SHOW"));
        vars.put("f", filter);
        JSONObject body = new JSONObject();
        body.put("query", JW_QUERY);
        body.put("variables", vars);

        JSONObject res = new JSONObject(new String(post(JUSTWATCH, body.toString().getBytes("UTF-8"), MAX_FEED), "UTF-8"));
        JSONArray edges = res.getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges");

        for (int idx : weightedOrder(edges.length(), rnd, 15f)) {
            if (out.size() >= quota) break;
            try {
                JSONObject node = edges.getJSONObject(idx).getJSONObject("node");
                JSONObject c = node.getJSONObject("content");
                String title = cleanTitle(c.getString("title"));
                if (title.isEmpty() || title.length() > 120) continue;

                String link = bestLink(p, node.optJSONArray("offers"));
                if (link == null) continue;

                String path = c.optString("posterUrl");
                if (!JW_POSTER.matcher(path).matches()) continue;
                if (!franchises.add(franchise(title))) continue;
                byte[] jpg = download(JW_IMAGES + path.replace("{profile}", "s332").replace("{format}", "jpg"), MAX_POSTER);
                if (!isJpeg(jpg)) continue;

                JSONObject it = new JSONObject();
                it.put("title", title);
                it.put("provider", p.key);
                if (p == NETFLIX) {
                    java.util.regex.Matcher m = NETFLIX_URL.matcher(link);
                    if (!m.matches()) continue;
                    it.put("netflixId", m.group(1));
                } else {
                    it.put("link", link);
                }
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
                out.add(it);
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
