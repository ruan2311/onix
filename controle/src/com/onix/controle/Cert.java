package com.onix.controle;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.io.ByteArrayInputStream;
import java.util.Locale;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Identidade deste celular para as TVs: uma chave RSA e um certificado próprio (sem biblioteca,
 * montado à mão em DER), criados uma vez e guardados só na pasta privada do app. A TV guarda esse
 * certificado ao parear; ele é o que prova que é este celular.
 */
final class Cert {

    private static Cert instance;

    final PrivateKey key;
    final X509Certificate cert;

    static synchronized Cert get(Context ctx) throws Exception {
        if (instance == null) instance = load(ctx.getFilesDir());
        return instance;
    }

    private Cert(PrivateKey key, X509Certificate cert) {
        this.key = key;
        this.cert = cert;
    }

    private static Cert load(File dir) throws Exception {
        File k = new File(dir, "client.key"), c = new File(dir, "client.crt");
        if (k.isFile() && c.isFile()) {
            try {
                PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readAll(k)));
                return new Cert(key, x509(readAll(c)));
            } catch (Exception ignored) {
                // arquivo estragado: cria outra identidade (as TVs pedem um novo pareamento)
            }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048, new SecureRandom());
        KeyPair kp = g.generateKeyPair();
        byte[] der = selfSigned(kp);
        writeAll(k, kp.getPrivate().getEncoded());
        writeAll(c, der);
        return new Cert(kp.getPrivate(), x509(der));
    }

    /**
     * Contexto TLS com o certificado deste celular. A TV usa um certificado próprio, sem autoridade:
     * no pareamento ele é aceito e guardado (pin); depois, só aquela TV é aceita.
     */
    SSLContext context(final String pin, final X509Certificate[] seen) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        char[] pw = "onix".toCharArray();
        ks.setKeyEntry("me", key, pw, new Certificate[]{cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pw);
        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                if (chain == null || chain.length == 0) throw new java.security.cert.CertificateException("TV sem certificado");
                if (seen != null) seen[0] = chain[0];
                if (pin != null && !pin.equals(fingerprint(chain[0]))) {
                    throw new java.security.cert.CertificateException("PIN");
                }
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), new TrustManager[]{tm}, new SecureRandom());
        return ssl;
    }

    static String fingerprint(Certificate c) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // --- certificado X.509 (v1) autoassinado, em DER ---

    private static byte[] selfSigned(KeyPair kp) throws Exception {
        byte[] sha256Rsa = seq(oid(new int[]{1, 2, 840, 113549, 1, 1, 11}), new byte[]{0x05, 0x00});
        byte[] name = seq(set(seq(oid(new int[]{2, 5, 4, 3}), tlv(0x0C, "Onix Controle".getBytes("UTF-8")))));
        byte[] serial = tlv(0x02, new BigInteger(63, new SecureRandom()).add(BigInteger.ONE).toByteArray());
        byte[] validity = seq(tlv(0x17, "260101000000Z".getBytes("US-ASCII")), tlv(0x17, "491231235959Z".getBytes("US-ASCII")));
        byte[] tbs = seq(serial, sha256Rsa, name, validity, name, kp.getPublic().getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        return seq(tbs, sha256Rsa, tlv(0x03, bits));
    }

    private static byte[] seq(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    private static byte[] oid(int[] arcs) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int v = arcs[i];
            byte[] tmp = new byte[5];
            int n = 0;
            do {
                tmp[n++] = (byte) (v & 0x7F);
                v >>>= 7;
            } while (v != 0);
            for (int j = n - 1; j >= 0; j--) o.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(0x06, o.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] v) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(v.length + 6);
        o.write(tag);
        int n = v.length;
        if (n < 0x80) {
            o.write(n);
        } else {
            int bytes = n > 0xFFFF ? 3 : n > 0xFF ? 2 : 1;
            o.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) o.write(n >>> (8 * i));
        }
        o.write(v, 0, v.length);
        return o.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static X509Certificate x509(byte[] der) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void writeAll(File f, byte[] b) throws Exception {
        FileOutputStream o = new FileOutputStream(f);
        try {
            o.write(b);
        } finally {
            o.close();
        }
    }
}
