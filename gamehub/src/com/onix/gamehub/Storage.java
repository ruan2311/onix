package com.onix.gamehub;

import android.content.Context;
import android.os.StatFs;

import org.json.JSONObject;

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
