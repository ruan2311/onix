package com.onix.gamehub;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;

/**
 * Ajustes do controle na tela (touch.json, o mesmo para todos os consoles e perfis). Em pé e
 * deitado são dois layouts independentes, como no Onix Controle (touch.js, v4): cada um tem o
 * tamanho, a transparência, onde cada botão fica e o ajuste de cada botão. Mexer em um não muda o
 * outro. As posições são o centro do grupo em fração da área do controle, então valem em qualquer
 * tela. Só o "analógico no lugar da cruz" vale para os dois.
 *
 * Versões antigas: a 1 tinha grupos ("1" ação, "4" SELECT e START, "5" menu e avançar), que viram
 * "face", "sys" e "top" e o TouchPad reparte entre os botões; a 2 guardava as posições como
 * "p.<botão>" e "l.<botão>" com um tamanho e uma transparência só, que vão para os dois lados.
 */
final class TouchLayout {

    static final float MIN_SCALE = 0.7f, MAX_SCALE = 1.5f, MIN_OPACITY = 0.3f, MIN_SIZE = 0.5f, MAX_SIZE = 2f;

    /** Um lado (em pé ou deitado). */
    private static final class Side {
        float scale = 1f, opacity = 0.85f;
        JSONObject pos = new JSONObject();
        /* Ajuste de um botão só ("snes.B", "0" o direcional…): {"s": tamanho, "o": opacidade}. */
        JSONObject each = new JSONObject();

        JSONObject json() throws Exception {
            return new JSONObject().put("scale", scale).put("opacity", opacity).put("pos", pos).put("each", each);
        }

        static Side of(JSONObject o) {
            Side s = new Side();
            if (o == null) return s;
            s.scale = clamp((float) o.optDouble("scale", 1), MIN_SCALE, MAX_SCALE);
            s.opacity = clamp((float) o.optDouble("opacity", 0.85), MIN_OPACITY, 1);
            JSONObject p = o.optJSONObject("pos");
            if (p != null) s.pos = p;
            JSONObject e = o.optJSONObject("each");
            if (e != null) s.each = e;
            return s;
        }
    }

    private Side port = new Side(), land = new Side();
    boolean stick;   // analógico no lugar da cruz

    private static File file(Context c) {
        return new File(c.getFilesDir(), "touch.json");
    }

    static TouchLayout load(Context c) {
        TouchLayout t = new TouchLayout();
        try {
            String s = Files.read(file(c));
            if (s != null) {
                JSONObject o = new JSONObject(s);
                t.stick = o.optBoolean("stick");
                if (o.optInt("v", 1) >= 4) {
                    t.port = Side.of(o.optJSONObject("portrait"));
                    t.land = Side.of(o.optJSONObject("landscape"));
                } else {
                    // v1 e v2: o tamanho, a transparência e o ajuste de cada botão valiam para os
                    // dois lados; cada lado começa com uma cópia, e as posições se separam pelo prefixo.
                    JSONObject pos = o.optJSONObject("pos");
                    if (pos == null) pos = new JSONObject();
                    if (o.optInt("v", 1) < 2) {
                        for (String side : new String[]{"p.", "l."}) {
                            rename(pos, side + "1", side + "face");
                            rename(pos, side + "4", side + "sys");
                            rename(pos, side + "5", side + "top");
                        }
                    }
                    for (Side d : new Side[]{t.port, t.land}) {
                        d.scale = clamp((float) o.optDouble("scale", 1), MIN_SCALE, MAX_SCALE);
                        d.opacity = clamp((float) o.optDouble("opacity", 0.85), MIN_OPACITY, 1);
                        JSONObject e = o.optJSONObject("each");
                        d.each = e == null ? new JSONObject() : new JSONObject(e.toString());
                    }
                    Iterator<String> it = pos.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if (k.startsWith("p.")) t.port.pos.put(k.substring(2), pos.get(k));
                        else if (k.startsWith("l.")) t.land.pos.put(k.substring(2), pos.get(k));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return t;
    }

    void save(Context c) {
        try {
            Files.write(file(c), new JSONObject().put("v", 4).put("stick", stick)
                    .put("portrait", port.json()).put("landscape", land.json()).toString().getBytes("UTF-8"));
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

    private Side side(boolean landscape) {
        return landscape ? land : port;
    }

    /** Tamanho de todos os botões daquela orientação. */
    float scale(boolean landscape) {
        return side(landscape).scale;
    }

    void setScale(boolean landscape, float v) {
        side(landscape).scale = clamp(v, MIN_SCALE, MAX_SCALE);
    }

    /** Opacidade de todos os botões daquela orientação. */
    float opacity(boolean landscape) {
        return side(landscape).opacity;
    }

    void setOpacityAll(boolean landscape, float v) {
        side(landscape).opacity = clamp(v, MIN_OPACITY, 1);
    }

    /** Centro salvo do grupo (fração x, y) ou null. */
    float[] get(boolean landscape, String key) {
        JSONArray a = side(landscape).pos.optJSONArray(key);
        if (a == null || a.length() != 2) return null;
        return new float[]{clamp((float) a.optDouble(0), 0, 1), clamp((float) a.optDouble(1), 0, 1)};
    }

    void set(boolean landscape, String key, float fx, float fy) {
        try {
            side(landscape).pos.put(key, new JSONArray().put(clamp(fx, 0, 1)).put(clamp(fy, 0, 1)));
        } catch (Exception ignored) {
        }
    }

    /** Tamanho do botão em relação ao resto (1 = igual). */
    float size(boolean landscape, String key) {
        JSONObject o = side(landscape).each.optJSONObject(key);
        return o == null ? 1 : clamp((float) o.optDouble("s", 1), MIN_SIZE, MAX_SIZE);
    }

    /** Opacidade do botão: a dele, se tiver, ou a de todos. */
    float opacity(boolean landscape, String key) {
        JSONObject o = side(landscape).each.optJSONObject(key);
        return o == null || !o.has("o") ? side(landscape).opacity : clamp((float) o.optDouble("o"), MIN_OPACITY, 1);
    }

    void setSize(boolean landscape, String key, float v) {
        put(landscape, key, "s", clamp(v, MIN_SIZE, MAX_SIZE));
    }

    void setOpacity(boolean landscape, String key, float v) {
        put(landscape, key, "o", clamp(v, MIN_OPACITY, 1));
    }

    private void put(boolean landscape, String key, String field, float v) {
        try {
            JSONObject each = side(landscape).each, o = each.optJSONObject(key);
            if (o == null) each.put(key, o = new JSONObject());
            o.put(field, Math.round(v * 100) / 100.0);
        } catch (Exception ignored) {
        }
    }

    /** Um botão volta ao padrão naquela orientação: tamanho, opacidade e posição. */
    void resetButton(boolean landscape, String key) {
        side(landscape).each.remove(key);
        side(landscape).pos.remove(key);
    }

    /** Some com um par antigo (versão 1) depois de repartido. */
    void drop(boolean landscape, String key) {
        side(landscape).pos.remove(key);
    }

    /** Volta ao padrão só a orientação que está na tela: a outra fica como estava. */
    void reset(boolean landscape) {
        if (landscape) land = new Side();
        else port = new Side();
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
