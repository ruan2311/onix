package com.onix.gamehub;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Biblioteca (library.json, a mesma para a casa toda) e o que cada perfil jogou (played.json).
 * Os dois processos (Game Hub e o jogo) leem do arquivo; cada escrita relê antes de gravar.
 */
final class Library {

    private Library() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "library.json");
    }

    private static File playedFile(Context c, String profile) {
        return new File(Files.profileDir(c, profile), "played.json");
    }

    static synchronized JSONArray games(Context c) {
        try {
            String s = Files.read(file(c));
            if (s != null) return new JSONObject(s).getJSONArray("games");
        } catch (Exception ignored) {
        }
        return new JSONArray();
    }

    private static void save(Context c, JSONArray games) throws Exception {
        if (!Files.write(file(c), new JSONObject().put("version", 1).put("games", games).toString())) throw new Exception("sem espaço");
    }

    static synchronized JSONObject game(Context c, String id) {
        JSONArray g = games(c);
        for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    static synchronized void add(Context c, JSONObject game) throws Exception {
        JSONArray g = games(c);
        g.put(game);
        save(c, g);
    }

    /** Muda campos de um jogo (nome, capa, Modo Infantil…). */
    static synchronized boolean update(Context c, String id, JSONObject fields) {
        try {
            JSONArray g = games(c);
            for (int i = 0; i < g.length(); i++) {
                JSONObject o = g.getJSONObject(i);
                if (!id.equals(o.optString("id"))) continue;
                JSONArray names = fields.names();
                for (int k = 0; names != null && k < names.length(); k++) o.put(names.getString(k), fields.get(names.getString(k)));
                save(c, g);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Apaga o jogo e tudo que é dele: ROM, capa, trapaças, saves e estados de todos os perfis. */
    static synchronized boolean remove(Context c, String id) {
        try {
            JSONArray g = games(c), keep = new JSONArray();
            JSONObject gone = null;
            for (int i = 0; i < g.length(); i++) {
                JSONObject o = g.getJSONObject(i);
                if (id.equals(o.optString("id"))) gone = o;
                else keep.put(o);
            }
            if (gone == null) return false;
            save(c, keep);
            new File(Files.roms(c), gone.optString("file")).delete();
            new File(Files.covers(c), id + ".jpg").delete();
            Files.cheats(c, id).delete();
            File[] profiles = Files.dir(c, "profiles").listFiles();
            if (profiles != null) {
                for (File p : profiles) {
                    new File(p, "saves/" + id + ".srm").delete();
                    Files.delete(new File(p, "states/" + id));
                    JSONObject played = readPlayed(new File(p, "played.json"));
                    if (played.has(id)) {
                        played.remove(id);
                        Files.write(new File(p, "played.json"), played.toString());
                    }
                }
            }
            Config.removeGame(c, id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JSONObject readPlayed(File f) {
        try {
            String s = Files.read(f);
            if (s != null) return new JSONObject(s);
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    static synchronized JSONObject played(Context c, String profile) {
        return readPlayed(playedFile(c, profile));
    }

    /** Fim de uma partida: guarda a hora e soma o tempo jogado. */
    static synchronized void recordPlay(Context c, String profile, String id, long seconds) {
        try {
            JSONObject all = played(c, profile);
            JSONObject p = all.optJSONObject(id);
            if (p == null) p = new JSONObject();
            p.put("last", System.currentTimeMillis());
            p.put("time", p.optLong("time") + Math.max(0, seconds));
            all.put(id, p);
            Files.write(playedFile(c, profile), all.toString());
        } catch (Exception ignored) {
        }
    }

    static synchronized boolean setFavorite(Context c, String profile, String id, boolean fav) {
        try {
            JSONObject all = played(c, profile);
            JSONObject p = all.optJSONObject(id);
            if (p == null) p = new JSONObject();
            p.put("fav", fav);
            all.put(id, p);
            return Files.write(playedFile(c, profile), all.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lista para a tela: cada jogo com o que o perfil jogou e se há um "continuar" (estado auto).
     * No Modo Infantil, só os jogos liberados.
     */
    static synchronized JSONArray list(Context c, String profile, boolean kids) {
        JSONArray g = games(c), out = new JSONArray();
        JSONObject played = played(c, profile);
        for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o == null) continue;
            if (kids && !o.optBoolean("kids")) continue;
            try {
                JSONObject x = new JSONObject(o.toString());
                String id = o.optString("id");
                JSONObject p = played.optJSONObject(id);
                if (p != null) {
                    x.put("last", p.optLong("last"));
                    x.put("time", p.optLong("time"));
                    x.put("fav", p.optBoolean("fav"));
                }
                x.put("resume", new File(Files.states(c, profile, id), "auto.state").isFile());
                out.put(x);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Os jogados mais recentes do perfil, do mais novo para o mais antigo. */
    static List<JSONObject> recent(Context c, String profile, boolean kids, int max) {
        JSONArray all = list(c, profile, kids);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && o.optLong("last") > 0) out.add(o);
        }
        Collections.sort(out, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(b.optLong("last"), a.optLong("last"));
            }
        });
        return out.size() > max ? out.subList(0, max) : out;
    }
}
