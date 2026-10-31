package com.onix.gamehub;

import android.content.Context;
import android.util.Base64;

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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor HTTP + WebSocket local para o controle de celular do Onix GameHub.
 * Roda na porta 8772 da TV durante o jogo.
 *
 * Entrega a página web do controle (gamepad.html) e recebe os botões via WebSocket
 * de baixíssima latência (2-5ms) na rede local, sem precisar de internet ou Bluetooth.
 */
final class RemoteGamepadServer {

    interface Listener {
        void onButtons(int port, int bits);
        void onMenu();
        void onFastForward(boolean on);
        void onClientsChanged(int count);
    }

    static final int PORT = 8772;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Context ctx;
    private final Listener listener;
    private final String consoleId;
    private final String gameTitle;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<Integer, ClientHandler>();
    private final AtomicInteger nextPort = new AtomicInteger(0);

    RemoteGamepadServer(Context ctx, Listener listener, String consoleId, String gameTitle) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.consoleId = consoleId != null ? consoleId : "snes";
        this.gameTitle = gameTitle != null ? gameTitle : "";
    }

    void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket s = new ServerSocket();
                    s.setReuseAddress(true);
                    s.bind(new InetSocketAddress(PORT));
                    serverSocket = s;

                    while (running && !s.isClosed()) {
                        try {
                            Socket client = s.accept();
                            client.setTcpNoDelay(true);
                            client.setSoTimeout(15000); // 15s timeout
                            ClientHandler handler = new ClientHandler(client);
                            new Thread(handler, "gamepad-client").start();
                        } catch (Exception e) {
                            if (!running) break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }, "gamepad-server");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception ignored) {
        }
        for (ClientHandler h : clients.values()) {
            h.close();
        }
        clients.clear();
    }

    int clientCount() {
        return clients.size();
    }

    String url() {
        String ip = localIp();
        return ip != null ? "http://" + ip + ":" + PORT + "/" : "";
    }

    static String localIp() {
        try {
            String fallback = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
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

    private final class ClientHandler implements Runnable {
        private final Socket socket;
        private int playerPort = -1;
        private volatile boolean closed;
        private OutputStream out;
        private long lastPacketAt;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                InputStream in = new BufferedInputStream(socket.getInputStream());
                out = socket.getOutputStream();

                // Lê a requisição HTTP inicial
                ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
                int b, prev = 0, count = 0;
                while ((b = in.read()) != -1) {
                    headerBuf.write(b);
                    count++;
                    if (prev == '\r' && b == '\n') {
                        byte[] bytes = headerBuf.toByteArray();
                        if (bytes.length >= 4 && bytes[bytes.length - 4] == '\r' && bytes[bytes.length - 3] == '\n'
                                && bytes[bytes.length - 2] == '\r' && bytes[bytes.length - 1] == '\n') {
                            break;
                        }
                    }
                    prev = b;
                    if (count > 8192) break; // cabeçalho muito grande
                }

                String headerStr = headerBuf.toString("UTF-8");
                String[] lines = headerStr.split("\r\n");
                if (lines.length == 0) {
                    close();
                    return;
                }

                String reqLine = lines[0];
                String[] parts = reqLine.split(" ");
                if (parts.length < 2) {
                    close();
                    return;
                }

                String path = parts[1];
                if (path.contains("?")) path = path.substring(0, path.indexOf("?"));

                // Verifica se é WebSocket Upgrade
                boolean isWs = false;
                String wsKey = null;
                for (String l : lines) {
                    int colon = l.indexOf(':');
                    if (colon < 0) continue;
                    String k = l.substring(0, colon).trim().toLowerCase();
                    String v = l.substring(colon + 1).trim();
                    if ("upgrade".equals(k) && "websocket".equalsIgnoreCase(v)) isWs = true;
                    if ("sec-websocket-key".equals(k)) wsKey = v;
                }

                if (isWs && wsKey != null) {
                    handleWsUpgrade(in, wsKey);
                } else {
                    handleHttpRequest(path);
                }
            } catch (Exception e) {
                close();
            }
        }

        private void handleHttpRequest(String path) throws Exception {
            byte[] body;
            String mime = "text/html; charset=utf-8";

            if ("/touch.js".equals(path)) {
                body = readFile("touch.js");
                mime = "application/javascript; charset=utf-8";
            } else if ("/info".equals(path)) {
                JSONObject info = new JSONObject();
                info.put("console", consoleId);
                info.put("title", gameTitle);
                info.put("players", clients.size());
                body = info.toString().getBytes("UTF-8");
                mime = "application/json; charset=utf-8";
            } else {
                body = readFile("gamepad.html");
                mime = "text/html; charset=utf-8";
            }

            if (body == null) {
                String notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n";
                out.write(notFound.getBytes("UTF-8"));
                out.flush();
                close();
                return;
            }

            String resp = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + body.length + "\r\n" +
                    "Connection: close\r\n" +
                    "Access-Control-Allow-Origin: *\r\n\r\n";
            out.write(resp.getBytes("UTF-8"));
            out.write(body);
            out.flush();
            close();
        }

        private byte[] readFile(String name) {
            try {
                InputStream is = ctx.getAssets().open(name);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                is.close();
                return baos.toByteArray();
            } catch (Exception e) {
                return null;
            }
        }

        private void handleWsUpgrade(InputStream in, String key) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((key + WS_GUID).getBytes("UTF-8"));
            String accept = Base64.encodeToString(digest, Base64.NO_WRAP);

            String handshake = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            out.write(handshake.getBytes("UTF-8"));
            out.flush();

            // Atribui porta do jogador (0 para P1, 1 para P2)
            playerPort = allocatePort();
            clients.put(playerPort, this);
            try { listener.onClientsChanged(clients.size()); } catch (Exception ignored) {}

            // Manda estado inicial
            JSONObject init = new JSONObject();
            init.put("type", "init");
            init.put("console", consoleId);
            init.put("title", gameTitle);
            init.put("player", playerPort);
            sendWsText(init.toString());

            // Loop de leitura WebSocket
            lastPacketAt = System.currentTimeMillis();
            while (running && !closed) {
                int b0 = in.read();
                if (b0 == -1) break;
                int opcode = b0 & 0x0F;

                int b1 = in.read();
                if (b1 == -1) break;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) {
                    len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                } else if (len == 127) {
                    break;
                }

                byte[] mask = new byte[4];
                if (masked) {
                    int r = 0;
                    while (r < 4) {
                        int c = in.read(mask, r, 4 - r);
                        if (c < 0) break;
                        r += c;
                    }
                }

                byte[] payload = new byte[(int) len];
                int r = 0;
                while (r < len) {
                    int c = in.read(payload, r, (int) len - r);
                    if (c < 0) break;
                    r += c;
                }

                if (masked) {
                    for (int i = 0; i < len; i++) {
                        payload[i] ^= mask[i % 4];
                    }
                }

                lastPacketAt = System.currentTimeMillis();

                if (opcode == 0x8) { // CLOSE
                    break;
                } else if (opcode == 0x9) { // PING
                    sendWsPong(payload);
                } else if (opcode == 0x2) { // BINARY: [player(1B), bits(2B)]
                    if (payload.length >= 3) {
                        int p = payload[0] & 0xFF;
                        int bits = (payload[1] & 0xFF) | ((payload[2] & 0xFF) << 8);
                        if (p >= 0 && p < 4) {
                            listener.onButtons(p, bits);
                        }
                    }
                } else if (opcode == 0x1) { // TEXT JSON
                    String text = new String(payload, "UTF-8");
                    handleJson(text);
                }
            }
            close();
        }

        private void handleJson(String text) {
            try {
                JSONObject msg = new JSONObject(text);
                String type = msg.optString("type");
                if ("btn".equals(type)) {
                    int p = msg.optInt("p", playerPort);
                    int bits = msg.optInt("b", 0);
                    listener.onButtons(p, bits);
                } else if ("ping".equals(type)) {
                    JSONObject pong = new JSONObject();
                    pong.put("type", "pong");
                    pong.put("t", msg.optLong("t"));
                    sendWsText(pong.toString());
                } else if ("menu".equals(type)) {
                    listener.onMenu();
                } else if ("fast".equals(type)) {
                    listener.onFastForward(msg.optBoolean("on"));
                }
            } catch (Exception ignored) {
            }
        }

        private synchronized void sendWsText(String text) {
            if (closed || out == null) return;
            try {
                byte[] b = text.getBytes("UTF-8");
                out.write(0x81);
                if (b.length <= 125) {
                    out.write(b.length);
                } else {
                    out.write(126);
                    out.write((b.length >> 8) & 0xFF);
                    out.write(b.length & 0xFF);
                }
                out.write(b);
                out.flush();
            } catch (Exception e) {
                close();
            }
        }

        private synchronized void sendWsPong(byte[] payload) {
            if (closed || out == null) return;
            try {
                out.write(0x8A);
                out.write(payload.length & 0x7F);
                out.write(payload);
                out.flush();
            } catch (Exception e) {
                close();
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            if (playerPort >= 0) {
                clients.remove(playerPort);
                listener.onButtons(playerPort, 0); // solta os botões na hora
                try { listener.onClientsChanged(clients.size()); } catch (Exception ignored) {}
            }
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private int allocatePort() {
        for (int p = 0; p < 4; p++) {
            if (!clients.containsKey(p)) return p;
        }
        return nextPort.getAndIncrement() % 4;
    }
}
