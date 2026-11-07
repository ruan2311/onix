package com.onix.gamehub;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

/**
 * Conquistas do Cobalt: locais, por perfil, sem conta. A lista e as metas vêm de assets/conquistas.json (a mesma do
 * PC). O que é contado fica em profiles/&lt;perfil&gt;/conquistas.json = {stats, unlocked: {id: hora}}.
 * Os dois processos (GameHub e o jogo) leem do arquivo; cada escrita relê antes de gravar (como o played.json).
 *
 * Contadores guardados: plays, saves, rewind, ffwd, multi, night e session (maior partida seguida, em minutos).
 * Calculados na hora: library (jogos na biblioteca), favs, minutes (total), game (o jogo mais jogado, em minutos),
 * consoles (quantos consoles diferentes já jogou) e all (a platina: todas as outras).
 */
final class Conquistas {

    static final class Def {
        String id, tier, icon, stat;
        int pts, min;
        String[] pt, es;
        /** Só nas do RetroAchievements: a imagem da conquista (no lugar do emoji) e o título pequeno de cima. */
        android.graphics.Bitmap badge;
        String kicker;

        String title(String lang) {
            return ("es".equals(lang) ? es : pt)[0];
        }

        String desc(String lang) {
            return ("es".equals(lang) ? es : pt)[1];
        }
    }

    /** A partida que está rolando agora (ainda não foi para o played.json). */
    static final class Live {
        String id, console;
        long seconds;
    }

    interface Listener {
        void unlocked(List<Def> list);
    }

    private static List<Def> defs;
    private static volatile Listener listener;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private Conquistas() {
    }

    /** Quem mostra o pop-up neste processo (o jogo ou o hub). */
    static void setListener(Listener l) {
        listener = l;
    }

    static synchronized List<Def> defs(Context c) {
        if (defs != null) return defs;
        List<Def> out = new ArrayList<>();
        try {
            InputStream in = c.getAssets().open("conquistas.json");
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) b.write(buf, 0, n);
            in.close();
            JSONArray list = new JSONObject(b.toString("UTF-8")).getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                Def d = new Def();
                d.id = o.getString("id");
                d.tier = o.getString("tier");
                d.icon = o.optString("icon");
                d.stat = o.getString("stat");
                d.pts = o.optInt("pts");
                d.min = o.optInt("min", 1);
                d.pt = new String[] { o.getJSONArray("pt").getString(0), o.getJSONArray("pt").getString(1) };
                d.es = new String[] { o.getJSONArray("es").getString(0), o.getJSONArray("es").getString(1) };
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        defs = out;
        return defs;
    }

    private static File file(Context c, String profile) {
        return new File(Files.profileDir(c, profile), "conquistas.json");
    }

    private static JSONObject read(Context c, String profile) {
        try {
            String s = Files.read(file(c, profile));
            if (s != null) return new JSONObject(s);
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    /** Soma 1 a um contador (plays, saves, rewind, ffwd, multi, night) e confere o que saiu. */
    static void count(Context c, String profile, String stat, Live live) {
        update(c, profile, stat, 1, false, live);
    }

    /** Guarda o maior valor de um contador (session) e confere o que saiu. */
    static void max(Context c, String profile, String stat, long value, Live live) {
        update(c, profile, stat, value, true, live);
    }

    /** Só confere (biblioteca mudou, favorito, abriu o hub…). */
    static void check(Context c, String profile, Live live) {
        update(c, profile, null, 0, false, live);
    }

    private static synchronized void update(Context c, String profile, String stat, long value, boolean max, Live live) {
        // Com a conta do RetroAchievements conectada, as conquistas do Cobalt ficam paradas (voltam ao sair da conta).
        // Desativadas pela pessoa (aba Conquistas): nada conta.
        if (RetroAchievements.connected(c, profile) || !enabled(c, profile)) return;
        try {
            JSONObject all = read(c, profile);
            JSONObject stats = all.optJSONObject("stats");
            if (stats == null) stats = new JSONObject();
            boolean dirty = false;
            if (stat != null) {
                long old = stats.optLong(stat);
                long now = max ? Math.max(old, value) : old + value;
                if (now != old) {
                    stats.put(stat, now);
                    dirty = true;
                }
            }
            JSONObject unlocked = all.optJSONObject("unlocked");
            if (unlocked == null) unlocked = new JSONObject();
            JSONObject values = values(c, profile, stats, live);
            List<Def> fresh = new ArrayList<>();
            // Duas voltas: a platina depende das outras que podem sair agora.
            for (int pass = 0; pass < 2; pass++) {
                int missing = 0;
                for (Def d : defs(c)) if (!"all".equals(d.stat) && !unlocked.has(d.id)) missing++;
                values.put("all", missing == 0 ? 1 : 0);
                for (Def d : defs(c)) {
                    if (unlocked.has(d.id) || values.optLong(d.stat) < d.min) continue;
                    unlocked.put(d.id, System.currentTimeMillis());
                    fresh.add(d);
                }
            }
            if (!fresh.isEmpty()) dirty = true;
            if (dirty) {
                all.put("stats", stats);
                all.put("unlocked", unlocked);
                Files.write(file(c, profile), all.toString());
            }
            if (!fresh.isEmpty()) {
                final List<Def> list = fresh;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Listener l = listener;
                        if (l != null) l.unlocked(list);
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    /** Os valores de cada "stat", com a partida que está rolando somada. */
    private static JSONObject values(Context c, String profile, JSONObject stats, Live live) throws Exception {
        JSONObject v = new JSONObject();
        for (String k : new String[] { "plays", "saves", "rewind", "ffwd", "multi", "night", "session" }) v.put(k, stats.optLong(k));
        JSONArray games = Library.games(c);
        v.put("library", games.length());
        JSONObject consoleOf = new JSONObject();
        for (int i = 0; i < games.length(); i++) {
            JSONObject g = games.optJSONObject(i);
            if (g != null) consoleOf.put(g.optString("id"), g.optString("console"));
        }
        JSONObject played = Library.played(c, profile);
        long total = 0, best = 0, favs = 0;
        HashSet<String> consoles = new HashSet<>();
        JSONArray ids = played.names();
        for (int i = 0; ids != null && i < ids.length(); i++) {
            String id = ids.getString(i);
            JSONObject p = played.optJSONObject(id);
            if (p == null) continue;
            if (p.optBoolean("fav")) favs++;
            long t = p.optLong("time");
            if (live != null && id.equals(live.id)) t += live.seconds;
            total += t;
            best = Math.max(best, t);
            String con = consoleOf.optString(id);
            if (t > 0 && !con.isEmpty()) consoles.add(con);
        }
        if (live != null && live.id != null && !played.has(live.id)) {
            total += live.seconds;
            best = Math.max(best, live.seconds);
        }
        if (live != null && live.console != null) consoles.add(live.console);
        v.put("favs", favs);
        v.put("minutes", total / 60);
        v.put("game", best / 60);
        v.put("consoles", consoles.size());
        return v;
    }

    /** Para a aba Conquistas: cada uma com o progresso, se já saiu (e quando) e o total de pontos. */
    static synchronized String json(Context c, String profile, String lang) {
        JSONObject out = new JSONObject();
        try {
            JSONObject all = read(c, profile);
            JSONObject stats = all.optJSONObject("stats");
            if (stats == null) stats = new JSONObject();
            JSONObject unlocked = all.optJSONObject("unlocked");
            if (unlocked == null) unlocked = new JSONObject();
            JSONObject values = values(c, profile, stats, null);
            int missing = 0, points = 0, total = 0, got = 0;
            for (Def d : defs(c)) if (!"all".equals(d.stat) && !unlocked.has(d.id)) missing++;
            values.put("all", missing == 0 ? 1 : 0);
            JSONArray list = new JSONArray();
            for (Def d : defs(c)) {
                long at = unlocked.optLong(d.id);
                total += d.pts;
                if (at > 0) {
                    points += d.pts;
                    got++;
                }
                list.put(new JSONObject().put("id", d.id).put("tier", d.tier).put("pts", d.pts).put("icon", d.icon)
                        .put("title", d.title(lang)).put("desc", d.desc(lang)).put("min", d.min)
                        .put("value", Math.min(d.min, values.optLong(d.stat))).put("at", at));
            }
            out.put("list", list).put("points", points).put("total", total).put("got", got).put("count", defs(c).size())
                    .put("off", all.optBoolean("off"));
        } catch (Exception ignored) {
        }
        return out.toString();
    }

    /** A pessoa pode desligar todas as conquistas do perfil (as do Cobalt e as do RetroAchievements). */
    static boolean enabled(Context c, String profile) {
        return !read(c, profile).optBoolean("off");
    }

    static synchronized void setEnabled(Context c, String profile, boolean on) {
        try {
            JSONObject all = read(c, profile);
            all.put("off", !on);
            Files.write(file(c, profile), all.toString());
        } catch (Exception ignored) {
        }
    }

    /** 0 h às 4h59: a "Coruja". */
    static boolean night() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 5;
    }
}
