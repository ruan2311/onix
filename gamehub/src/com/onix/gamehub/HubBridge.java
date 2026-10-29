package com.onix.gamehub;

import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Ponte "Hub" da página hub.html. Tudo que chega do JS é validado aqui. */
final class HubBridge {

    private final HubActivity a;
    private final Map<String, String> probed = new HashMap<String, String>();

    HubBridge(HubActivity a) {
        this.a = a;
    }

    private boolean id(String id) {
        return id != null && Files.ID.matcher(id).matches();
    }

    @JavascriptInterface
    public String init() {
        try {
            JSONObject o = new JSONObject();
            o.put("profile", a.profile);
            o.put("profileName", a.profileName);
            o.put("kids", a.kids);
            o.put("lang", a.lang);
            o.put("open", a.open);
            o.put("fromOnix", a.fromOnix);
            o.put("version", a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName);
            o.put("onix", installed("com.aurora.launcher"));
            o.put("gamepads", Gamepads.list(a));
            o.put("onixSound", a.onixSound);
            o.put("onixMotion", a.onixMotion);
            o.put("abi", android.os.Build.SUPPORTED_ABIS.length > 0 ? android.os.Build.SUPPORTED_ABIS[0] : "");
            o.put("mobile", Device.mobile(a));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean installed(String pkg) {
        try {
            a.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @JavascriptInterface
    public String library() {
        return Library.list(a, a.profile, a.kids).toString();
    }

    /** Estados salvos de um jogo: [{slot, time}] dos que existem. */
    @JavascriptInterface
    public String states(String id) {
        JSONArray out = new JSONArray();
        if (!id(id)) return out.toString();
        File dir = Files.states(a, a.profile, id);
        for (String slot : new String[]{"auto", "1", "2", "3", "4"}) {
            File f = new File(dir, slot + ".state");
            if (!f.isFile()) continue;
            try {
                out.put(new JSONObject().put("slot", slot).put("time", f.lastModified()).put("shot", new File(dir, slot + ".jpg").isFile()));
            } catch (Exception ignored) {
            }
        }
        return out.toString();
    }

    /** mode: "resume" (do ponto em que parou), "new" (do começo) ou "1".."4" (estado salvo). */
    @JavascriptInterface
    public boolean play(final String id, final String mode) {
        if (!id(id) || mode == null || !mode.matches("^(resume|new|[1-4])$")) return false;
        final boolean[] ok = {false};
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ok[0] = a.startGame(id, mode);
            }
        });
        return true;
    }

    @JavascriptInterface
    public boolean setFavorite(String id, boolean fav) {
        return id(id) && Library.setFavorite(a, a.profile, id, fav);
    }

    @JavascriptInterface
    public boolean rename(String id, String name) {
        if (a.kids || !id(id) || name == null) return false;
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty() || name.length() > 80) return false;
        try {
            return Library.update(a, id, new JSONObject().put("name", name));
        } catch (Exception e) {
            return false;
        }
    }

    /** Liberar ou não o jogo nos perfis do Modo Infantil (só fora do Modo Infantil). */
    @JavascriptInterface
    public boolean setKids(String id, boolean allowed) {
        if (a.kids || !id(id)) return false;
        try {
            return Library.update(a, id, new JSONObject().put("kids", allowed));
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean remove(String id) {
        return !a.kids && id(id) && Library.remove(a, id);
    }

    @JavascriptInterface
    public boolean deleteState(String id, String slot) {
        if (a.kids || !id(id) || slot == null || !Files.SLOT.matcher(slot).matches()) return false;
        File dir = Files.states(a, a.profile, id);
        new File(dir, slot + ".jpg").delete();
        return new File(dir, slot + ".state").delete();
    }

    @JavascriptInterface
    public String config() {
        return Config.all(a).toString();
    }

    @JavascriptInterface
    public String effective(String console, String id) {
        if (Consoles.byId(console) == null) return "{}";
        return Config.effective(a, console, id(id) ? id : null).toString();
    }

    @JavascriptInterface
    public boolean setConfig(String scope, String key, String value) {
        if (a.kids || scope == null) return false;
        return Config.set(a, scope, key, value);
    }

    /** Opções do núcleo do console (lidas do próprio núcleo, traduzidas quando ele tem). */
    @JavascriptInterface
    public String coreOptions(String console) {
        Consoles.Console c = Consoles.byId(console);
        if (c == null) return "{}";
        String key = c.id + ":" + a.lang;
        String cached = probed.get(key);
        if (cached != null) return cached;
        String json = null;
        try {
            String lib = a.getApplicationInfo().nativeLibraryDir + "/lib" + c.core + ".so";
            json = Native.probe(lib, "es".equals(a.lang) ? Native.LANG_ES : Native.LANG_PT_BR);
        } catch (Throwable ignored) {
        }
        if (json == null || json.contains("\"options\":[]")) {
            // O núcleo só conta as opções com um jogo aberto: usa as da última partida.
            String saved = Files.read(new java.io.File(a.getFilesDir(), "options-" + c.id + "-" + a.lang + ".json"));
            if (saved != null) return saved;
            return "{\"categories\":[],\"options\":[],\"later\":true}";
        }
        probed.put(key, json);
        return json;
    }

    @JavascriptInterface
    public String cheats(String id) {
        if (!id(id)) return "[]";
        String s = Files.read(Files.cheats(a, id));
        return s == null ? "[]" : s;
    }

    /** [{desc, code, on}] — até 100 códigos de até 200 letras. */
    @JavascriptInterface
    public boolean setCheats(String id, String json) {
        if (a.kids || !id(id) || json == null || json.length() > 64 * 1024) return false;
        try {
            JSONArray in = new JSONArray(json), out = new JSONArray();
            for (int i = 0; i < in.length() && i < 100; i++) {
                JSONObject o = in.getJSONObject(i);
                String code = o.optString("code").trim(), desc = o.optString("desc").trim();
                if (code.isEmpty() || code.length() > 200 || desc.length() > 80) continue;
                out.put(new JSONObject().put("code", code).put("desc", desc).put("on", o.optBoolean("on")));
            }
            return Files.write(Files.cheats(a, id), out.toString());
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String storage() {
        return Storage.info(a).toString();
    }

    @JavascriptInterface
    public String uploadStart() {
        if (a.kids) return "{}";
        return a.upload.start().toString();
    }

    @JavascriptInterface
    public void uploadStop() {
        a.upload.stop();
    }

    @JavascriptInterface
    public String gamepads() {
        return Gamepads.list(a).toString();
    }

    /** Celular: editar o controle na tela (posição, tamanho e transparência). */
    @JavascriptInterface
    public boolean editTouch() {
        if (a.kids) return false;
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                a.startActivity(new android.content.Intent(a, TouchEditActivity.class).putExtra("lang", a.lang));
            }
        });
        return true;
    }

    /** Celular: adicionar jogos de uma pasta (folder) ou de arquivos soltos. */
    @JavascriptInterface
    public boolean pickGames(final boolean folder) {
        if (a.kids) return false;
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                a.pick(folder);
            }
        });
        return true;
    }

    /** Controle 1 e 2 definidos: {"1": {"name", "connected"}, "2": ...}. */
    @JavascriptInterface
    public String players() {
        JSONObject all = Players.all(a), out = new JSONObject();
        try {
            JSONArray pads = Gamepads.list(a);
            for (int p = 1; p <= Players.MAX; p++) {
                JSONObject o = all.optJSONObject(String.valueOf(p));
                if (o == null) continue;
                boolean on = false;
                for (int i = 0; i < pads.length(); i++) if (pads.getJSONObject(i).optInt("player") == p) on = true;
                out.put(String.valueOf(p), new JSONObject().put("name", o.optString("n")).put("connected", on));
            }
        } catch (Exception ignored) {
        }
        return out.toString();
    }

    /** O próximo botão apertado num controle de videogame define esse aparelho como o controle 1 ou 2. */
    @JavascriptInterface
    public void capturePad(int player) {
        a.capturePlayer = player >= 1 && player <= Players.MAX ? player : 0;
    }

    @JavascriptInterface
    public boolean clearPad(int player) {
        return Players.clear(a, player);
    }

    @JavascriptInterface
    public boolean openBluetooth() {
        final boolean[] ok = {false};
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ok[0] = Gamepads.openBluetooth(a);
            }
        });
        return true;
    }

    /** Trocar botões: a próxima tecla apertada vai para a página (evento "key"). */
    @JavascriptInterface
    public void captureKey(boolean on) {
        a.capture = on;
    }

    @JavascriptInterface
    public String update() {
        return Updater.get(a).json();
    }

    @JavascriptInterface
    public boolean checkUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Updater.get(a).check(true);
            }
        }, "gamehub-update").start();
        return true;
    }

    @JavascriptInterface
    public boolean installUpdate() {
        final Updater up = Updater.get(a);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!up.download()) return;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        up.install(a);
                    }
                });
            }
        }, "gamehub-update").start();
        return true;
    }

    @JavascriptInterface
    public void exit() {
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                a.finish();
            }
        });
    }
}
