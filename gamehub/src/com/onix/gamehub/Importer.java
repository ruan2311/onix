package com.onix.gamehub;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Recebe um arquivo enviado pelo celular e põe na biblioteca:
 * .zip -> tira a ROM de dentro; descobre o console pela extensão; identifica o jogo pelo CRC
 * no banco No-Intro (assets/db, sem internet); confere o espaço; guarda em roms/<id>.<ext>.
 * O id é o começo do SHA-1 do conteúdo, então o mesmo jogo enviado duas vezes não duplica.
 */
final class Importer {

    private static final Map<String, Map<String, String>> DB = new HashMap<String, Map<String, String>>();
    private static final Pattern TAGS = Pattern.compile("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]");
    private static final Pattern ARTICLE = Pattern.compile("^(.+?), (The|A|An|Les|Le|La|Die|Der|Das|El|Los|Las|O|Os)( - .*)?$");

    private Importer() {
    }

    /** Resultado para o celular e a TV: {ok, name, console, dup, error}. */
    static JSONObject add(Context c, File upload, String fileName) {
        JSONObject r = new JSONObject();
        File rom = null;
        try {
            String name = fileName;
            if ("zip".equals(Consoles.extension(name))) {
                rom = new File(upload.getPath() + ".rom");
                name = unzip(upload, rom);
                if (name == null) return fail(r, "O .zip não tem nenhum jogo de NES, SNES, GBA ou Mega Drive.");
            } else {
                rom = upload;
            }
            Consoles.Console console = Consoles.byExtension(name);
            if (console == null) return fail(r, "Esse tipo de arquivo não é de NES, SNES, GBA ou Mega Drive.");
            long size = rom.length();
            if (size < 512 || size > console.maxBytes) return fail(r, "O arquivo não parece um jogo desse console.");

            byte[] head = new byte[16];
            InputStream in = new FileInputStream(rom);
            int got;
            try {
                got = in.read(head);
            } finally {
                in.close();
            }
            int skip = 0;
            if ("nes".equals(console.id) && got >= 4 && head[0] == 'N' && head[1] == 'E' && head[2] == 'S' && head[3] == 0x1A) skip = 16;
            if ("snes".equals(console.id) && size % 1024 == 512) skip = 512;
            String[] sums = sums(rom, skip);
            String id = sums[0].substring(0, 16);

            r.put("console", console.id);
            JSONObject existing = Library.game(c, id);
            if (existing != null) {
                r.put("ok", true).put("dup", true).put("name", existing.optString("name")).put("id", id);
                return r;
            }
            if (!Storage.fits(c, size)) return fail(r, "Sem espaço seguro na TV para esse jogo.");

            String title = lookup(c, console.id, sums[1]);
            if (title == null && skip > 0) title = lookup(c, console.id, sums[2]);
            boolean known = title != null;
            if (title == null) {
                String base = new File(name).getName();
                int dot = base.lastIndexOf('.');
                title = dot > 0 ? base.substring(0, dot) : base;
            }
            String ext = Consoles.extension(name);
            File dest = new File(Files.roms(c), id + "." + ext);
            if (!move(rom, dest)) return fail(r, "Não deu para guardar o jogo na TV.");
            rom = null;

            JSONObject game = new JSONObject();
            game.put("id", id);
            game.put("console", console.id);
            game.put("name", displayName(title));
            game.put("title", title);
            game.put("known", known);
            game.put("file", dest.getName());
            game.put("size", size);
            game.put("crc", sums[1]);
            game.put("added", System.currentTimeMillis());
            game.put("cover", false);
            game.put("kids", false);
            Library.add(c, game);
            Covers.fetchLater(c, id);
            r.put("ok", true).put("name", game.getString("name")).put("id", id);
            return r;
        } catch (Exception e) {
            return fail(r, "Não deu para ler esse arquivo.");
        } finally {
            if (rom != null && rom != upload) rom.delete();
            upload.delete();
        }
    }

    private static JSONObject fail(JSONObject r, String msg) {
        try {
            r.put("ok", false).put("error", msg);
        } catch (Exception ignored) {
        }
        return r;
    }

    /** Tira do .zip a maior ROM de um dos 4 consoles. Retorna o nome dela ou null. */
    private static String unzip(File zip, File out) throws Exception {
        ZipFile z = new ZipFile(zip);
        try {
            ZipEntry best = null;
            Enumeration<? extends ZipEntry> list = z.entries();
            while (list.hasMoreElements()) {
                ZipEntry e = list.nextElement();
                if (e.isDirectory() || e.getName().contains("..")) continue;
                Consoles.Console c = Consoles.byExtension(e.getName());
                if (c == null) continue;
                if (best == null || e.getSize() > best.getSize()) best = e;
            }
            if (best == null) return null;
            Consoles.Console c = Consoles.byExtension(best.getName());
            InputStream in = z.getInputStream(best);
            OutputStream o = new FileOutputStream(out);
            try {
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > c.maxBytes) throw new Exception("grande demais");
                    o.write(buf, 0, n);
                }
            } finally {
                o.close();
                in.close();
            }
            return new File(best.getName()).getName();
        } finally {
            z.close();
        }
    }

    /** SHA-1 de tudo, CRC32 de tudo e CRC32 sem o cabeçalho (o banco tem os dois jeitos). */
    private static String[] sums(File f, int skip) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        CRC32 all = new CRC32(), body = new CRC32();
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            long pos = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                sha.update(buf, 0, n);
                all.update(buf, 0, n);
                int from = (int) Math.max(0, Math.min(n, skip - pos));
                if (from < n) body.update(buf, from, n - from);
                pos += n;
            }
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : sha.digest()) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return new String[]{sb.toString(), String.format(Locale.ROOT, "%08x", all.getValue()), String.format(Locale.ROOT, "%08x", body.getValue())};
    }

    private static synchronized String lookup(Context c, String console, String crc) {
        Map<String, String> db = DB.get(console);
        if (db == null) {
            db = new HashMap<String, String>();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("db/" + console + ".tsv"), "UTF-8"));
                try {
                    String line;
                    while ((line = r.readLine()) != null) {
                        int tab = line.indexOf('\t');
                        if (tab == 8) db.put(line.substring(0, 8), line.substring(9));
                    }
                } finally {
                    r.close();
                }
            } catch (Exception ignored) {
            }
            DB.put(console, db);
        }
        return db.get(crc);
    }

    /** "Legend of Zelda, The - A Link to the Past (USA) (Rev 1)" -> "The Legend of Zelda - A Link to the Past" */
    static String displayName(String title) {
        String s = TAGS.matcher(title).replaceAll("").replace('_', ' ').trim();
        Matcher m = ARTICLE.matcher(s);
        if (m.matches()) s = m.group(2) + " " + m.group(1) + (m.group(3) != null ? m.group(3) : "");
        s = s.replaceAll("\\s{2,}", " ").trim();
        if (s.isEmpty()) s = title.trim();
        return s.length() > 80 ? s.substring(0, 80).trim() : s;
    }

    private static boolean move(File from, File to) {
        if (from.renameTo(to)) return true;
        try {
            InputStream in = new FileInputStream(from);
            OutputStream o = new FileOutputStream(to);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            } finally {
                o.close();
                in.close();
            }
            from.delete();
            return true;
        } catch (Exception e) {
            to.delete();
            return false;
        }
    }
}
