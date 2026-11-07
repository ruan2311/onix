package com.onix.gamehub;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;

/**
 * Tradução automática dos textos das conquistas do RetroAchievements (só existem em inglês), pelo MyMemory
 * (api.mymemory.translated.net, grátis). Cada texto é traduzido uma vez só e fica em files/ra-traducao-&lt;pt|es&gt;.json,
 * que o hub e o jogo (outro processo) leem; cada escrita relê antes de gravar.
 *
 * Limite grátis: 5.000 letras por dia sem e-mail e 50.000 com o e-mail de contato (parâmetro "de"). Quando o limite do
 * dia acaba, para de pedir até o dia seguinte e o texto aparece em inglês.
 */
final class Traducao {

    /** E-mail de contato mandado ao MyMemory (sobe o limite para 50.000 letras por dia). Vazio = sem e-mail. */
    static final String CONTACT = "escliburh@gmail.com";

    private Traducao() {
    }

    private static File file(Context c, String lang) {
        return new File(c.getFilesDir(), "ra-traducao-" + lang + ".json");
    }

    private static JSONObject read(Context c, String lang) {
        try {
            String s = Files.read(file(c, lang));
            if (s != null) return new JSONObject(s);
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
    }

    /** O texto já traduzido, ou o original (inglês) se ainda não tiver. */
    static String cached(JSONObject cache, String en) {
        String t = en == null ? "" : cache.optString(en, "");
        return t.isEmpty() ? en : cap(t);
    }

    /** A tradução às vezes vem com a primeira letra minúscula ("eu acredito que eu consigo"). */
    static String cap(String t) {
        return t == null || t.isEmpty() ? t : t.substring(0, t.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT) + t.substring(t.offsetByCodePoints(0, 1));
    }

    static JSONObject cache(Context c, String lang) {
        return read(c, lang);
    }

    /** Traduz e guarda (rede: fora da thread da tela). Devolve o texto em português/espanhol, ou o original. */
    static synchronized String translate(Context c, String lang, String en) {
        if (en == null || en.trim().isEmpty() || !("pt".equals(lang) || "es".equals(lang))) return en;
        JSONObject cache = read(c, lang);
        String have = cache.optString(en, "");
        if (!have.isEmpty()) return cap(have);
        if (today().equals(cache.optString("_cheio"))) return en;   // limite do dia já acabou
        String out = null;
        boolean full = false;
        try {
            String q = en.length() > 450 ? en.substring(0, 450) : en;   // o MyMemory aceita até 500 bytes
            String url = "https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(q, "UTF-8")
                    + "&langpair=" + URLEncoder.encode("en|" + ("es".equals(lang) ? "es" : "pt-BR"), "UTF-8")
                    + (CONTACT.isEmpty() ? "" : "&de=" + URLEncoder.encode(CONTACT, "UTF-8"));
            RetroAchievements.Response r = RetroAchievements.http(url, null, null);
            if (r.status == 200) {
                JSONObject o = new JSONObject(new String(r.body, "UTF-8"));
                JSONObject d = o.optJSONObject("responseData");
                String t = d == null ? "" : d.optString("translatedText").trim();
                full = o.optBoolean("quotaFinished") || t.toUpperCase(java.util.Locale.ROOT).startsWith("MYMEMORY WARNING");
                if (!full && o.optInt("responseStatus") == 200 && !t.isEmpty()) out = t;
            } else if (r.status == 429 || r.status == 403) {
                full = true;
            } else {
                android.util.Log.w("GameHubTraducao", "HTTP " + r.status);
            }
        } catch (Exception e) {
            android.util.Log.w("GameHubTraducao", "erro: " + e);
        }
        android.util.Log.i("GameHubTraducao", (out != null ? "ok" : full ? "limite do dia" : "falhou") + " (" + lang + "): " + en);
        if (out == null && !full) return en;
        try {
            cache = read(c, lang);   // o outro processo pode ter gravado no meio
            if (out != null) cache.put(en, out);
            if (full) cache.put("_cheio", today());
            Files.write(file(c, lang), cache.toString());
        } catch (Exception ignored) {
        }
        return out != null ? cap(out) : en;
    }

    /** Traduz o que falta da lista de um jogo, um texto por vez (devagar, para não pesar). progress: a cada 10 novas. */
    static void translateList(Context c, String lang, JSONArray list) {
        translateList(c, lang, list, null);
    }

    static void translateList(Context c, String lang, JSONArray list, Runnable progress) {
        if (list == null) return;
        int n = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject a = list.optJSONObject(i);
            if (a == null) continue;
            for (String k : new String[] { "title", "desc" }) {
                String en = a.optString(k);
                JSONObject cache = read(c, lang);
                if (en.isEmpty() || cache.has(en)) continue;
                if (today().equals(cache.optString("_cheio"))) return;
                translate(c, lang, en);
                if (progress != null && ++n % 10 == 0) progress.run();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /** Põe os textos traduzidos (os que já têm) na lista de jogos do hub; o original fica em "titleEn" e "descEn". */
    static JSONObject apply(Context c, String lang, JSONObject games) {
        if (!("pt".equals(lang) || "es".equals(lang))) return games;
        JSONObject cache = read(c, lang);
        try {
            JSONArray ids = games.names();
            for (int i = 0; ids != null && i < ids.length(); i++) {
                JSONObject g = games.optJSONObject(ids.getString(i));
                JSONArray list = g == null ? null : g.optJSONArray("list");
                for (int j = 0; list != null && j < list.length(); j++) {
                    JSONObject a = list.optJSONObject(j);
                    if (a == null) continue;
                    a.put("titleEn", a.optString("title")).put("descEn", a.optString("desc"));
                    a.put("title", cached(cache, a.optString("title"))).put("desc", cached(cache, a.optString("desc")));
                }
            }
        } catch (Exception ignored) {
        }
        return games;
    }
}
