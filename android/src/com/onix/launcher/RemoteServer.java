package com.onix.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/**
 * Controle pelo celular: um servidor HTTP pequeno, só na rede de casa, enquanto o Onix está aberto.
 *
 * Conectar não precisa do controle da TV: o QR code fica à vista no menu do Onix e leva uma chave
 * aleatória (128 bits). Quem lê o QR (quem está vendo a TV) troca a chave por um token do aparelho,
 * que o celular guarda; depois ele reconecta sozinho. A chave muda a cada uso e uma vez por dia.
 * Todo pedido da API precisa do token no cabeçalho X-Onix-Token (um site de fora não consegue mandar
 * esse cabeçalho sem a liberação de CORS, que este servidor nunca dá). Só hashes dos tokens ficam
 * salvos. O que chega do celular vai para a página como JSON e é validado lá de novo.
 */
final class RemoteServer {

    interface Host {
        /** Comando do celular para a página (JSON já montado aqui). */
        void command(String json);

        /** O Onix está na frente (a página só responde com ele aberto). */
        boolean front();

        /** A chave do QR mudou ou um celular entrou/saiu: a página redesenha o QR. */
        void changed(String event);
    }

    static final int PORT = 8765;
    private static final int MAX_HEADER = 8 * 1024, MAX_BODY = 300 * 1024, MAX_DEVICES = 8, MAX_CONN = 6;
    private static final long KEY_MS = 24L * 60 * 60 * 1000;
    private static final String PREFS = "remote";

    private final Context ctx;
    private final Host host;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();
    private final Semaphore slots = new Semaphore(MAX_CONN);
    private volatile ServerSocket socket;
    private volatile int port;
    private volatile String state = "{}";
    private volatile String key;
    private volatile long keyAt;
    private volatile long lastActive;   // último pedido de um celular conectado

    RemoteServer(Context ctx, Host host) {
        this.ctx = ctx.getApplicationContext();
        this.host = host;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean enabled() {
        return prefs.getBoolean("on", true);
    }

    void setEnabled(boolean on) {
        prefs.edit().putBoolean("on", on).apply();
        if (on) start();
        else stop();
    }

    synchronized void start() {
        if (socket != null || !enabled()) return;
        for (int p = PORT; p < PORT + 5 && socket == null; p++) {
            try {
                ServerSocket s = new ServerSocket();
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(p), 16);
                socket = s;
                port = p;
            } catch (Exception ignored) {
            }
        }
        final ServerSocket s = socket;
        if (s == null) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                accept(s);
            }
        }, "onix-remote");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    synchronized void stop() {
        ServerSocket s = socket;
        socket = null;
        if (s != null) try {
            s.close();
        } catch (Exception ignored) {
        }
    }

    /** O que o celular mostra (apps, campo de texto aberto, foto esperada…), vindo da página. */
    void setState(String json) {
        state = json;
    }

    /** {"on","url","devices"}: a página monta o QR com a url (que já traz a chave). */
    String info() {
        JSONObject o = new JSONObject();
        try {
            o.put("on", enabled() && socket != null);
            String ip = localIp();
            o.put("url", ip == null || socket == null ? "" : "http://" + ip + ":" + port + "/#k=" + currentKey());
            o.put("devices", devices().length());
            // O controle aberto no celular fala com a TV a cada 1,5 s: sem nada há 20 s, não há ninguém.
            o.put("active", System.currentTimeMillis() - lastActive < 20000);
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    /** Configurações → "Desconectar todos". */
    void forgetAll() {
        prefs.edit().remove("devices").apply();
        rotateKey();
        host.changed("devices");
    }

    /* ---------------- Rede ---------------- */

    private void accept(ServerSocket s) {
        while (socket == s) {
            final Socket c;
            try {
                c = s.accept();
            } catch (Exception e) {
                break;
            }
            if (!slots.tryAcquire()) {
                closeQuietly(c);
                continue;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serve(c);
                    } finally {
                        slots.release();
                        closeQuietly(c);
                    }
                }
            }, "onix-remote-conn").start();
        }
    }

    private void serve(Socket c) {
        try {
            c.setSoTimeout(10000);
            InputStream in = new BufferedInputStream(c.getInputStream());
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 3) return;
            String method = req[0], path = req[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            int length = 0;
            String token = null;
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) continue;
                String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = lines[i].substring(colon + 1).trim();
                if (name.equals("content-length")) length = Integer.parseInt(value);
                else if (name.equals("x-onix-token")) token = value;
            }
            if (length < 0 || length > MAX_BODY) {
                send(c, 413, "text/plain", "grande demais".getBytes("UTF-8"));
                return;
            }
            byte[] body = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(body, read, length - read);
                if (n < 0) return;
                read += n;
            }
            route(c, method, path, token, new String(body, "UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private void route(Socket c, String method, String path, String token, String body) throws Exception {
        if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
            send(c, 200, "text/html; charset=utf-8", asset("remote.html"));
            return;
        }
        // Atalho na tela inicial do celular: nome e ícone do Onix.
        if (method.equals("GET") && path.equals("/manifest.json")) {
            send(c, 200, "application/manifest+json", ("{\"name\":\"Onix Controle\",\"short_name\":\"Onix\",\"start_url\":\"/\","
                    + "\"display\":\"standalone\",\"background_color\":\"#0b0c0f\",\"theme_color\":\"#0b0c0f\","
                    + "\"icons\":[{\"src\":\"/icon.png\",\"sizes\":\"192x192\",\"type\":\"image/png\"}]}").getBytes("UTF-8"));
            return;
        }
        if (method.equals("GET") && path.equals("/icon.png")) {
            send(c, 200, "image/png", asset("remote-icon.png"));
            return;
        }
        if (!path.startsWith("/api/")) {
            send(c, 404, "text/plain", "não encontrado".getBytes("UTF-8"));
            return;
        }
        if (method.equals("POST") && path.equals("/api/pair")) {
            JSONObject o = new JSONObject(body);
            String k = o.optString("key", "");
            if (k.isEmpty() || !k.equals(currentKey())) {
                json(c, 403, "{\"error\":\"key\"}");
                return;
            }
            String t = newToken();
            addDevice(t, o.optString("name", ""));
            rotateKey();
            host.changed("paired");
            json(c, 200, new JSONObject().put("token", t).toString());
            return;
        }
        if (token == null || !knownDevice(token)) {
            json(c, 401, "{\"error\":\"token\"}");
            return;
        }
        boolean wasActive = System.currentTimeMillis() - lastActive < 20000;
        lastActive = System.currentTimeMillis();
        if (!wasActive) host.changed("active");
        if (method.equals("GET") && path.equals("/api/state")) {
            JSONObject o = new JSONObject(state);
            o.put("front", host.front());
            json(c, 200, o.toString());
            return;
        }
        if (!method.equals("POST")) {
            json(c, 405, "{}");
            return;
        }
        if (path.equals("/api/bye")) {
            removeDevice(token);
            host.changed("devices");
            json(c, 200, "{}");
            return;
        }
        String type = path.substring(5);
        if (!type.matches("key|text|order|open|photo")) {
            json(c, 404, "{}");
            return;
        }
        if (!host.front()) {
            json(c, 409, "{\"error\":\"front\"}");
            return;
        }
        JSONObject o = new JSONObject(body);
        o.put("type", type);
        host.command(o.toString());
        json(c, 200, "{}");
    }

    private static String readHead(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int a = 0, b = 0, cc = 0, d;
        while ((d = in.read()) >= 0) {
            out.write(d);
            if (out.size() > MAX_HEADER) return null;
            if (a == '\r' && b == '\n' && cc == '\r' && d == '\n') return out.toString("UTF-8");
            a = b;
            b = cc;
            cc = d;
        }
        return null;
    }

    private static void json(Socket c, int code, String body) throws Exception {
        send(c, code, "application/json; charset=utf-8", body.getBytes("UTF-8"));
    }

    private static void send(Socket c, int code, String type, byte[] body) throws Exception {
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Erro") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = c.getOutputStream();
        out.write(head.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private static void closeQuietly(Socket c) {
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    private final java.util.Map<String, byte[]> assets = new java.util.HashMap<String, byte[]>();

    private synchronized byte[] asset(String name) throws Exception {
        byte[] b = assets.get(name);
        if (b == null) {
            InputStream in = ctx.getAssets().open(name);
            try {
                b = WeeklyPicks.readAll(in, 512 * 1024);
            } finally {
                in.close();
            }
            assets.put(name, b);
        }
        return b;
    }

    /** IPv4 da rede de casa (Wi-Fi ou cabo). */
    static String localIp() {
        try {
            String fallback = null;
            Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(list)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String name = ni.getName();
                    if (name.startsWith("wlan") || name.startsWith("eth")) return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------------- Chave do QR e aparelhos ---------------- */

    private synchronized String currentKey() {
        if (key == null) {
            key = prefs.getString("key", null);
            keyAt = prefs.getLong("keyAt", 0);
        }
        if (key == null || System.currentTimeMillis() - keyAt > KEY_MS) rotateKey();
        return key;
    }

    private synchronized void rotateKey() {
        key = randomHex(16);
        keyAt = System.currentTimeMillis();
        prefs.edit().putString("key", key).putLong("keyAt", keyAt).apply();
    }

    private String newToken() {
        return randomHex(24);
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        return sb.toString();
    }

    private static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.encodeToString(md.digest(token.getBytes("UTF-8")), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private synchronized JSONArray devices() {
        try {
            return new JSONArray(prefs.getString("devices", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private synchronized boolean knownDevice(String token) {
        if (token.length() != 48) return false;
        String h = hash(token);
        JSONArray list = devices();
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.optJSONObject(i);
            if (d != null && h.equals(d.optString("h"))) {
                if (System.currentTimeMillis() - d.optLong("seen") > 60 * 60 * 1000) {
                    try {
                        d.put("seen", System.currentTimeMillis());
                        prefs.edit().putString("devices", list.toString()).apply();
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }
        }
        return false;
    }

    private synchronized void addDevice(String token, String name) throws Exception {
        JSONArray list = devices(), out = new JSONArray();
        // O mais antigo sai quando passa do limite.
        for (int i = Math.max(0, list.length() - (MAX_DEVICES - 1)); i < list.length(); i++) out.put(list.get(i));
        String clean = name.replaceAll("[^\\p{L}\\p{N} ._-]", "");
        out.put(new JSONObject().put("h", hash(token)).put("name", clean.length() > 30 ? clean.substring(0, 30) : clean)
                .put("seen", System.currentTimeMillis()));
        prefs.edit().putString("devices", out.toString()).apply();
    }

    private synchronized void removeDevice(String token) {
        String h = hash(token);
        JSONArray list = devices(), out = new JSONArray();
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.optJSONObject(i);
            if (d != null && !h.equals(d.optString("h"))) out.put(d);
        }
        prefs.edit().putString("devices", out.toString()).apply();
    }
}
