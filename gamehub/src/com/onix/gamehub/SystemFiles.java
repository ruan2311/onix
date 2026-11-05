package com.onix.gamehub;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Arquivos de sistema dos núcleos 3D (fontes do PSP, a pasta Sys do Dolphin) e a BIOS do PS2.
 * Os de sistema são os mesmos do PC: vêm da release motor-retroarch-1 do Cobalt GameHub na primeira
 * vez que o console abre, conferidos pelo SHA-256, e ficam em Files.system (system/PPSSPP,
 * system/dolphin-emu). A BIOS do PS2 é do console da própria pessoa: chega pelo Adicionar jogos.
 */
final class SystemFiles {

    private static final String BASE = "https://github.com/ruan2311/cobalt-gamehub/releases/download/motor-retroarch-1/";
    private static final long MAX = 64L * 1024 * 1024;

    private SystemFiles() {
    }

    private static String sha256(String zip) {
        if ("system-PPSSPP.zip".equals(zip)) return "3dadc50ba0a0827c14b49f7171d9b084197e350b0a1d7923a32de396932176be";
        if ("system-Dolphin.zip".equals(zip)) return "1506a58e65ff5c20c6c714d2355ae5900ee35705a57de764b94852c420a9b6ce";
        return null;
    }

    static boolean ready(Context c, String zip) {
        return zip == null || new File(Files.system(c), "." + zip).isFile();
    }

    /** Baixa e abre o zip em Files.system. Demora: chamar fora da thread da tela. */
    static boolean ensure(Context c, String zip) {
        if (ready(c, zip)) return true;
        String want = sha256(zip);
        if (want == null) return false;
        File sys = Files.system(c), tmp = new File(c.getCacheDir(), zip + ".part");
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(BASE + zip).openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            if (h.getResponseCode() != 200) return false;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream in = h.getInputStream();
            OutputStream out = new FileOutputStream(tmp);
            long total = 0;
            try {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX) return false;
                    md.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
                in.close();
            }
            if (!hex(md.digest()).equals(want)) return false;
            unzip(tmp, sys);
            Files.write(new File(sys, "." + zip), "motor-retroarch-1");
            return true;
        } catch (Exception e) {
            android.util.Log.w("OnixRetro", "arquivos de sistema " + zip, e);
            return false;
        } finally {
            tmp.delete();
        }
    }

    private static void unzip(File zip, File dest) throws Exception {
        String base = dest.getCanonicalPath() + File.separator;
        ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(zip));
        try {
            byte[] buf = new byte[65536];
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                File out = new File(dest, e.getName());
                if (!out.getCanonicalPath().startsWith(base)) continue;   // nada fora da pasta
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                OutputStream o = new FileOutputStream(out);
                try {
                    for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
                } finally {
                    o.close();
                }
            }
        } finally {
            in.close();
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xFF));
        return s.toString();
    }

    /* ---------------- BIOS do PS2 ---------------- */

    static File ps2BiosDir(Context c) {
        return new File(Files.system(c), "pcsx2/bios");
    }

    static boolean hasPs2Bios(Context c) {
        File[] l = ps2BiosDir(c).listFiles();
        if (l != null) for (File f : l) if (f.isFile() && f.length() >= 1024 * 1024) return true;
        return false;
    }

    /** Uma BIOS de PS2 tem 4 MB ou mais e a lista ROMDIR (RESET, ROMDIR, ROMVER) logo no começo. */
    static boolean isPs2Bios(File f) {
        long n = f.length();
        if (n < 2 * 1024 * 1024 || n > 16 * 1024 * 1024) return false;
        try {
            InputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[65536];
            int got = 0;
            try {
                for (int k; got < b.length && (k = in.read(b, got, b.length - got)) > 0; ) got += k;
            } finally {
                in.close();
            }
            String s = new String(b, 0, got, "ISO-8859-1");
            return s.contains("RESET") && s.contains("ROMDIR") && s.contains("ROMVER");
        } catch (Exception e) {
            return false;
        }
    }
}
