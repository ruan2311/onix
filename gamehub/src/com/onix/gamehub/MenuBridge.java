package com.onix.gamehub;

import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** Ponte "Menu" do menu durante o jogo (menu.html). O que mexe no núcleo vai para a thread do GL. */
final class MenuBridge {

    private final GameActivity g;
    private volatile String options = "{\"categories\":[],\"options\":[]}";

    MenuBridge(GameActivity g) {
        this.g = g;
    }

    private void emit(String type, String json) {
        Web.emit(g.menuView(), type, json);
    }

    @JavascriptInterface
    public String info() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", g.id);
            o.put("name", g.game.optString("name"));
            o.put("console", g.console.id);
            o.put("profile", g.profile);
            o.put("kids", g.kids);
            o.put("lang", g.lang);
            o.put("loaded", g.isLoaded());
            o.put("failed", g.isFailed());
            o.put("ff", Native.fastForward());
            o.put("settings", g.settings);
            o.put("gamepads", Gamepads.list(g));
            o.put("sounds", g.sounds);
            o.put("mobile", g.mobile);
            o.put("motion", g.motion);
            o.put("states", states());
            o.put("cover", new File(Files.covers(g), g.id + ".jpg").isFile());
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private JSONArray states() throws Exception {
        JSONArray out = new JSONArray();
        File dir = Files.states(g, g.profile, g.id);
        for (String slot : new String[]{"auto", "1", "2", "3", "4"}) {
            File f = new File(dir, slot + ".state");
            JSONObject o = new JSONObject().put("slot", slot);
            if (f.isFile()) o.put("time", f.lastModified()).put("shot", new File(dir, slot + ".jpg").isFile());
            out.put(o);
        }
        return out;
    }

    @JavascriptInterface
    public void resume() {
        g.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                g.closeMenu();
            }
        });
    }

    @JavascriptInterface
    public void quit() {
        g.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                g.quit();
            }
        });
    }

    @JavascriptInterface
    public boolean saveState(final String slot) {
        if (slot == null || !slot.matches("^[1-4]$") || !g.isLoaded()) return false;
        g.queue(new Runnable() {
            @Override
            public void run() {
                boolean ok = g.saveStateNow(slot);
                if (ok) g.toast(g.tr("Estado salvo no espaço " + slot, "Estado guardado en el espacio " + slot));
                try {
                    emit("saved", new JSONObject().put("slot", slot).put("ok", ok).put("states", states()).toString());
                } catch (Exception ignored) {
                }
            }
        });
        return true;
    }

    @JavascriptInterface
    public boolean loadState(final String slot) {
        if (slot == null || !Files.SLOT.matcher(slot).matches() || !g.isLoaded()) return false;
        g.queue(new Runnable() {
            @Override
            public void run() {
                boolean ok = g.loadStateNow(slot);
                try {
                    emit("loaded", new JSONObject().put("slot", slot).put("ok", ok).toString());
                } catch (Exception ignored) {
                }
                if (ok) {
                    g.toast(g.tr("Estado carregado", "Estado cargado"));
                    resume();
                }
            }
        });
        return true;
    }

    @JavascriptInterface
    public void reset() {
        if (!g.isLoaded()) return;
        g.queue(new Runnable() {
            @Override
            public void run() {
                Native.reset();
                resume();
            }
        });
    }

    @JavascriptInterface
    public void setFastForward(boolean on) {
        Native.setFastForward(on);
    }

    @JavascriptInterface
    public boolean setConfig(String scope, String key, String value) {
        if (g.kids || scope == null || key == null) return false;
        if (!scope.equals("global") && !scope.equals("console:" + g.console.id) && !scope.equals("game:" + g.id)) return false;
        boolean ok = Config.set(g, scope, key, value);
        if (ok) {
            if (value == null && key.startsWith("core.")) {
                // voltou ao padrão: o núcleo precisa receber o valor padrão de novo
                final String k = key.substring(5);
                final String def = defaultOf(k);
                if (def != null) g.queue(new Runnable() {
                    @Override
                    public void run() {
                        Native.setOption(k, def);
                    }
                });
            }
            g.reloadSettings();
        }
        return ok;
    }

    private String defaultOf(String key) {
        try {
            JSONArray list = new JSONObject(options).getJSONArray("options");
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                if (key.equals(o.optString("key"))) return o.optString("default");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Celular: editar o controle na tela (fecha o menu e abre o editor por cima do jogo). */
    @JavascriptInterface
    public void editTouch() {
        g.editPad();
    }

    /** Atribuir botões: a próxima tecla apertada vai para o menu (evento "key"). */
    @JavascriptInterface
    public void captureKey(boolean on) {
        g.capture = on && !g.kids;
    }

    @JavascriptInterface
    public String effective() {
        return g.settings.toString();
    }

    /** Opções do núcleo em uso (evento "options"). */
    @JavascriptInterface
    public void coreOptions() {
        if (!g.isLoaded()) {
            emit("options", options);
            return;
        }
        g.queue(new Runnable() {
            @Override
            public void run() {
                options = Native.options();
                emit("options", options);
            }
        });
    }

    @JavascriptInterface
    public String cheats() {
        String s = Files.read(Files.cheats(g, g.id));
        return s == null ? "[]" : s;
    }

    @JavascriptInterface
    public boolean setCheats(String json) {
        if (g.kids || json == null || json.length() > 64 * 1024) return false;
        try {
            JSONArray in = new JSONArray(json), out = new JSONArray();
            for (int i = 0; i < in.length() && i < 100; i++) {
                JSONObject o = in.getJSONObject(i);
                String code = o.optString("code").trim(), desc = o.optString("desc").trim();
                if (code.isEmpty() || code.length() > 200 || desc.length() > 80) continue;
                out.put(new JSONObject().put("code", code).put("desc", desc).put("on", o.optBoolean("on")));
            }
            if (!Files.write(Files.cheats(g, g.id), out.toString())) return false;
            g.queue(new Runnable() {
                @Override
                public void run() {
                    g.applyCheats();
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String gamepads() {
        return Gamepads.list(g).toString();
    }

    @JavascriptInterface
    public void openBluetooth() {
        g.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Gamepads.openBluetooth(g);
            }
        });
    }
}
