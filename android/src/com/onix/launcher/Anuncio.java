package com.onix.launcher;

import android.content.Context;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.regex.Pattern;

/**
 * Anúncio do Project Gems: o primeiro banner do destaque, com as novidades dos projetos do Ruan.
 * O conteúdo fica no site (projectgems.github.io/onix/anuncio/anuncio.json + imagens) e é editado
 * pelo app "Onix Anúncio", sem nova versão do Onix. Baixado no máximo a cada 1 h (e no botão
 * "Atualizar"); a página recebe as imagens já em data: URL. "ativo": false tira o banner.
 */
final class Anuncio {

    private static final String BASE = "https://projectgems.github.io/onix/anuncio/";
    private static final Pattern IMG_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,40}\\.(webp|png|jpg|jpeg)$");
    private static final Pattern LINK = Pattern.compile("^https://[A-Za-z0-9.-]{1,80}(/[^\\s\"'<>]{0,200})?$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final int MAX_JSON = 64 * 1024, MAX_IMG = 350 * 1024, MAX_IMGS = 2;
    private static final long FRESH_MS = 60 * 60 * 1000L, RETRY_MS = 15 * 60 * 1000L;

    private static Anuncio instance;

    private final File file;
    private boolean loaded;
    private long fetched;
    private String json = "{}";
    private long lastAttempt;
    private boolean inFlight;

    static synchronized Anuncio get(Context ctx) {
        if (instance == null) instance = new Anuncio(ctx.getApplicationContext());
        return instance;
    }

    private Anuncio(Context ctx) {
        file = new File(ctx.getFilesDir(), "anuncio.json");
    }

    synchronized String json() {
        ensureLoaded();
        return json;
    }

    boolean refreshIfStale(boolean networkBack) {
        return refresh(networkBack, false);
    }

    /** Bloqueia (rede): chamar fora da UI thread. Retorna true se o anúncio mudou. */
    boolean refresh(boolean networkBack, boolean force) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            ensureLoaded();
            if (inFlight || (!force && now - fetched >= 0 && now - fetched < FRESH_MS)) return false;
            long t = SystemClock.elapsedRealtime();
            if (!force && !networkBack && lastAttempt != 0 && t - lastAttempt < RETRY_MS) return false;
            lastAttempt = t;
            inFlight = true;
        }
        JSONObject ad;
        try {
            ad = fetch();
        } catch (Exception e) {
            return false;   // sem internet ou site fora do ar: fica o anúncio em cache
        } finally {
            synchronized (this) { inFlight = false; }
        }
        synchronized (this) {
            String novo = ad.toString();
            boolean mudou = !novo.equals(json);
            try {
                JSONObject o = new JSONObject();
                o.put("fetched", now);
                o.put("ad", ad);
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
            fetched = now;
            json = novo;
            return mudou;
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(new String(WeeklyPicks.readFile(file), "UTF-8"));
            fetched = o.optLong("fetched");
            json = o.getJSONObject("ad").toString();
        } catch (Exception ignored) {
            // Primeira execução: o anúncio aparece quando houver internet.
        }
    }

    /** Lê o anuncio.json do site e baixa as imagens. Desativado = objeto vazio (sem banner). */
    private static JSONObject fetch() throws Exception {
        // ?t= fura o cache do GitHub Pages / proxies: a edição aparece na próxima busca
        JSONObject src = new JSONObject(new String(WeeklyPicks.download(BASE + "anuncio.json?t=" + System.currentTimeMillis() / 60000, MAX_JSON), "UTF-8"));
        JSONObject out = new JSONObject();
        if (!src.optBoolean("ativo", false)) return out;
        JSONObject pt = textos(src.optJSONObject("pt"));
        if (pt == null) return out;
        out.put("pt", pt);
        JSONObject es = textos(src.optJSONObject("es"));
        if (es != null) out.put("es", es);
        String link = src.optString("link", "");
        out.put("link", LINK.matcher(link).matches() ? link : "https://projectgems.github.io/");
        out.put("cor1", cor(src.optString("cor1"), "#7fe6b0"));
        out.put("cor2", cor(src.optString("cor2"), "#0f5c3d"));
        // v2 = duas imagens em pé (padrão), h1 = uma deitada, h2 = duas deitadas
        String layout = src.optString("layout", "v2");
        if (!layout.equals("h1") && !layout.equals("h2")) layout = "v2";
        out.put("layout", layout);
        int max = layout.equals("h1") ? 1 : MAX_IMGS;
        JSONArray imgs = new JSONArray();
        JSONArray nomes = src.optJSONArray("imagens");
        for (int i = 0; nomes != null && i < nomes.length() && imgs.length() < max; i++) {
            String nome = nomes.optString(i, "");
            if (!IMG_NAME.matcher(nome).matches()) continue;
            byte[] b = WeeklyPicks.download(BASE + nome + "?t=" + System.currentTimeMillis() / 60000, MAX_IMG);
            String mime = mime(b);
            if (mime != null) imgs.put("data:" + mime + ";base64," + Base64.encodeToString(b, Base64.NO_WRAP));
        }
        out.put("imagens", imgs);
        return out;
    }

    private static JSONObject textos(JSONObject o) throws Exception {
        if (o == null) return null;
        String titulo = limpo(o.optString("titulo"), 60);
        if (titulo.isEmpty()) return null;
        JSONObject t = new JSONObject();
        t.put("kicker", limpo(o.optString("kicker"), 60));
        t.put("titulo", titulo);
        t.put("texto", limpo(o.optString("texto"), 240));
        String botao = limpo(o.optString("botao"), 24);
        t.put("botao", botao.isEmpty() ? "Saiba mais" : botao);
        t.put("qr", limpo(o.optString("qr"), 240));
        return t;
    }

    private static String limpo(String s, int max) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max).trim() : s;
    }

    private static String cor(String s, String padrao) {
        return s != null && COLOR.matcher(s).matches() ? s : padrao;
    }

    private static String mime(byte[] b) {
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8) return "image/jpeg";
        return null;
    }
}
