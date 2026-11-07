package com.onix.gamehub;

import android.content.Context;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * RetroAchievements: a conta de cada perfil e os pedidos à internet do motor (jni/cheevos.c).
 *
 * A senha nunca é guardada: o login devolve um token, que fica em profiles/&lt;perfil&gt;/retroachievements.json
 * = {user, token, score, avatar}. Com a conta conectada, as conquistas do Cobalt param de contar (Conquistas.java)
 * e a aba Conquistas mostra as do RetroAchievements.
 *
 * O que cada jogo tem fica em profiles/&lt;perfil&gt;/ra-jogos.json = {idDoJogo: {id, title, badge, total, got,
 * points, gotPoints, list:[…], at}}, gravado pelo jogo (outro processo) e lido pelo hub.
 */
final class RetroAchievements {

    static final String LOGIN_URL = "https://retroachievements.org/dorequest.php";
    /** Para o "Criar conta" e o QR code. */
    static final String SITE = "https://retroachievements.org";

    private RetroAchievements() {
    }

    private static File accountFile(Context c, String profile) {
        return new File(Files.profileDir(c, profile), "retroachievements.json");
    }

    private static File gamesFile(Context c, String profile) {
        return new File(Files.profileDir(c, profile), "ra-jogos.json");
    }

    /** A conta conectada do perfil, ou null. */
    static JSONObject account(Context c, String profile) {
        try {
            String s = Files.read(accountFile(c, profile));
            if (s == null) return null;
            JSONObject o = new JSONObject(s);
            return o.optString("user").isEmpty() || o.optString("token").isEmpty() ? null : o;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean connected(Context c, String profile) {
        return account(c, profile) != null;
    }

    static void logout(Context c, String profile) {
        //noinspection ResultOfMethodCallIgnored
        accountFile(c, profile).delete();
    }

    /** Atualiza a pontuação e a foto que o jogo recebeu ao entrar. */
    static synchronized void updateAccount(Context c, String profile, JSONObject login) {
        JSONObject a = account(c, profile);
        if (a == null) return;
        try {
            a.put("score", login.optLong("score", a.optLong("score")));
            if (!login.optString("avatar").isEmpty()) a.put("avatar", login.optString("avatar"));
            Files.write(accountFile(c, profile), a.toString());
        } catch (Exception ignored) {
        }
    }

    /**
     * Entra com usuário e senha (só nesta hora a senha passa pelo app). Chamar fora da thread da tela.
     * Devolve {ok:true, user, score} ou {ok:false, error}.
     */
    static JSONObject login(Context c, String profile, String user, String password) {
        init(c);
        JSONObject out = new JSONObject();
        try {
            String body = "r=login2&u=" + URLEncoder.encode(user.trim(), "UTF-8") + "&p=" + URLEncoder.encode(password, "UTF-8");
            Response r = http(LOGIN_URL, body, "application/x-www-form-urlencoded");
            JSONObject o = r.status > 0 && r.body.length > 0 ? new JSONObject(new String(r.body, "UTF-8")) : null;
            if (o != null && o.optBoolean("Success") && !o.optString("Token").isEmpty()) {
                JSONObject a = new JSONObject()
                        .put("user", o.optString("User", user.trim()))
                        .put("token", o.optString("Token"))
                        .put("score", o.optLong("Score") + o.optLong("SoftcoreScore"))
                        .put("avatar", o.optString("AvatarUrl"))
                        .put("at", System.currentTimeMillis());
                Files.write(accountFile(c, profile), a.toString());
                return out.put("ok", true).put("user", a.optString("user")).put("score", a.optLong("score"));
            }
            String err = o != null ? o.optString("Error") : "";
            if (r.status == 0) err = "offline";
            else if (r.status == 401 || r.status == 403 || (o != null && "invalid_credentials".equals(o.optString("Code")))) err = "senha";
            return out.put("ok", false).put("error", err.isEmpty() ? "erro" : err);
        } catch (Exception e) {
            try {
                return out.put("ok", false).put("error", "erro");
            } catch (Exception ignored) {
                return out;
            }
        }
    }

    /** O jogo guardou o que tem (para a aba Conquistas). */
    static synchronized void saveGame(Context c, String profile, String gameId, JSONObject game) {
        try {
            String s = Files.read(gamesFile(c, profile));
            JSONObject all = s == null ? new JSONObject() : new JSONObject(s);
            game.put("at", System.currentTimeMillis());
            all.put(gameId, game);
            Files.write(gamesFile(c, profile), all.toString());
        } catch (Exception ignored) {
        }
    }

    static JSONObject games(Context c, String profile) {
        try {
            String s = Files.read(gamesFile(c, profile));
            return s == null ? new JSONObject() : new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** O número do console no RetroAchievements (rc_consoles.h), ou 0 se ele não tiver conquistas. */
    static int consoleId(String console, String core) {
        switch (console) {
            case "nes": return 7;
            case "snes": return 3;
            case "gba": return 5;
            case "gbc": return 6;
            case "md": return 1;
            case "ps1": return 12;
            case "atari": return "prosystem".equals(core) ? 51 : 25;
            case "arcade": return 27;
            case "n64": return 2;
            case "ds": return 18;
            case "psp": return 41;
            case "gc": return 16;
            case "wii": return 19;
            case "ps2": return 21;
            default: return 0;
        }
    }

    /** Como o RetroAchievements pede: Produto/versão (sistema) rcheevos/x.y. Sem ele, nada conta como hardcore. */
    static String userAgent(Context c) {
        String v = "2.0.0";
        try {
            v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        String rc = "";
        try {
            rc = Native.raAgent();
        } catch (Throwable ignored) {   // a biblioteca do motor não abriu
        }
        return "CobaltGameHub/" + v + " (Android " + Build.VERSION.RELEASE + ")" + (rc == null || rc.isEmpty() ? "" : " " + rc);
    }

    static final class Response {
        int status;
        byte[] body = new byte[0];
    }

    private static String agent;

    /** Um pedido HTTPS (GET sem post, POST com). status 0 = sem internet. Chamar fora da thread da tela. */
    static Response http(String url, String post, String type) {
        Response r = new Response();
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            if (agent != null) h.setRequestProperty("User-Agent", agent);
            if (post != null) {
                h.setDoOutput(true);
                h.setRequestMethod("POST");
                h.setRequestProperty("Content-Type", type != null ? type : "application/x-www-form-urlencoded");
                byte[] b = post.getBytes("UTF-8");
                h.setFixedLengthStreamingMode(b.length);
                OutputStream o = h.getOutputStream();
                o.write(b);
                o.close();
            }
            r.status = h.getResponseCode();
            InputStream in = r.status >= 400 ? h.getErrorStream() : h.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0;) bo.write(buf, 0, n);
                in.close();
                r.body = bo.toByteArray();
            }
        } catch (Exception e) {
            r.status = 0;
        } finally {
            if (h != null) h.disconnect();
        }
        return r;
    }

    /** Chamar uma vez por processo, antes dos pedidos. */
    static void init(Context c) {
        if (agent == null) agent = userAgent(c);
    }
}
