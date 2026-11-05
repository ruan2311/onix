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
 * Fliperama: o .zip com o nome do romset fica como veio em roms/arcade (o FBNeo acha o jogo e o
 * principal das variações pelo nome). PS1 em .cue: o .cue e os .bin dele chegam um por um e
 * esperam em ps1-pending até o disco ficar completo, que vai para roms/<id>/.
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
            String ext0 = Consoles.extension(name);
            if ("zip".equals(ext0) && arcadeInfo(c, romset(name)) != null) return arcade(c, upload, name, r);
            if ("cue".equals(ext0)) return cue(c, upload, name, r);
            if ("bin".equals(ext0) && Consoles.byId("ps2") != null && SystemFiles.isPs2Bios(upload)) {
                // BIOS do PS2 (do console da pessoa): vai para system/pcsx2/bios, sem virar jogo.
                File dir = SystemFiles.ps2BiosDir(c);
                dir.mkdirs();
                String safe = new File(name.replace('\\', '/')).getName();
                if (!move(upload, new File(dir, safe))) return fail(r, "Não deu para guardar o jogo.");
                return r.put("ok", true).put("console", "ps2").put("bios", true).put("name", "BIOS do PlayStation 2");
            }
            if ("bin".equals(ext0)) {
                JSONObject d = disc(c, upload, name, r);
                if (d != null) return d;
            }
            if ("zip".equals(ext0)) {
                rom = new File(upload.getPath() + ".rom");
                name = unzip(upload, rom);
                if (name == null) return fail(r, "O .zip não tem nenhum jogo que o GameHub conheça. No fliperama, o nome do .zip tem que ser o do jogo, como sf2.zip.");
            } else {
                rom = upload;
            }
            Consoles.Console console = Consoles.of(rom, name);
            if (console == null) return fail(r, "Esse tipo de arquivo não é de nenhum console do GameHub.");
            long size = rom.length();
            if (size < 512 || size > console.maxBytes) return fail(r, "O arquivo não parece um jogo desse console.");

            String[] info = identify(c, console, rom, name);
            String id = info[0];

            r.put("console", console.id);
            JSONObject existing = Library.game(c, id);
            if (existing != null) {
                r.put("ok", true).put("dup", true).put("name", existing.optString("name")).put("id", id);
                return r;
            }
            if (!Storage.fits(c, size)) return fail(r, "Sem espaço seguro no aparelho para esse jogo.");

            String title = info[2];
            boolean known = !info[3].isEmpty();
            String ext = Consoles.extension(name);
            File dest = new File(Files.roms(c), id + "." + ext);
            if (!move(rom, dest)) return fail(r, "Não deu para guardar o jogo.");
            rom = null;

            JSONObject game = new JSONObject();
            game.put("id", id);
            game.put("console", console.id);
            game.put("name", displayName(title));
            game.put("title", title);
            game.put("known", known);
            game.put("file", dest.getName());
            game.put("size", size);
            game.put("crc", info[1]);
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

    /** {id, crc, título, "1" se o jogo está no banco No-Intro}: o mesmo para a cópia e para o pendrive. */
    private static String[] identify(Context c, Consoles.Console console, File rom, String name) throws Exception {
        long size = rom.length();
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
        if ("atari".equals(console.id) && got >= 10 && new String(head, 1, 9, "ISO-8859-1").equals("ATARI7800")) skip = 128;
        // Discos grandes: o id sai do começo, do fim e do tamanho (ler 700 MB demora na TV).
        String[] sums = size > 64L * 1024 * 1024 ? quickSums(rom) : sums(rom, skip);
        String title = lookup(c, console.id, sums[1]);
        if (title == null && skip > 0) title = lookup(c, console.id, sums[2]);
        boolean known = title != null;
        if (title == null) {
            String base = new File(name).getName();
            int dot = base.lastIndexOf('.');
            title = dot > 0 ? base.substring(0, dot) : base;
        }
        return new String[]{sums[0].substring(0, 16), sums[1], title, known ? "1" : ""};
    }

    /* ---------------- Pendrive ----------------
     * O jogo fica no pendrive: a biblioteca guarda o caminho ("path") e o pendrive ("drive",
     * "driveName"), e o GameActivity abre o arquivo de lá. Na TV ficam só o nome, a capa e os saves.
     * O id é o mesmo da cópia, então o mesmo jogo não entra duas vezes. Nada é gravado no pendrive:
     * o .zip de NES, SNES… abre numa cópia temporária na hora de jogar.
     */
    static JSONObject link(Context c, File f, String drive, String driveName) {
        JSONObject r = new JSONObject();
        File tmp = null;
        try {
            String name = f.getName(), ext0 = Consoles.extension(name);
            JSONObject game;
            if ("zip".equals(ext0) && arcadeInfo(c, romset(name)) != null) {
                String set = romset(name), info = arcadeInfo(c, set);
                r.put("console", "arcade");
                // A BIOS (neogeo.zip…) fica na pasta do pendrive, ao lado dos jogos que precisam dela.
                if (info.endsWith("\tbios")) return r.put("ok", true).put("bios", true).put("name", info.substring(0, info.length() - 5));
                String id = sha1("arcade:" + set).substring(0, 16);
                if (linked(c, id, f, drive, driveName, r)) return r;
                game = newGame(id, "arcade", info, true, "arcade/" + set + ".zip", f.length(), "");
            } else if ("cue".equals(ext0)) {
                r.put("console", "ps1");
                java.util.List<String> need = cueFiles(f);
                if (need.isEmpty()) return fail(r, "Esse .cue não diz quais arquivos .bin são do disco.");
                long size = 0;
                for (String n : need) {
                    File b = findIgnoreCase(f.getParentFile(), n);
                    if (b == null) return fail(r, "Faltam os .bin desse disco na pasta do .cue.");
                    size += b.length();
                }
                String id = sha1Hex(read(f)).substring(0, 16);
                if (linked(c, id, f, drive, driveName, r)) return r;
                game = newGame(id, "ps1", name.substring(0, name.length() - 4), false, name, size, "");
            } else {
                String inner = name;
                File rom = f;
                if ("zip".equals(ext0)) {
                    tmp = new File(c.getCacheDir(), "link-" + System.nanoTime());
                    inner = unzip(f, tmp);
                    if (inner == null) return fail(r, "O .zip não tem nenhum jogo que o GameHub conheça. No fliperama, o nome do .zip tem que ser o do jogo, como sf2.zip.");
                    rom = tmp;
                }
                Consoles.Console console = Consoles.of(rom, inner);
                if (console == null) return fail(r, "Esse tipo de arquivo não é de nenhum console do GameHub.");
                r.put("console", console.id);
                if ("bin".equals(Consoles.extension(inner)) && rom.length() > Consoles.MD_MAX) {
                    return fail(r.put("console", "ps1"), "Disco de PlayStation em .bin precisa do .cue dele na mesma pasta.");
                }
                long size = rom.length();
                if (size < 512 || size > console.maxBytes) return fail(r, "O arquivo não parece um jogo desse console.");
                String[] info = identify(c, console, rom, inner);
                if (linked(c, info[0], f, drive, driveName, r)) return r;
                game = newGame(info[0], console.id, info[2], !info[3].isEmpty(), inner, size, info[1]);
                if (tmp != null) game.put("zip", true);
            }
            game.put("path", f.getAbsolutePath()).put("drive", drive).put("driveName", driveName);
            Library.add(c, game);
            Covers.fetchLater(c, game.getString("id"));
            return r.put("ok", true).put("name", game.getString("name")).put("id", game.getString("id"));
        } catch (Exception e) {
            return fail(r, "Não deu para ler esse arquivo.");
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    /** Já está na biblioteca: se for do pendrive e mudou de lugar, o caminho novo vale. */
    private static boolean linked(Context c, String id, File f, String drive, String driveName, JSONObject r) throws Exception {
        JSONObject existing = Library.game(c, id);
        if (existing == null) return false;
        if (existing.has("path") && !f.getAbsolutePath().equals(existing.optString("path"))) {
            Library.update(c, id, new JSONObject().put("path", f.getAbsolutePath()).put("drive", drive).put("driveName", driveName));
        }
        r.put("ok", true).put("dup", true).put("name", existing.optString("name")).put("id", id);
        return true;
    }

    /* ---------------- Fliperama ---------------- */

    /** "Street Fighter II (World 910522).zip" não; "sf2.zip" -> "sf2". */
    private static String romset(String fileName) {
        String b = new File(fileName).getName().toLowerCase(Locale.ROOT);
        return b.endsWith(".zip") ? b.substring(0, b.length() - 4) : b;
    }

    /** Nome do jogo no banco do FBNeo (assets/db/arcade.tsv: romset, nome e "bios" nas BIOS). */
    private static String arcadeInfo(Context c, String set) {
        return set.isEmpty() ? null : lookup(c, "arcade", set);
    }

    /** BIOS do fliperama (neogeo.zip…): fica na pasta mesmo sem jogo na biblioteca. */
    static boolean isArcadeBios(Context c, String fileName) {
        String info = arcadeInfo(c, romset(fileName));
        return info != null && info.endsWith("\tbios");
    }

    private static JSONObject arcade(Context c, File upload, String fileName, JSONObject r) throws Exception {
        String set = romset(fileName);
        String info = arcadeInfo(c, set);
        boolean bios = info.endsWith("\tbios");
        String title = bios ? info.substring(0, info.length() - 5) : info;
        File dir = new File(Files.roms(c), "arcade");
        if (!dir.isDirectory()) dir.mkdirs();
        File dest = new File(dir, set + ".zip");
        String id = sha1("arcade:" + set).substring(0, 16);
        r.put("console", "arcade");
        JSONObject existing = Library.game(c, id);
        if (existing != null && dest.isFile()) {
            r.put("ok", true).put("dup", true).put("name", existing.optString("name")).put("id", id);
            return r;
        }
        long size = upload.length();
        if (!Storage.fits(c, size)) return fail(r, "Sem espaço seguro no aparelho para esse jogo.");
        dest.delete();
        if (!move(upload, dest)) return fail(r, "Não deu para guardar o jogo.");
        // A BIOS (neogeo.zip, pgm.zip…) só fica na pasta, para os jogos que precisam dela.
        if (bios) return r.put("ok", true).put("bios", true).put("name", title);
        JSONObject game = newGame(id, "arcade", title, true, "arcade/" + dest.getName(), size, "");
        Library.add(c, game);
        Covers.fetchLater(c, id);
        return r.put("ok", true).put("name", game.getString("name")).put("id", id);
    }

    /* ---------------- PS1 em .cue + .bin ---------------- */

    private static final Pattern CUE_FILE = Pattern.compile("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))");
    private static final long DAY = 24L * 60 * 60 * 1000;

    /** Os .bin que o .cue pede (só o nome do arquivo, como vieram do PC). */
    private static java.util.List<String> cueFiles(File cue) throws Exception {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (cue.length() > 64 * 1024) return out;
        Matcher m = CUE_FILE.matcher(new String(read(cue), "ISO-8859-1"));
        while (m.find()) {
            String n = m.group(1) != null ? m.group(1) : m.group(2);
            n = new File(n.replace('\\', '/')).getName();
            if (!n.isEmpty() && !out.contains(n)) out.add(n);
        }
        return out;
    }

    private static JSONObject cue(Context c, File upload, String fileName, JSONObject r) throws Exception {
        r.put("console", "ps1");
        java.util.List<String> need = cueFiles(upload);
        if (need.isEmpty()) return fail(r, "Esse .cue não diz quais arquivos .bin são do disco.");
        String id = sha1Hex(read(upload)).substring(0, 16);
        JSONObject existing = Library.game(c, id);
        if (existing != null) return r.put("ok", true).put("dup", true).put("name", existing.optString("name")).put("id", id);
        cleanPending(c);
        File dir = new File(Files.dir(c, "ps1-pending"), id);
        if (!dir.isDirectory()) dir.mkdirs();
        String name = new File(fileName).getName();
        move(upload, new File(dir, name));
        // Os .bin que chegaram antes do .cue esperam soltos.
        File loose = Files.dir(c, "ps1-loose");
        for (String n : need) {
            File f = findIgnoreCase(loose, n);
            if (f != null) move(f, new File(dir, n));
        }
        return complete(c, dir, r);
    }

    /** Um .bin: entra no disco que o espera; os grandes sem .cue ainda esperam soltos. Null = Mega Drive. */
    private static JSONObject disc(Context c, File upload, String fileName, JSONObject r) throws Exception {
        String name = new File(fileName).getName();
        String done = discFileOf(c, name, upload.length());
        if (done != null) {
            JSONObject g = Library.game(c, done);
            return r.put("console", "ps1").put("ok", true).put("dup", true).put("name", g != null ? g.optString("name") : name).put("id", done);
        }
        File[] dirs = Files.dir(c, "ps1-pending").listFiles();
        if (dirs != null) for (File dir : dirs) {
            File cue = cueIn(dir);
            if (cue == null) continue;
            for (String n : cueFiles(cue)) {
                if (!n.equalsIgnoreCase(name) || new File(dir, n).isFile()) continue;
                r.put("console", "ps1");
                if (!Storage.fits(c, upload.length())) return fail(r, "Sem espaço seguro no aparelho para esse jogo.");
                move(upload, new File(dir, n));
                return complete(c, dir, r);
            }
        }
        if (upload.length() <= Consoles.MD_MAX) return null;
        r.put("console", "ps1");
        if (!Storage.fits(c, upload.length())) return fail(r, "Sem espaço seguro no aparelho para esse jogo.");
        cleanPending(c);
        move(upload, new File(Files.dir(c, "ps1-loose"), name));
        return r.put("ok", true).put("wait", true).put("name", name);
    }

    /** Com todos os .bin, o disco vai para roms/<id>/ e entra na biblioteca. */
    private static JSONObject complete(Context c, File dir, JSONObject r) throws Exception {
        File cue = cueIn(dir);
        java.util.List<String> need = cueFiles(cue);
        int missing = 0;
        long size = 0;
        for (String n : need) {
            File f = new File(dir, n);
            if (f.isFile()) size += f.length();
            else missing++;
        }
        String title = cue.getName().substring(0, cue.getName().length() - 4);
        if (missing > 0) return r.put("ok", true).put("wait", true).put("missing", missing).put("name", displayName(title));
        String id = dir.getName();
        File dest = new File(Files.roms(c), id);
        Files.delete(dest);
        if (!dir.renameTo(dest)) return fail(r, "Não deu para guardar o jogo.");
        JSONObject game = newGame(id, "ps1", title, false, id + "/" + cue.getName(), size, "");
        Library.add(c, game);
        Covers.fetchLater(c, id);
        return r.put("ok", true).put("name", game.getString("name")).put("id", id);
    }

    /** O id do disco já guardado que tem esse .bin (mesmo nome e tamanho), ou null. */
    static String discFileOf(Context c, String name, long size) {
        File[] dirs = Files.roms(c).listFiles();
        if (dirs != null) for (File d : dirs) {
            if (!d.isDirectory() || d.getName().equals("arcade")) continue;
            File f = findIgnoreCase(d, name);
            if (f != null && f.length() == size) return d.getName();
        }
        return null;
    }

    private static File cueIn(File dir) {
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) if ("cue".equals(Consoles.extension(f.getName()))) return f;
        return null;
    }

    private static File findIgnoreCase(File dir, String name) {
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) if (f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }

    /** Disco que ficou pela metade por mais de um dia sai, para não ocupar espaço. */
    private static void cleanPending(Context c) {
        long old = System.currentTimeMillis() - DAY;
        for (String d : new String[]{"ps1-pending", "ps1-loose"}) {
            File[] fs = Files.dir(c, d).listFiles();
            if (fs != null) for (File f : fs) if (f.lastModified() < old) Files.delete(f);
        }
    }

    private static JSONObject newGame(String id, String console, String title, boolean known, String file, long size, String crc) throws Exception {
        JSONObject game = new JSONObject();
        game.put("id", id);
        game.put("console", console);
        game.put("name", displayName(title));
        game.put("title", title);
        game.put("known", known);
        game.put("file", file);
        game.put("size", size);
        game.put("crc", crc);
        game.put("added", System.currentTimeMillis());
        game.put("cover", false);
        game.put("kids", false);
        return game;
    }

    private static byte[] read(File f) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        } finally {
            in.close();
        }
        return b.toByteArray();
    }

    private static String sha1(String s) throws Exception {
        return sha1Hex(s.getBytes("UTF-8"));
    }

    private static String sha1Hex(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-1").digest(data)) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }

    /** SHA-1 dos primeiros e dos últimos 4 MB e do tamanho (o CRC não serve: o banco é por faixa). */
    private static String[] quickSums(File f) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
        try {
            long len = raf.length(), part = 4L * 1024 * 1024;
            byte[] buf = new byte[(int) part];
            raf.readFully(buf, 0, (int) Math.min(part, len));
            sha.update(buf, 0, (int) Math.min(part, len));
            if (len > part) {
                raf.seek(Math.max(part, len - part));
                int n = (int) (len - Math.max(part, len - part));
                raf.readFully(buf, 0, n);
                sha.update(buf, 0, n);
            }
            sha.update(Long.toString(len).getBytes("UTF-8"));
        } finally {
            raf.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : sha.digest()) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return new String[]{sb.toString(), "", ""};
    }

    private static JSONObject fail(JSONObject r, String msg) {
        try {
            r.put("ok", false).put("error", msg);
        } catch (Exception ignored) {
        }
        return r;
    }

    /** Tira do .zip a maior ROM de um dos 4 consoles. Retorna o nome dela ou null. */
    static String unzip(File zip, File out) throws Exception {
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
                        if (tab > 0) db.put(line.substring(0, tab), line.substring(tab + 1));
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

    static boolean move(File from, File to) {
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
