package com.onix.gamehub;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * Onde cada coisa fica, sempre na pasta interna do app (filesDir), nunca no cache: o Android
 * mantém essa pasta nas atualizações e só a apaga ao desinstalar.
 *
 *   roms/<id>.<ext>                       jogos
 *   covers/<id>.jpg                       capas baixadas
 *   library.json                          biblioteca (nome, console, capa, Modo Infantil…)
 *   config.json                           configurações (global, por console, por jogo)
 *   cheats/<id>.json                      códigos de trapaça
 *   profiles/<perfil>/played.json         última vez, tempo jogado e favoritos
 *   profiles/<perfil>/saves/<id>.srm      memória do cartucho
 *   profiles/<perfil>/states/<id>/<n>.*   estados salvos (auto, 1–4) e miniaturas
 *   system/                               pasta de sistema dos núcleos (sem BIOS)
 */
final class Files {

    static final Pattern ID = Pattern.compile("^[0-9a-f]{16}$");
    static final Pattern PROFILE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    static final Pattern SLOT = Pattern.compile("^(auto|[1-4])$");

    private Files() {
    }

    static File dir(Context c, String name) {
        File d = new File(c.getFilesDir(), name);
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static File roms(Context c) {
        return dir(c, "roms");
    }

    static File covers(Context c) {
        return dir(c, "covers");
    }

    static File system(Context c) {
        return dir(c, "system");
    }

    static File cheats(Context c, String id) {
        return new File(dir(c, "cheats"), id + ".json");
    }

    static String profile(String p) {
        return p != null && PROFILE.matcher(p).matches() ? p : "default";
    }

    static File profileDir(Context c, String profile) {
        return dir(c, "profiles/" + profile(profile));
    }

    static File saves(Context c, String profile) {
        File d = new File(profileDir(c, profile), "saves");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static File states(Context c, String profile, String id) {
        File d = new File(profileDir(c, profile), "states/" + id);
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static String read(File f) {
        try {
            InputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                return o.toString("UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Grava num .tmp e renomeia: um desligamento no meio nunca deixa o arquivo pela metade. */
    static boolean write(File f, byte[] data) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try {
                o.write(data);
                o.getFD().sync();
            } finally {
                o.close();
            }
            return tmp.renameTo(f);
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
    }

    static boolean write(File f, String text) {
        try {
            return write(f, text.getBytes("UTF-8"));
        } catch (Exception e) {
            return false;
        }
    }

    static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] list = f.listFiles();
        if (list != null) for (File x : list) total += size(x);
        return total;
    }

    static void delete(File f) {
        if (f == null || !f.exists()) return;
        File[] list = f.isDirectory() ? f.listFiles() : null;
        if (list != null) for (File x : list) delete(x);
        f.delete();
    }
}
