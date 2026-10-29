package com.onix.gamehub;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Configurações em três níveis: global, por console e por jogo (o mais específico vale).
 * config.json = {"global": {...}, "console": {"snes": {...}}, "game": {"<id>": {...}}}
 * Cada nível pode ter "core" (opções do núcleo), "remap" (tecla do Android -> botão) e "turbo".
 */
final class Config {

    /** Padrões e os valores aceitos de cada configuração. */
    static final Map<String, Object> DEFAULTS = new HashMap<String, Object>();
    private static final Map<String, String[]> CHOICES = new HashMap<String, String[]>();
    private static final Map<String, int[]> RANGES = new HashMap<String, int[]>();

    static {
        choice("filter", "sharp", "sharp", "pixel", "smooth", "crt", "lcd");
        choice("scale", "fit", "fit", "integer", "stretch");
        choice("aspect", "core", "core", "4:3", "pixel", "16:9");
        range("volume", 100, 0, 100);
        range("latency", 64, 32, 128);
        range("runahead", 0, 0, 2);
        range("ffspeed", 3, 2, 8);
        range("deadzone", 25, 5, 60);
        range("turboRate", 3, 1, 10);
        flag("rewind", false);
        flag("autosave", true);
        flag("stickDpad", true);
        flag("showFps", false);
        // Interface do GameHub: "onix" segue o que está no Onix (som de navegação e animações).
        choice("sounds", "onix", "onix", "on", "off");
        choice("motion", "onix", "onix", "on", "off");
    }

    private static final Pattern CORE_KEY = Pattern.compile("^[A-Za-z0-9_.-]{1,80}$");

    private static void choice(String key, String def, String... values) {
        DEFAULTS.put(key, def);
        CHOICES.put(key, values);
    }

    private static void range(String key, int def, int min, int max) {
        DEFAULTS.put(key, def);
        RANGES.put(key, new int[]{min, max});
    }

    private static void flag(String key, boolean def) {
        DEFAULTS.put(key, def);
    }

    private Config() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "config.json");
    }

    static synchronized JSONObject all(Context c) {
        try {
            String s = Files.read(file(c));
            if (s != null) return new JSONObject(s);
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    private static JSONObject level(JSONObject all, String scope, boolean create) throws Exception {
        if ("global".equals(scope)) {
            if (!all.has("global") && create) all.put("global", new JSONObject());
            return all.optJSONObject("global");
        }
        String kind, id;
        if (scope.startsWith("console:")) {
            kind = "console";
            id = scope.substring(8);
            if (Consoles.byId(id) == null) return null;
        } else if (scope.startsWith("game:")) {
            kind = "game";
            id = scope.substring(5);
            if (!Files.ID.matcher(id).matches()) return null;
        } else {
            return null;
        }
        JSONObject group = all.optJSONObject(kind);
        if (group == null) {
            if (!create) return null;
            group = new JSONObject();
            all.put(kind, group);
        }
        JSONObject o = group.optJSONObject(id);
        if (o == null && create) {
            o = new JSONObject();
            group.put(id, o);
        }
        return o;
    }

    /**
     * Muda uma configuração. key = nome (filter, volume…), "core.<opção>", "remap.<tecla>" ou
     * "turbo"; value = JSON do valor, ou null para voltar a seguir o nível de cima.
     */
    static synchronized boolean set(Context c, String scope, String key, String value) {
        try {
            JSONObject all = all(c);
            JSONObject lv = level(all, scope, true);
            if (lv == null || key == null) return false;
            Object v = value == null ? null : new JSONArray("[" + value + "]").get(0);
            if (key.startsWith("core.") || key.startsWith("remap.")) {
                boolean core = key.startsWith("core.");
                String sub = key.substring(core ? 5 : 6);
                if (core ? !CORE_KEY.matcher(sub).matches() : !sub.matches("^\\d{1,3}$")) return false;
                JSONObject map = lv.optJSONObject(core ? "core" : "remap");
                if (map == null) {
                    map = new JSONObject();
                    lv.put(core ? "core" : "remap", map);
                }
                if (v == null) map.remove(sub);
                else if (core && v instanceof String && ((String) v).length() <= 120) map.put(sub, v);
                else if (!core && v instanceof Integer && (Integer) v >= -1 && (Integer) v <= 15) map.put(sub, v);
                else return false;
            } else if ("remap".equals(key) && v == null) {
                lv.remove("remap");
            } else if ("core".equals(key) && v == null) {
                lv.remove("core");
            } else if ("turbo".equals(key)) {
                if (v == null) lv.remove("turbo");
                else if (v instanceof Integer && (Integer) v >= 0 && (Integer) v <= 0xFFFF) lv.put("turbo", v);
                else return false;
            } else if (DEFAULTS.containsKey(key)) {
                if (v == null) lv.remove(key);
                else if (!valid(key, v)) return false;
                else lv.put(key, v);
            } else {
                return false;
            }
            return Files.write(file(c), all.toString());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean valid(String key, Object v) {
        Object def = DEFAULTS.get(key);
        if (def instanceof Boolean) return v instanceof Boolean;
        if (def instanceof Integer) {
            if (!(v instanceof Integer)) return false;
            int[] r = RANGES.get(key);
            int x = (Integer) v;
            return x >= r[0] && x <= r[1];
        }
        String[] ch = CHOICES.get(key);
        if (ch == null || !(v instanceof String)) return false;
        for (String s : ch) if (s.equals(v)) return true;
        return false;
    }

    /** As configurações que valem para um jogo: padrão < global < console < jogo. */
    static synchronized JSONObject effective(Context c, String console, String id) {
        JSONObject out = new JSONObject();
        JSONObject core = new JSONObject(), remap = new JSONObject();
        try {
            for (Map.Entry<String, Object> e : DEFAULTS.entrySet()) out.put(e.getKey(), e.getValue());
            out.put("turbo", 0);
            JSONObject all = all(c);
            JSONObject[] levels = {level(all, "global", false), level(all, "console:" + console, false), id == null ? null : level(all, "game:" + id, false)};
            for (JSONObject lv : levels) {
                if (lv == null) continue;
                Iterator<String> it = lv.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if ("core".equals(k) || "remap".equals(k)) {
                        JSONObject src = lv.getJSONObject(k), dst = "core".equals(k) ? core : remap;
                        Iterator<String> j = src.keys();
                        while (j.hasNext()) {
                            String s = j.next();
                            dst.put(s, src.get(s));
                        }
                    } else {
                        out.put(k, lv.get(k));
                    }
                }
            }
            out.put("core", core);
            out.put("remap", remap);
        } catch (Exception ignored) {
        }
        return out;
    }

    static synchronized void removeGame(Context c, String id) {
        try {
            JSONObject all = all(c);
            JSONObject g = all.optJSONObject("game");
            if (g != null && g.has(id)) {
                g.remove(id);
                Files.write(file(c), all.toString());
            }
        } catch (Exception ignored) {
        }
    }
}
