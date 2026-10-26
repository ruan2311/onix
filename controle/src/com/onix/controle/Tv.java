package com.onix.controle;

import android.content.Context;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/**
 * Conexão com uma Google TV pelo protocolo de controle remoto dela (o mesmo do app Google TV):
 * pareamento na porta 6467 (a TV mostra um código de 6 caracteres) e controle na 6466.
 * Testado numa Philips Google TV (Android TV 14): teclas, Início, volume, texto e abrir apps por link.
 * Cada connect() cria uma Conn nova; a anterior para sozinha e não avisa mais a tela.
 */
final class Tv {

    interface Listener {
        /** Sempre fora da UI thread. */
        void on(String type, JSONObject data);
    }

    static final int PAIR_PORT = 6467, REMOTE_PORT = 6466;
    /** Recursos anunciados à TV (o mesmo valor dos apps de controle conhecidos). */
    private static final int FEATURES = 622;
    /** O controle virtual da TV leva um instante para ligar depois do "ativo". */
    private static final long WARMUP_MS = 1200;

    private final Context ctx;
    private final Listener listener;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private volatile Conn conn;

    Tv(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    boolean ready() {
        Conn c = conn;
        return c != null && c.readyAt != 0 && System.currentTimeMillis() >= c.readyAt;
    }

    /** Conecta (pin != null: TV já pareada) ou pareia (pin == null). */
    synchronized void connect(String host, String name, String pin) {
        close();
        final Conn c = new Conn(host, name, pin);
        conn = c;
        new Thread(new Runnable() {
            public void run() {
                if (c.pin == null && !c.pair()) return;
                c.session();
            }
        }, "tv").start();
    }

    synchronized void close() {
        Conn c = conn;
        conn = null;
        if (c != null) c.close();
    }

    /** Código de 6 caracteres que apareceu na TV. */
    void code(String code) {
        Conn c = conn;
        if (c != null) c.codes.offer(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
    }

    // --- comandos ---

    void key(int keycode) {
        send(new Proto().msg(10, new Proto().num(1, keycode).num(2, 3)).build());
    }

    /** Segurar: down = START_LONG, depois END_LONG. */
    void press(int keycode, boolean down) {
        send(new Proto().msg(10, new Proto().num(1, keycode).num(2, down ? 1 : 2)).build());
    }

    /** Abre um app ou um título por link (https ou o esquema do app). */
    void link(String url) {
        Conn c = conn;
        if (c != null) c.lastLink = url;
        send(new Proto().msg(90, new Proto().str(1, url)).build());
    }

    /**
     * Texto do campo aberto na TV: o campo inteiro passa a ser este texto. Usa o número do campo
     * que a TV mandou ao abrir o teclado (com o da edição, ela ignora o texto).
     */
    boolean text(String value) {
        Conn c = conn;
        if (c == null || c.fieldCounter < 0) return false;
        int end = Math.max(value.length() - 1, 0);
        Proto obj = new Proto().num(1, end).num(2, end).str(3, value);
        send(new Proto().msg(21, new Proto().num(1, c.imeCounter).num(2, c.fieldCounter)
                .msg(3, new Proto().num(1, 1).msg(2, obj))).build());
        return true;
    }

    private void send(final byte[] msg) {
        final Conn c = conn;
        if (c == null) return;
        writer.execute(new Runnable() {
            public void run() {
                try {
                    long ready = c.readyAt;
                    if (ready == 0) return;   // sem conexão: o toque se perde (a tela avisa)
                    long wait = ready - System.currentTimeMillis();
                    if (wait > 0) Thread.sleep(wait);
                    OutputStream o = c.out;
                    if (o != null) Proto.write(o, msg);
                } catch (Exception ignored) {
                    // a leitura percebe a queda e reconecta
                }
            }
        });
    }

    // --- uma conexão com uma TV ---

    private final class Conn {
        final String host, name;
        volatile String pin;
        final LinkedBlockingQueue<String> codes = new LinkedBlockingQueue<String>();
        volatile boolean closed;
        volatile SSLSocket socket;
        volatile OutputStream out;
        volatile long readyAt;   // 0 = ainda não ativo
        volatile long imeCounter, fieldCounter = -1;
        volatile String lastLink;

        Conn(String host, String name, String pin) {
            this.host = host;
            this.name = name;
            this.pin = pin;
        }

        void close() {
            closed = true;
            readyAt = 0;
            out = null;
            codes.offer("");
            final SSLSocket s = socket;
            socket = null;
            if (s != null) {
                // Fechar o TLS manda um aviso à TV: é rede, então fora da UI thread.
                new Thread(new Runnable() {
                    public void run() {
                        quiet(s);
                    }
                }, "tv-close").start();
            }
        }

        boolean pair() {
            state("pairing", null);
            SSLSocket s = null;
            try {
                X509Certificate[] seen = new X509Certificate[1];
                s = open(this, PAIR_PORT, null, seen);
                socket = s;
                InputStream in = s.getInputStream();
                OutputStream o = s.getOutputStream();
                Proto enc = new Proto().num(1, 3).num(2, 6);   // hexadecimal, 6 símbolos

                Proto.write(o, head().msg(10, new Proto().str(1, "com.onix.controle").str(2, "Onix Controle")).build());
                check(Proto.parse(Proto.read(in)));
                Proto.write(o, head().msg(20, new Proto().msg(1, enc).num(3, 1)).build());
                check(Proto.parse(Proto.read(in)));
                Proto.write(o, head().msg(30, new Proto().msg(1, new Proto().num(1, 3).num(2, 6)).num(2, 1)).build());
                check(Proto.parse(Proto.read(in)));

                state("code", null);
                String code = codes.poll(5, TimeUnit.MINUTES);
                if (closed) return false;
                if (code == null || code.isEmpty()) {
                    state("error", "O tempo para digitar o código acabou.");
                    return false;
                }
                if (!code.matches("[0-9A-F]{6}")) {
                    state("error", "O código tem 6 letras e números, como aparece na TV.");
                    return false;
                }
                byte[] secret = secret((RSAPublicKey) Cert.get(ctx).cert.getPublicKey(), (RSAPublicKey) seen[0].getPublicKey(), code);
                if ((secret[0] & 0xFF) != Integer.parseInt(code.substring(0, 2), 16)) {
                    state("error", "Código errado. Confira o que está na TV e tente de novo.");
                    return false;
                }
                Proto.write(o, head().msg(40, new Proto().bytes(1, secret)).build());
                check(Proto.parse(Proto.read(in)));
                pin = Cert.fingerprint(seen[0]);
                if (closed) return false;
                JSONObject d = new JSONObject();
                d.put("host", host);
                d.put("name", name);
                d.put("pin", pin);
                listener.on("paired", d);
                return true;
            } catch (Exception e) {
                if (!closed) state("error", "A TV não aceitou o pareamento. Tente de novo.");
                return false;
            } finally {
                socket = null;
                quiet(s);
            }
        }

        void session() {
            int fails = 0;
            while (!closed) {
                state("connecting", null);
                SSLSocket s = null;
                boolean active = false;
                try {
                    s = open(this, REMOTE_PORT, pin, null);
                    s.setSoTimeout(30000);   // a TV manda um ping a cada poucos segundos
                    socket = s;
                    out = s.getOutputStream();
                    InputStream in = s.getInputStream();
                    fieldCounter = -1;
                    while (!closed) {
                        if (handle(Proto.parse(Proto.read(in)))) active = true;
                        fails = 0;
                    }
                } catch (SSLException e) {
                    if (closed) return;
                    // Certificado da TV mudou, ou a TV recusou este celular logo de cara: parear de novo.
                    if (String.valueOf(e.getMessage()).contains("PIN") || !active) {
                        state("unpaired", null);
                        return;
                    }
                } catch (Exception e) {
                    if (closed) return;
                } finally {
                    readyAt = 0;
                    out = null;
                    socket = null;
                    quiet(s);
                }
                fails++;
                state("offline", null);
                try {
                    Thread.sleep(Math.min(1000L * fails, 5000));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        /** Trata uma mensagem da TV; true quando a TV deixou este celular ativo. */
        boolean handle(Proto.Msg m) throws Exception {
            OutputStream o = out;
            if (o == null) return false;
            if (m.has(1)) {   // configure
                Proto info = new Proto().str(1, android.os.Build.MODEL).str(2, android.os.Build.MANUFACTURER)
                        .num(3, 1).str(4, "1").str(5, "com.onix.controle").str(6, "1");
                Proto.write(o, new Proto().msg(1, new Proto().num(1, FEATURES).msg(2, info)).build());
            } else if (m.has(2)) {   // ativo
                Proto.write(o, new Proto().msg(2, new Proto().num(1, FEATURES)).build());
                readyAt = System.currentTimeMillis() + WARMUP_MS;
                state("connected", null);
                return true;
            } else if (m.has(8)) {   // ping
                Proto.Msg p = m.msg(8);
                Proto.write(o, new Proto().msg(9, new Proto().num(1, p == null ? 0 : p.num(1, 0))).build());
            } else if (m.has(20)) {   // teclado: app na frente e, se houver, o campo de texto aberto
                Proto.Msg k = m.msg(20);
                Proto.Msg app = k.msg(1);
                if (app != null && app.has(12)) emit("app", new JSONObject().put("pkg", app.str(12)));
                Proto.Msg field = k.msg(2);
                if (field != null) field(field);
                else {
                    fieldCounter = -1;
                    emit("field", new JSONObject().put("open", false));
                }
            } else if (m.has(21)) {   // edição do campo vinda da TV
                imeCounter = m.msg(21).num(1, imeCounter);
            } else if (m.has(22)) {   // teclado aberto/atualizado
                Proto.Msg st = m.msg(22).msg(2);
                if (st != null) field(st);
            } else if (m.has(40)) {   // TV ligada ou em standby
                emit("power", new JSONObject().put("on", m.msg(40).num(1, 0) != 0));
            } else if (m.has(50)) {   // volume
                Proto.Msg v = m.msg(50);
                JSONObject d = new JSONObject();
                d.put("level", v.num(7, 0));
                d.put("max", v.num(6, 100));
                d.put("muted", v.num(8, 0) != 0);
                emit("volume", d);
            } else if (m.has(3)) {   // erro: em geral um link que a TV não abriu
                String link = lastLink;
                lastLink = null;
                if (link != null) emit("linkError", new JSONObject().put("link", link));
            }
            return false;
        }

        private void field(Proto.Msg st) throws Exception {
            fieldCounter = st.num(1, fieldCounter);
            JSONObject d = new JSONObject();
            d.put("open", true);
            d.put("value", st.str(2));
            d.put("label", st.str(6));
            emit("field", d);
        }

        private void state(String s, String msg) {
            try {
                JSONObject d = new JSONObject();
                d.put("s", s);
                d.put("host", host);
                d.put("name", name);
                if (msg != null) d.put("msg", msg);
                emit("state", d);
            } catch (Exception ignored) {
            }
        }

        /** Só a conexão atual fala com a tela. */
        private void emit(String type, JSONObject d) {
            if (!closed && conn == this) listener.on(type, d);
        }
    }

    // --- pareamento: protocolo ---

    private static Proto head() {
        return new Proto().num(1, 2).num(2, 200);
    }

    private static void check(Proto.Msg m) throws Exception {
        if (m.num(2, 0) != 200) throw new Exception("status " + m.num(2, 0));
    }

    /** SHA-256(módulo e expoente do celular, depois os da TV, depois o código sem os 2 primeiros). */
    static byte[] secret(RSAPublicKey client, RSAPublicKey server, String code) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(unsigned(client.getModulus()));
        md.update(unsigned(client.getPublicExponent()));
        md.update(unsigned(server.getModulus()));
        md.update(unsigned(server.getPublicExponent()));
        md.update(hex(code.substring(2)));
        return md.digest();
    }

    private static byte[] unsigned(BigInteger n) {
        byte[] b = n.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            byte[] c = new byte[b.length - 1];
            System.arraycopy(b, 1, c, 0, c.length);
            return c;
        }
        return b;
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    // --- rede ---

    private SSLSocket open(Conn c, int port, String pin, X509Certificate[] seen) throws Exception {
        SSLSocket s = (SSLSocket) Cert.get(ctx).context(pin, seen).getSocketFactory().createSocket();
        try {
            s.connect(new InetSocketAddress(c.host, port), 6000);
            s.setSoTimeout(15000);
            s.startHandshake();
            return s;
        } catch (Exception e) {
            quiet(s);
            throw e;
        }
    }

    private static void quiet(SSLSocket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Exception ignored) {
        }
    }
}
