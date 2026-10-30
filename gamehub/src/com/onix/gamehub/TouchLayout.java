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
 *
 * Versão 2: cada botão se move sozinho. Os grupos da versão 1 ficam em "p.face" (botões de ação),
 * "p.sys" (SELECT e START) e "p.top" (menu e avançar), e o TouchPad reparte entre os botões.
 */
final class TouchLayout {

    static final float MIN_SCALE = 0.7f, MAX_SCALE = 1.5f, MIN_OPACITY = 0.3f, MIN_SIZE = 0.5f, MAX_SIZE = 2f;

    float scale = 1f, opacity = 0.85f;
    private JSONObject pos = new JSONObject();
    /* Ajuste de um botão só ("snes.B", "0" o direcional…): {"s": tamanho, "o": opacidade}. */
    private JSONObject each = new JSONObject();

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
                JSONObject e = o.optJSONObject("each");
                if (e != null) t.each = e;
                if (o.optInt("v", 1) < 2) {
                    for (String side : new String[]{"p.", "l."}) {
                        rename(t.pos, side + "1", side + "face");
                        rename(t.pos, side + "4", side + "sys");
                        rename(t.pos, side + "5", side + "top");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return t;
    }

    void save(Context c) {
        try {
            Files.write(file(c), new JSONObject().put("v", 2).put("scale", scale).put("opacity", opacity).put("pos", pos).put("each", each).toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private static void rename(JSONObject o, String from, String to) {
        Object v = o.remove(from);
        if (v != null) {
            try {
                o.put(to, v);
            } catch (Exception ignored) {
            }
        }
    }

    /** Centro salvo do grupo (fração x, y) ou null. */
    float[] get(boolean landscape, String key) {
        JSONArray a = pos.optJSONArray((landscape ? "l." : "p.") + key);
        if (a == null || a.length() != 2) return null;
        return new float[]{clamp((float) a.optDouble(0), 0, 1), clamp((float) a.optDouble(1), 0, 1)};
    }

    void set(boolean landscape, String key, float fx, float fy) {
        try {
            pos.put((landscape ? "l." : "p.") + key, new JSONArray().put(clamp(fx, 0, 1)).put(clamp(fy, 0, 1)));
        } catch (Exception ignored) {
        }
    }

    /** Tamanho do botão em relação ao resto (1 = igual). */
    float size(String key) {
        JSONObject o = each.optJSONObject(key);
        return o == null ? 1 : clamp((float) o.optDouble("s", 1), MIN_SIZE, MAX_SIZE);
    }

    /** Opacidade do botão: a dele, se tiver, ou a de todos. */
    float opacity(String key) {
        JSONObject o = each.optJSONObject(key);
        return o == null || !o.has("o") ? opacity : clamp((float) o.optDouble("o"), MIN_OPACITY, 1);
    }

    void setSize(String key, float v) {
        put(key, "s", clamp(v, MIN_SIZE, MAX_SIZE));
    }

    void setOpacity(String key, float v) {
        put(key, "o", clamp(v, MIN_OPACITY, 1));
    }

    private void put(String key, String field, float v) {
        try {
            JSONObject o = each.optJSONObject(key);
            if (o == null) each.put(key, o = new JSONObject());
            o.put(field, Math.round(v * 100) / 100.0);
        } catch (Exception ignored) {
        }
    }

    /** Um botão volta ao padrão: tamanho, opacidade e a posição naquela orientação. */
    void resetButton(boolean landscape, String key) {
        each.remove(key);
        pos.remove((landscape ? "l." : "p.") + key);
    }

    /** Some com um par antigo (versão 1) depois de repartido. */
    void drop(boolean landscape, String key) {
        pos.remove((landscape ? "l." : "p.") + key);
    }

    /** Volta ao padrão: tamanho, transparência, o ajuste de cada botão e as posições daquela orientação. */
    void reset(boolean landscape) {
        scale = 1f;
        opacity = 0.85f;
        each = new JSONObject();
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
