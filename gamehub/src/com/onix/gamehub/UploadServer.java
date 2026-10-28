package com.onix.gamehub;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/**
 * Envio de jogos pelo celular: só fica ligado enquanto a tela "Adicionar jogos" está aberta.
 * O QR leva a http://<ip>:8770/#k=<chave>; a chave (128 bits, nova a cada abertura da tela) fica
 * no fragmento da URL, que o navegador não manda na rede, e volta no cabeçalho X-Key.
 * Cada arquivo vai num PUT /api/upload?name=<nome> e é gravado direto num arquivo temporário.
 */
final class UploadServer {

    interface Host {
        /** {"type":"start|progress|done","name","size","sent", e o resultado do Importer no done} */
        void event(JSONObject e);
    }

    private static final int PORT = 8770, MAX_CONN = 4, MAX_HEADER = 8 * 1024;
    private static final long MAX_UPLOAD = 64L * 1024 * 1024;

    private final Context ctx;
    private final Host host;
    private final SecureRandom random = new SecureRandom();
    private final Semaphore slots = new Semaphore(MAX_CONN);
    private volatile ServerSocket socket;
    private volatile int port;
    private volatile String key;
    private byte[] page;

    UploadServer(Context ctx, Host host) {
        this.ctx = ctx.getApplicationContext();
        this.host = host;
    }

    /** Liga com uma chave nova e devolve {"url"} para o QR ("" sem rede). */
    synchronized JSONObject start() {
        stop();
        byte[] b = new byte[16];
        random.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        key = sb.toString();
        for (int p = PORT; p < PORT + 5 && socket == null; p++) {
            try {
                ServerSocket s = new ServerSocket();
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(p), 8);
                socket = s;
                port = p;
            } catch (Exception ignored) {
            }
        }
        final ServerSocket s = socket;
        if (s != null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    accept(s);
                }
            }, "gamehub-upload");
            t.start();
        }
        JSONObject o = new JSONObject();
        try {
            String ip = localIp();
            o.put("url", ip == null || s == null ? "" : "http://" + ip + ":" + port + "/#k=" + key);
            o.put("ip", ip == null ? "" : ip + ":" + port);
        } catch (Exception ignored) {
        }
        return o;
    }

    synchronized void stop() {
        ServerSocket s = socket;
        socket = null;
        key = null;
        if (s != null) try {
            s.close();
        } catch (Exception ignored) {
        }
    }

    private void accept(ServerSocket s) {
        while (socket == s) {
            final Socket c;
            try {
                c = s.accept();
            } catch (Exception e) {
                break;
            }
            if (!slots.tryAcquire()) {
                close(c);
                continue;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serve(c);
                    } finally {
                        slots.release();
                        close(c);
                    }
                }
            }, "gamehub-upload-conn").start();
        }
    }

    private void serve(Socket c) {
        try {
            c.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(c.getInputStream(), 64 * 1024);
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 3) return;
            String method = req[0], path = req[1], query = "";
            int q = path.indexOf('?');
            if (q >= 0) {
                query = path.substring(q + 1);
                path = path.substring(0, q);
            }
            long length = -1;
            String k = null;
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) continue;
                String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = lines[i].substring(colon + 1).trim();
                if (name.equals("content-length")) length = Long.parseLong(value);
                else if (name.equals("x-key")) k = value;
            }
            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                send(c, 200, "text/html; charset=utf-8", page());
                return;
            }
            String current = key;
            if (current == null || k == null || !current.equals(k)) {
                json(c, 403, "{\"error\":\"key\"}");
                return;
            }
            if (method.equals("GET") && path.equals("/api/info")) {
                JSONObject o = Storage.info(ctx);
                o.put("es", "es".equals(Locale.getDefault().getLanguage()));
                json(c, 200, o.toString());
                return;
            }
            if (method.equals("PUT") && path.equals("/api/upload")) {
                upload(c, in, query, length);
                return;
            }
            json(c, 404, "{}");
        } catch (Exception ignored) {
        }
    }

    private void upload(Socket c, InputStream in, String query, long length) throws Exception {
        String name = null;
        for (String part : query.split("&")) {
            if (part.startsWith("name=")) name = URLDecoder.decode(part.substring(5), "UTF-8");
        }
        if (name != null) name = new File(name.replace('\\', '/')).getName();
        if (name == null || name.isEmpty() || name.length() > 200) {
            json(c, 400, "{\"ok\":false,\"error\":\"Arquivo sem nome.\"}");
            return;
        }
        boolean zip = "zip".equals(Consoles.extension(name));
        if (!zip && Consoles.byExtension(name) == null) {
            json(c, 415, new JSONObject().put("ok", false).put("name", name).put("error", "Esse tipo de arquivo não é de NES, SNES, GBA ou Mega Drive.").toString());
            return;
        }
        if (length <= 0 || length > MAX_UPLOAD) {
            json(c, 413, new JSONObject().put("ok", false).put("name", name).put("error", "Arquivo grande demais.").toString());
            return;
        }
        if (!Storage.fits(ctx, length * (zip ? 2 : 1))) {
            json(c, 507, new JSONObject().put("ok", false).put("name", name).put("error", "Sem espaço seguro na TV para esse jogo.").toString());
            return;
        }
        host.event(new JSONObject().put("type", "start").put("name", name).put("size", length));
        File tmp = new File(Files.dir(ctx, "incoming"), Long.toHexString(random.nextLong()) + ".part");
        OutputStream o = new FileOutputStream(tmp);
        long got = 0, lastEvent = 0;
        try {
            byte[] buf = new byte[64 * 1024];
            while (got < length) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, length - got));
                if (n < 0) break;
                o.write(buf, 0, n);
                got += n;
                long now = System.currentTimeMillis();
                if (now - lastEvent > 250) {
                    lastEvent = now;
                    host.event(new JSONObject().put("type", "progress").put("name", name).put("size", length).put("sent", got));
                }
            }
        } finally {
            o.close();
        }
        if (got != length) {
            tmp.delete();
            host.event(new JSONObject().put("type", "done").put("name", name).put("ok", false).put("error", "O envio parou no meio."));
            return;
        }
        JSONObject r = Importer.add(ctx, tmp, name);
        if (!r.has("name")) r.put("name", name);
        r.put("file", name);
        host.event(new JSONObject(r.toString()).put("type", "done"));
        json(c, 200, r.toString());
    }

    private synchronized byte[] page() throws Exception {
        if (page == null) {
            InputStream in = ctx.getAssets().open("upload.html");
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                page = o.toByteArray();
            } finally {
                in.close();
            }
        }
        return page;
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

    private static void close(Socket c) {
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    /** IPv4 da rede de casa (Wi-Fi ou cabo), como no Onix. */
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
}
