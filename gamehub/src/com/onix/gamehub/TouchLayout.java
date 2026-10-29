package com.onix.gamehub;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;

/**
 * Ajustes do controle na tela (touch.json, o mesmo para todos os consoles e perfis): tamanho,
 * transparência e onde cada grupo de botões fica, em pé ("p") e deitado ("l"). As posições são
 * o centro do grupo em fração da área do controle, então valem em qualquer tela.
 */
final class TouchLayout {

    static final float MIN_SCALE = 0.7f, MAX_SCALE = 1.5f, MIN_OPACITY = 0.3f;

    float scale = 1f, opacity = 0.85f;
    private JSONObject pos = new JSONObject();

    private static File file(Context c) {
        return new File(c.getFilesDir(), "touch.json");
    }

    static TouchLayout load(Context c) {
        TouchLayout t = new TouchLayout();
        try {
            String s = Files.read(file(c));
            if (s != null) {
                JSONObject o = new JSONObject(s);
                t.scale = clamp((float) o.optDouble("scale", 1), MIN_SCALE, MAX_SCALE);
                t.opacity = clamp((float) o.optDouble("opacity", 0.85), MIN_OPACITY, 1);
                JSONObject p = o.optJSONObject("pos");
                if (p != null) t.pos = p;
            }
        } catch (Exception ignored) {
        }
        return t;
    }

    void save(Context c) {
        try {
            Files.write(file(c), new JSONObject().put("scale", scale).put("opacity", opacity).put("pos", pos).toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    /** Centro salvo do grupo (fração x, y) ou null. */
    float[] get(boolean landscape, int group) {
        JSONArray a = pos.optJSONArray((landscape ? "l." : "p.") + group);
        if (a == null || a.length() != 2) return null;
        return new float[]{clamp((float) a.optDouble(0), 0, 1), clamp((float) a.optDouble(1), 0, 1)};
    }

    void set(boolean landscape, int group, float fx, float fy) {
        try {
            pos.put((landscape ? "l." : "p.") + group, new JSONArray().put(clamp(fx, 0, 1)).put(clamp(fy, 0, 1)));
        } catch (Exception ignored) {
        }
    }

    /** Volta ao padrão: tamanho, transparência e as posições daquela orientação. */
    void reset(boolean landscape) {
        scale = 1f;
        opacity = 0.85f;
        String prefix = landscape ? "l." : "p.";
        Iterator<String> it = pos.keys();
        java.util.List<String> drop = new java.util.ArrayList<String>();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith(prefix)) drop.add(k);
        }
        for (String k : drop) pos.remove(k);
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
