package com.onix.launcher;

import android.content.Context;
import android.os.SystemClock;
import android.text.Html;
import android.util.Base64;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Notícias dos games": RSS da Adrenaline (só a categoria Games) e da Eurogamer.pt.
 * Baixa no máximo a cada 3 h e só com o launcher aberto (nada em segundo plano), então o
 * consumo fica em ~700 KB por atualização. O resumo é montado aqui a partir do próprio
 * feed; a página recebe só texto e miniaturas já em data: URL.
 */
final class GameNews {

    private static final String[][] FEEDS = {
            {"Adrenaline", "https://www.adrenaline.com.br/feed/", "Games"},
            {"Eurogamer.pt", "https://www.eurogamer.pt/feed", null},
    };
    private static final Pattern LINK = Pattern.compile("^https://www\\.(adrenaline\\.com\\.br|eurogamer\\.pt)/[^\\s\"'<>]{1,400}$");
    private static final Pattern ADR_IMG = Pattern.compile("src=\"(https://www\\.adrenaline\\.com\\.br/wp-content/uploads/[\\w/.-]{1,200}?)-\\d+x\\d+\\.(webp|jpg|jpeg|png)\"");
    // srcset da imagem de destaque: "URL 912w, URL 1029w, ...".
    private static final Pattern ADR_SRCSET = Pattern.compile("srcset=\"([^\"]{1,4000})\"");
    private static final Pattern ADR_SRC = Pattern.compile("(https://www\\.adrenaline\\.com\\.br/wp-content/uploads/[\\w/.-]{1,200}?\\.(?:webp|jpg|jpeg|png)) (\\d{2,4})w");
    private static final int IMG_W = 1100;   // o leitor mostra a imagem com ~1.000 px de largura na TV
    private static final Pattern EG_IMG = Pattern.compile("^(https://assetsio\\.gnwcdn\\.com/[\\w/.-]{1,200})(\\?.*)?$");
    private static final Pattern PARA = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SKIP = Pattern.compile("^(leia (também|mais)|veja (também|mais)|ler mais|link de compra|confira|via:|fonte:).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int PER_FEED = 8, COUNT = 12;
    private static final int MAX_FEED = 2 * 1024 * 1024, MAX_IMG = 400 * 1024;
    private static final long FRESH_MS = 3 * 60 * 60 * 1000L, RETRY_MS = 15 * 60 * 1000L;

    private static GameNews instance;

    private final File file;
    private boolean loaded;
    private long fetched;
    private String json = "[]";
    private long lastAttempt;
    private boolean inFlight;

    static synchronized GameNews get(Context ctx) {
        if (instance == null) instance = new GameNews(ctx.getApplicationContext());
        return instance;
    }

    private GameNews(Context ctx) {
        file = new File(ctx.getFilesDir(), "news.json");
    }

    synchronized String json() {
        ensureLoaded();
        return json;
    }

    /**
     * Baixa de novo se a lista tiver mais de 3 h. Bloqueia (rede): chamar fora da UI thread.
     * networkBack = a internet acabou de voltar: ignora a espera após uma falha.
     */
    boolean refreshIfStale(boolean networkBack) {
        return refresh(networkBack, false);
    }

    /** force = botão "Atualizar": baixa agora, sem a espera de 3 h nem a de depois de uma falha. */
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
        JSONArray items;
        try {
            items = fetch();
        } catch (Exception e) {
            return false;
        } finally {
            synchronized (this) { inFlight = false; }
        }
        if (items.length() == 0) return false;
        synchronized (this) {
            try {
                JSONObject o = new JSONObject();
                o.put("fetched", now);
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
            JSONObject o = new JSONObject(new String(WeeklyPicks.readFile(file), "UTF-8"));
            apply(o.optLong("fetched"), o.getJSONArray("items"));
        } catch (Exception ignored) {
            // Primeira execução: a lista aparece quando houver internet.
        }
    }

    private void apply(long when, JSONArray items) {
        fetched = when;
        json = items.toString();
    }

    /** Uma notícia já lida do feed (antes de baixar a miniatura). */
    private static final class Item {
        String source, title, link, summary, image;
        long date;
    }

    private static JSONArray fetch() throws Exception {
        List<Item> all = new ArrayList<Item>();
        Exception error = null;
        for (String[] f : FEEDS) {
            try {
                all.addAll(parse(f[0], WeeklyPicks.download(f[1], MAX_FEED), f[2]));
            } catch (Exception e) {
                error = e;   // um site fora do ar não derruba o outro
            }
        }
        if (all.isEmpty() && error != null) throw error;
        Collections.sort(all, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.date < b.date ? 1 : a.date > b.date ? -1 : 0;
            }
        });

        JSONArray out = new JSONArray();
        for (Item it : all) {
            if (out.length() >= COUNT) break;
            JSONObject o = new JSONObject();
            o.put("source", it.source);
            o.put("title", it.title);
            o.put("link", it.link);
            o.put("date", it.date);
            o.put("summary", it.summary);
            String img = it.image == null ? null : thumbnail(it.image);
            if (img != null) o.put("image", img);
            out.put(o);
        }
        return out;
    }

    private static List<Item> parse(String source, byte[] xml, String category) throws Exception {
        List<Item> out = new ArrayList<Item>();
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(new ByteArrayInputStream(xml), null);
        SimpleDateFormat rfc822 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

        String title = null, link = null, date = null, desc = null, content = null, media = null;
        boolean inItem = false, hasCategory = false, wanted = false;
        try {
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT && out.size() < PER_FEED; ev = p.next()) {
                String name = p.getName();
                if (ev == XmlPullParser.START_TAG) {
                    if ("item".equals(name)) {
                        inItem = true;
                        title = link = date = desc = content = media = null;
                        hasCategory = wanted = false;
                    } else if (!inItem) {
                        continue;
                    } else if ("title".equals(name)) title = p.nextText();
                    else if ("link".equals(name)) link = p.nextText().trim();
                    else if ("pubDate".equals(name)) date = p.nextText().trim();
                    else if ("description".equals(name)) desc = p.nextText();
                    else if ("content:encoded".equals(name)) content = p.nextText();
                    else if ("media:content".equals(name)) media = p.getAttributeValue(null, "url");
                    else if ("category".equals(name)) {
                        String c = p.nextText().trim();
                        hasCategory = true;
                        if (category != null && category.equalsIgnoreCase(c)) wanted = true;
                    }
                } else if (ev == XmlPullParser.END_TAG && "item".equals(name) && inItem) {
                    inItem = false;
                    // Adrenaline: só Games (o feed mistura hardware). Eurogamer: sem categoria costuma ser cinema/TV.
                    if (category != null ? !wanted : !hasCategory) continue;
                    if (title == null || link == null || !LINK.matcher(link).matches()) continue;
                    Item it = new Item();
                    it.source = source;
                    it.title = trim(text(title), 160);
                    it.link = link;
                    String sum = summary(content != null ? content : desc);
                    if (sum.length() < 80 && content != null) sum = summary(desc);
                    it.summary = sum;
                    if (it.title.isEmpty() || it.summary.isEmpty()) continue;
                    try {
                        it.date = rfc822.parse(date).getTime();
                    } catch (Exception e) {
                        it.date = 0;
                    }
                    it.image = imageUrl(desc, media);
                    out.add(it);
                }
            }
        } catch (Exception e) {
            if (out.isEmpty()) throw e;   // XML quebrado no meio: fica com o que já foi lido
        }
        return out;
    }

    /** Primeiros parágrafos do texto (até ~1000 caracteres), sem "leia também" e links de compra. */
    private static String summary(String html) {
        if (html == null) return "";
        StringBuilder sb = new StringBuilder();
        Matcher m = PARA.matcher(html);
        while (m.find() && sb.length() < 700) {
            String t = text(m.group(1));
            if (t.length() < 40 || SKIP.matcher(t).matches() || t.contains("Link de compra")) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(t);
        }
        if (sb.length() == 0) sb.append(text(html).replaceAll("\\s*Ler mais$", ""));
        return trim(sb.toString(), 1100);
    }

    @SuppressWarnings("deprecation")
    private static String text(String html) {
        String s = html.replaceAll("(?is)<(script|style|figure|figcaption)[^>]*>.*?</\\1>", " ").replaceAll("(?i)<img[^>]*>", " ");
        s = Html.fromHtml(s).toString();
        return s.replace(' ', ' ').replace("￼", "").replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\s*\\n\\s*", " ").trim();
    }

    private static String trim(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(". ", max - 1);
        if (cut > max / 2) return s.substring(0, cut + 1);
        cut = s.lastIndexOf(' ', max - 1);
        return s.substring(0, cut > max / 2 ? cut : max - 1) + "…";
    }

    /** Endereço da imagem na maior versão até IMG_W px (nítida no leitor sem pesar no download). */
    private static String imageUrl(String desc, String media) {
        if (desc != null) {
            Matcher set = ADR_SRCSET.matcher(desc);
            if (set.find()) {
                String best = null;
                int bestW = 0;
                Matcher m = ADR_SRC.matcher(set.group(1));
                while (m.find()) {
                    int w = Integer.parseInt(m.group(2));
                    if (w <= IMG_W && w > bestW) { bestW = w; best = m.group(1); }
                }
                if (best != null) return best;
            }
            Matcher m = ADR_IMG.matcher(desc);
            if (m.find()) return m.group(1) + "-768x480." + m.group(2);
        }
        if (media != null) {
            Matcher m = EG_IMG.matcher(media);
            if (m.matches()) return m.group(1) + "?width=" + IMG_W + "&quality=75&format=jpg";
        }
        return null;
    }

    private static String thumbnail(String url) {
        try {
            byte[] b = WeeklyPicks.download(url, MAX_IMG);
            String type = imageType(b);
            if (type == null) return null;
            return "data:image/" + type + ";base64," + Base64.encodeToString(b, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;   // sem miniatura a notícia aparece mesmo assim
        }
    }

    private static String imageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8) return "jpeg";
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }
}
