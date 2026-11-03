package com.onix.gamehub;

import android.content.Context;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Espaço da TV. O GameHub nunca enche a memória: a reserva do sistema acompanha o tamanho de cada
 * TV (10% do total, entre 768 MB e 2 GB). O piso fica acima do aviso de "pouco espaço" do Android
 * (5% do total, no máximo 500 MB). O que passar da reserva é "livre para jogos".
 * Antes era 2 GB fixos, e uma TV de 8 GB com 1,8 GB livres ficava com zero para jogos.
 */
final class Storage {

    private static final long GB = 1024L * 1024 * 1024;

    private Storage() {
    }

    static long total(Context c) {
        try {
            return new StatFs(c.getFilesDir().getPath()).getTotalBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    static long free(Context c) {
        try {
            return new StatFs(c.getFilesDir().getPath()).getAvailableBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    static long reserve(Context c) {
        return Math.min(2 * GB, Math.max(768L * 1024 * 1024, total(c) / 10));
    }

    /** Quanto ainda cabe de jogos sem passar da reserva do sistema. */
    static long available(Context c) {
        return Math.max(0, free(c) - reserve(c));
    }

    static boolean fits(Context c, long bytes) {
        return bytes + 4L * 1024 * 1024 <= available(c);   // folga para saves e capa
    }

    /** Pastas de passagem: envio em andamento, disco .cue esperando os .bin e cópia do celular. */
    private static final String[] TEMP = {"incoming", "ps1-pending", "ps1-loose"};

    static JSONObject info(Context c) {
        JSONObject o = new JSONObject();
        try {
            long games = Files.size(Files.roms(c)), saves = Files.size(Files.dir(c, "profiles")), covers = Files.size(Files.covers(c));
            long cached = imports(c, 0, false), temp = cached;
            for (String d : TEMP) temp += Files.size(new File(c.getFilesDir(), d));
            long used = Files.size(c.getFilesDir()) + cached;
            o.put("total", total(c));
            o.put("free", free(c));
            o.put("reserve", reserve(c));
            o.put("available", available(c));
            o.put("games", games);
            o.put("saves", saves);
            o.put("covers", covers);
            o.put("temp", temp);
            o.put("used", used);
        } catch (Exception ignored) {
        }
        return o;
    }

    /* ---------------- Limpeza ----------------
     * O que sobra de um envio que caiu, de um disco .cue que não ficou completo ou de um jogo que
     * saiu pela metade não aparece em lugar nenhum, mas ocupa espaço. Ao abrir o GameHub, depois de
     * apagar um jogo e pelo botão do Armazenamento, sai tudo que não é de nenhum jogo da biblioteca.
     * Só mexe no que está parado há 10 minutos (um envio ou uma importação em andamento fica).
     */
    private static final long QUIET = 10L * 60 * 1000, DAY = 24L * 60 * 60 * 1000;

    /** Apaga as sobras e devolve quantos bytes liberou. all: também os discos .cue à espera, de qualquer idade. */
    static synchronized long clean(Context c, boolean all) {
        long now = System.currentTimeMillis(), freed = 0;
        try {
            freed += imports(c, now - QUIET, true);
            freed += old(Files.dir(c, "incoming"), now - QUIET);
            freed += old(Files.dir(c, "ps1-pending"), all ? now - QUIET : now - DAY);
            freed += old(Files.dir(c, "ps1-loose"), all ? now - QUIET : now - DAY);

            Set<String> ids = new HashSet<String>(), files = new HashSet<String>();
            JSONArray g = Library.games(c);
            for (int i = 0; i < g.length(); i++) {
                JSONObject o = g.optJSONObject(i);
                if (o == null) continue;
                ids.add(o.optString("id"));
                String f = o.optString("file");
                int slash = f.indexOf('/');
                files.add(slash > 0 && !f.startsWith("arcade/") ? f.substring(0, slash) : f);
            }
            File roms = Files.roms(c);
            File[] list = roms.listFiles();
            if (list != null) for (File f : list) {
                if (f.getName().equals("arcade")) continue;
                if (!files.contains(f.getName()) && quiet(f, now)) freed += gone(f);
            }
            // Fliperama: fica o .zip de um jogo da biblioteca e as BIOS (neogeo.zip…), que os jogos usam.
            File[] arcade = new File(roms, "arcade").listFiles();
            if (arcade != null) for (File f : arcade) {
                if (!files.contains("arcade/" + f.getName()) && !Importer.isArcadeBios(c, f.getName()) && quiet(f, now)) freed += gone(f);
            }
            freed += orphans(Files.covers(c), ids, ".jpg", now);
            freed += orphans(Files.dir(c, "cheats"), ids, ".json", now);
            File[] profiles = Files.dir(c, "profiles").listFiles();
            if (profiles != null) for (File p : profiles) {
                freed += orphans(new File(p, "saves"), ids, ".srm", now);
                freed += orphans(new File(p, "states"), ids, "", now);
            }
        } catch (Exception ignored) {
        }
        return freed;
    }

    /** Cópias do celular (FolderImport) que ficaram no cache. */
    private static long imports(Context c, long before, boolean delete) {
        long n = 0;
        File[] list = c.getCacheDir().listFiles();
        if (list != null) for (File f : list) {
            if (!f.getName().startsWith("import-")) continue;
            if (!delete) n += Files.size(f);
            else if (f.lastModified() < before) n += gone(f);
        }
        return n;
    }

    private static long old(File dir, long before) {
        long n = 0;
        File[] list = dir.listFiles();
        if (list != null) for (File f : list) if (newest(f) < before) n += gone(f);
        return n;
    }

    /** Arquivos com o nome de um id (<id><ext>) que não está mais na biblioteca. */
    private static long orphans(File dir, Set<String> ids, String ext, long now) {
        long n = 0;
        File[] list = dir.listFiles();
        if (list != null) for (File f : list) {
            String name = f.getName();
            if (!name.endsWith(ext)) continue;
            String id = name.substring(0, name.length() - ext.length());
            if (Files.ID.matcher(id).matches() && !ids.contains(id) && quiet(f, now)) n += gone(f);
        }
        return n;
    }

    private static boolean quiet(File f, long now) {
        return newest(f) < now - QUIET;
    }

    /** A mudança mais recente dentro de uma pasta (um disco ainda chegando conta como novo). */
    private static long newest(File f) {
        long t = f.lastModified();
        File[] list = f.isDirectory() ? f.listFiles() : null;
        if (list != null) for (File x : list) t = Math.max(t, newest(x));
        return t;
    }

    private static long gone(File f) {
        long n = Files.size(f);
        Files.delete(f);
        return f.exists() ? 0 : n;
    }
}
