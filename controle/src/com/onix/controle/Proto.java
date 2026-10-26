package com.onix.controle;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Protobuf mínimo para o protocolo de controle da Google TV: só varint e bytes, que é tudo o que as
 * mensagens usam. Cada mensagem vai na rede com o tamanho (varint) na frente.
 */
final class Proto {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    Proto num(int field, long v) {
        varint(out, (long) field << 3);
        varint(out, v);
        return this;
    }

    Proto bytes(int field, byte[] b) {
        varint(out, (long) field << 3 | 2);
        varint(out, b.length);
        out.write(b, 0, b.length);
        return this;
    }

    Proto str(int field, String s) {
        try {
            return bytes(field, s.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Proto msg(int field, Proto p) {
        return bytes(field, p.build());
    }

    byte[] build() {
        return out.toByteArray();
    }

    private static void varint(ByteArrayOutputStream o, long v) {
        while ((v & ~0x7FL) != 0) {
            o.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write((int) v);
    }

    static void write(OutputStream o, byte[] msg) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(msg.length + 4);
        varint(b, msg.length);
        b.write(msg, 0, msg.length);
        o.write(b.toByteArray());
        o.flush();
    }

    /** Lê uma mensagem; a TV nunca manda nada grande (64 KB já é folga). */
    static byte[] read(InputStream in) throws IOException {
        long n = 0;
        for (int shift = 0; ; shift += 7) {
            int b = in.read();
            if (b < 0) throw new EOFException("a TV fechou a conexão");
            n |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            if (shift > 28) throw new IOException("mensagem inválida");
        }
        if (n > 64 * 1024) throw new IOException("mensagem grande demais");
        byte[] buf = new byte[(int) n];
        int got = 0;
        while (got < n) {
            int r = in.read(buf, got, (int) n - got);
            if (r < 0) throw new EOFException("a TV fechou a conexão");
            got += r;
        }
        return buf;
    }

    /** Mensagem lida: campo -> valores (Long para números, byte[] para o resto). */
    static final class Msg {
        final Map<Integer, List<Object>> f = new HashMap<Integer, List<Object>>();

        boolean has(int field) {
            return f.containsKey(field);
        }

        long num(int field, long def) {
            List<Object> l = f.get(field);
            return l != null && l.get(0) instanceof Long ? (Long) l.get(0) : def;
        }

        byte[] bytes(int field) {
            List<Object> l = f.get(field);
            return l != null && l.get(0) instanceof byte[] ? (byte[]) l.get(0) : null;
        }

        Msg msg(int field) {
            byte[] b = bytes(field);
            return b == null ? null : parse(b);
        }

        String str(int field) {
            byte[] b = bytes(field);
            try {
                return b == null ? "" : new String(b, "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }
    }

    static Msg parse(byte[] b) {
        Msg m = new Msg();
        int i = 0;
        try {
            while (i < b.length) {
                long[] r = readVarint(b, i);
                long tag = r[0];
                i = (int) r[1];
                int field = (int) (tag >>> 3), type = (int) (tag & 7);
                Object v;
                if (type == 0) {
                    r = readVarint(b, i);
                    v = r[0];
                    i = (int) r[1];
                } else if (type == 2) {
                    r = readVarint(b, i);
                    int len = (int) r[0];
                    i = (int) r[1];
                    if (len < 0 || i + len > b.length) break;
                    byte[] sub = new byte[len];
                    System.arraycopy(b, i, sub, 0, len);
                    v = sub;
                    i += len;
                } else if (type == 5) {
                    i += 4;
                    continue;
                } else if (type == 1) {
                    i += 8;
                    continue;
                } else {
                    break;
                }
                List<Object> l = m.f.get(field);
                if (l == null) m.f.put(field, l = new ArrayList<Object>());
                l.add(v);
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // mensagem cortada: fica com o que deu para ler
        }
        return m;
    }

    private static long[] readVarint(byte[] b, int i) {
        long n = 0;
        for (int shift = 0; ; shift += 7) {
            int x = b[i++] & 0xFF;
            n |= (long) (x & 0x7F) << shift;
            if ((x & 0x80) == 0) return new long[]{n, i};
            if (shift > 56) throw new ArrayIndexOutOfBoundsException();
        }
    }
}
