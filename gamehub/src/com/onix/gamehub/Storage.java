package com.onix.gamehub;

import android.content.Context;
import android.os.StatFs;

import org.json.JSONObject;

/**
 * Espaço da TV. O GameHub nunca enche a memória: sempre sobra para o sistema o maior valor entre
 * 2 GB e 10% do total. O que passar disso é "livre para jogos".
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
        return Math.max(2 * GB, total(c) / 10);
    }

    /** Quanto ainda cabe de jogos sem passar da reserva do sistema. */
    static long available(Context c) {
        return Math.max(0, free(c) - reserve(c));
    }

    static boolean fits(Context c, long bytes) {
        return bytes + 4L * 1024 * 1024 <= available(c);   // folga para saves e capa
    }

    static JSONObject info(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("total", total(c));
            o.put("free", free(c));
            o.put("reserve", reserve(c));
            o.put("available", available(c));
            o.put("games", Files.size(Files.roms(c)));
            o.put("used", Files.size(c.getFilesDir()));
        } catch (Exception ignored) {
        }
        return o;
    }
}
