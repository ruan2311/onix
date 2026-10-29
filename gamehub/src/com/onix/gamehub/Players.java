package com.onix.gamehub;

import android.content.Context;
import android.view.InputDevice;

import org.json.JSONObject;

import java.io.File;

/**
 * Controle 1 e controle 2 definidos pela pessoa (Configurações → Controles). Guarda o
 * "descriptor" do aparelho, que continua o mesmo quando o controle desliga e volta. Quem não foi
 * definido entra na primeira vaga livre, como antes.
 *   players.json: {"1": {"d": descriptor, "n": nome}, "2": {...}}
 */
final class Players {

    static final int MAX = 2;

    private Players() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "players.json");
    }

    static synchronized JSONObject all(Context c) {
        try {
            String s = Files.read(file(c));
            if (s != null) return new JSONObject(s);
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    /** Define o aparelho como o controle "player" (1 ou 2); se ele era o outro, os dois trocam. */
    static synchronized boolean set(Context c, int player, InputDevice d) {
        if (player < 1 || player > MAX || d == null) return false;
        try {
            JSONObject all = all(c);
            String desc = d.getDescriptor();
            JSONObject old = all.optJSONObject(String.valueOf(player));
            for (int p = 1; p <= MAX; p++) {
                JSONObject o = all.optJSONObject(String.valueOf(p));
                if (p != player && o != null && desc.equals(o.optString("d"))) {
                    if (old != null) all.put(String.valueOf(p), old);
                    else all.remove(String.valueOf(p));
                }
            }
            all.put(String.valueOf(player), new JSONObject().put("d", desc).put("n", Gamepads.name(d)));
            return Files.write(file(c), all.toString());
        } catch (Exception e) {
            return false;
        }
    }

    static synchronized boolean clear(Context c, int player) {
        try {
            JSONObject all = all(c);
            all.remove(String.valueOf(player));
            return Files.write(file(c), all.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /** Porta (0 = controle 1, 1 = controle 2) definida para o aparelho, ou -1. */
    static int portOf(JSONObject all, InputDevice d) {
        if (d == null || all == null) return -1;
        String desc = d.getDescriptor();
        for (int p = 1; p <= MAX; p++) {
            JSONObject o = all.optJSONObject(String.valueOf(p));
            if (o != null && desc != null && desc.equals(o.optString("d"))) return p - 1;
        }
        return -1;
    }
}
