package com.onix.gamehub;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pendrive, HD externo e cartão de memória na TV. Muitas TVs não têm gerenciador de arquivos, e no
 * Google TV o seletor de pastas do Android é uma casca vazia; então o GameHub mesmo lista as pastas
 * e lê os arquivos pelo caminho (/storage/<id>). Para isso o Android pede uma permissão, uma vez:
 * do 6 ao 10, ler o armazenamento (o 10 só com o requestLegacyExternalStorage do manifesto); do 11
 * em diante, o "acesso a todos os arquivos" (é o único jeito de ler arquivos que não são fotos,
 * vídeos ou músicas). No 5 ela já vem na instalação. Sem a tela dessa permissão na TV, o seletor de
 * pastas serve se for de verdade. Os jogos passam pelo mesmo FolderImport do celular: são copiados
 * para a TV e o pendrive pode sair depois. Só lê: nunca grava nem apaga nada no pendrive.
 * O formato (FAT32, exFAT, NTFS) sai do /proc/mounts, e um aparelho USB de armazenamento ligado sem
 * nenhum pendrive aberto vira o aviso de formato que a TV não lê (ou de HD sem energia).
 */
final class UsbDrives {

    static final int REQ_READ = 4;
    private static final String READ = "android.permission.READ_EXTERNAL_STORAGE";
    private static final int MAX_DEPTH = 4, MAX_FILES = 500, MAX_LIST = 400;
    /** Pastas do sistema que o Windows, o Mac e o Android criam no pendrive. */
    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList("system volume information", "$recycle.bin", "recycler",
            "lost.dir", "android", "found.000", "lost+found"));
    private static final Pattern CUE_FILE = Pattern.compile("^\\s*FILE\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private UsbDrives() {
    }

    /** Um armazenamento removível: caminho (null se o Android não conseguiu abrir), nome e estado. */
    private static final class Drive {
        String path, label, state, uuid;
    }

    private static List<Drive> drives(Context c) {
        List<Drive> out = new ArrayList<Drive>();
        Set<String> seen = new HashSet<String>();
        // Android 7+: a lista oficial, com o nome ("Pendrive SanDisk") e o estado de cada um.
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Object sm = c.getSystemService(Context.STORAGE_SERVICE);
                List<?> vols = (List<?>) sm.getClass().getMethod("getStorageVolumes").invoke(sm);
                for (Object v : vols) {
                    Class<?> k = v.getClass();
                    if ((Boolean) k.getMethod("isPrimary").invoke(v) || !(Boolean) k.getMethod("isRemovable").invoke(v)) continue;
                    Drive d = new Drive();
                    d.uuid = (String) k.getMethod("getUuid").invoke(v);
                    d.state = (String) k.getMethod("getState").invoke(v);
                    d.label = (String) k.getMethod("getDescription", Context.class).invoke(v, c);
                    File dir = null;
                    if (Build.VERSION.SDK_INT >= 30) dir = (File) k.getMethod("getDirectory").invoke(v);
                    if (dir == null && d.uuid != null) dir = new File("/storage/" + d.uuid);
                    boolean mounted = Environment.MEDIA_MOUNTED.equals(d.state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(d.state);
                    if (mounted && dir != null) {
                        d.path = dir.getPath();
                        seen.add(d.path);
                    }
                    out.add(d);
                }
            } catch (Exception ignored) {
            }
        }
        // Android 5 e 6, e TVs que montam o pendrive fora da lista oficial.
        List<File> extra = new ArrayList<File>();
        for (String base : new String[]{"/storage", "/mnt/usb"}) {
            File[] l = new File(base).listFiles();
            if (l != null) extra.addAll(Arrays.asList(l));
        }
        try {
            File[] ext = c.getExternalFilesDirs(null);
            for (int i = 1; ext != null && i < ext.length; i++) {
                if (ext[i] == null) continue;
                String p = ext[i].getPath();
                int a = p.indexOf("/Android/");
                if (a > 0) extra.add(new File(p.substring(0, a)));
            }
        } catch (Exception ignored) {
        }
        String primary = canonical(Environment.getExternalStorageDirectory());
        for (File f : extra) {
            String n = f.getName(), p = f.getPath();
            if (n.equals("emulated") || n.equals("self") || n.equals("sdcard") || n.equals("sdcard0") || n.equals("enc_emulated")) continue;
            if (seen.contains(p) || !f.isDirectory() || canonical(f).equals(primary)) continue;
            seen.add(p);
            Drive d = new Drive();
            d.path = p;
            d.label = n;
            d.uuid = n;
            d.state = Environment.MEDIA_MOUNTED;
            out.add(d);
        }
        return out;
    }

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (Exception e) {
            return f.getAbsolutePath();
        }
    }

    /** O sistema de arquivos do pendrive, pelo /proc/mounts (no Android 11+, o de /mnt/media_rw/<id>). */
    private static String fsType(String path, String uuid) {
        String name = new File(path).getName(), best = null;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(" ");
                if (f.length < 3) continue;
                String mnt = f[1].replace("\\040", " "), type = f[2];
                if (!mnt.equals(path) && !mnt.equals("/mnt/media_rw/" + name) && (uuid == null || !mnt.endsWith("/" + uuid))) continue;
                boolean layer = type.equals("fuse") || type.equals("sdcardfs") || type.equals("esdfs");   // camada do Android por cima
                if (!layer) best = type;
                else if (best == null) best = "";
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {
            }
        }
        return best == null ? "" : best;
    }

    private static String fsName(String type) {
        switch (type) {
            case "vfat":
            case "msdos":
                return "FAT32";
            case "exfat":
            case "texfat":
                return "exFAT";
            case "sdfat":
                return "FAT32/exFAT";
            case "ntfs":
            case "ntfs3":
            case "tntfs":
            case "ufsd":
                return "NTFS";
            case "ext4":
            case "ext3":
            case "ext2":
                return "ext4";
            case "hfsplus":
                return "HFS+";
            default:
                return "";
        }
    }

    /** Aparelhos USB de armazenamento ligados (pendrive, HD, leitor de cartão), abertos ou não. */
    private static int usbStorage(Context c) {
        int n = 0;
        try {
            UsbManager um = (UsbManager) c.getSystemService(Context.USB_SERVICE);
            for (UsbDevice d : um.getDeviceList().values()) {
                for (int i = 0; i < d.getInterfaceCount(); i++) {
                    if (d.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        n++;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    static boolean granted(Context c) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return (Boolean) Environment.class.getMethod("isExternalStorageManager").invoke(null);
            } catch (Exception e) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= 23) return c.checkSelfPermission(READ) == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    /** O seletor de pastas do Android existe de verdade (no Google TV é uma casca que não abre nada). */
    static boolean realPicker(Context c) {
        try {
            ResolveInfo r = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 0);
            if (r == null || r.activityInfo == null) return false;
            String who = (r.activityInfo.packageName + "/" + r.activityInfo.name).toLowerCase(Locale.ROOT);
            return !who.contains("stub") && !who.equals("android/com.android.internal.app.resolveractivity");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Pede a permissão: "screen" (abriu a tela do acesso a todos os arquivos), "dialog" (a pergunta
     * do Android 6 a 10), "details" (só a tela do app abriu), "picker" (abriu o seletor de pastas)
     * ou "none" (esta TV não tem nenhum dos caminhos: só pelo celular). Roda na thread da tela.
     */
    static String ask(HubActivity a) {
        Uri pkg = Uri.parse("package:" + a.getPackageName());
        if (Build.VERSION.SDK_INT >= 30) {
            if (start(a, new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", pkg))) return "screen";
            if (start(a, new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"))) return "screen";
        } else if (Build.VERSION.SDK_INT >= 23) {
            // Já disse "não perguntar de novo": a pergunta não aparece mais, só a tela do app.
            boolean asked = a.prefs.getBoolean("usbAsked", false);
            if (!asked || a.shouldShowRequestPermissionRationale(READ)) {
                a.prefs.edit().putBoolean("usbAsked", true).apply();
                a.requestPermissions(new String[]{READ}, REQ_READ);
                return "dialog";
            }
        }
        if (realPicker(a) && a.pick(true)) return "picker";
        if (start(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))) return "details";
        return "none";
    }

    private static boolean start(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Para a tela: {permission, sdk, picker, usb, unread, drives:[{path, name, fs, total, free, read}], bad:[nome]}. */
    static JSONObject state(Context c) {
        JSONObject o = new JSONObject();
        try {
            boolean ok = granted(c);
            JSONArray list = new JSONArray(), bad = new JSONArray();
            for (Drive d : drives(c)) {
                String name = d.label == null || d.label.trim().isEmpty() ? "" : d.label.trim();
                if (d.path == null) {
                    // O Android viu o pendrive, mas não abriu: formato que a TV não lê, vazio ou com defeito.
                    if (d.state != null && !Environment.MEDIA_REMOVED.equals(d.state) && !Environment.MEDIA_BAD_REMOVAL.equals(d.state)
                            && !Environment.MEDIA_EJECTING.equals(d.state) && !Environment.MEDIA_UNMOUNTED.equals(d.state)) bad.put(name);
                    continue;
                }
                JSONObject j = new JSONObject().put("path", d.path).put("name", name).put("fs", fsName(fsType(d.path, d.uuid)));
                try {
                    StatFs s = new StatFs(d.path);
                    j.put("total", s.getTotalBytes()).put("free", s.getAvailableBytes());
                } catch (Exception ignored) {
                }
                j.put("read", ok && new File(d.path).listFiles() != null);
                list.put(j);
            }
            int usb = usbStorage(c);
            o.put("permission", ok).put("sdk", Build.VERSION.SDK_INT).put("picker", realPicker(c)).put("usb", usb)
                    .put("unread", Math.max(0, usb - list.length())).put("drives", list).put("bad", bad);
        } catch (Exception ignored) {
        }
        return o;
    }

    /** A pasta, se estiver dentro de um pendrive aberto (a página não lê outros lugares da TV). */
    private static File inside(Context c, String path, Drive[] driveOut) {
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        String cp = canonical(f);
        for (Drive d : drives(c)) {
            if (d.path == null) continue;
            String root = canonical(new File(d.path));
            if (cp.equals(root) || cp.startsWith(root + "/")) {
                if (driveOut != null) driveOut[0] = d;
                return f;
            }
        }
        return null;
    }

    private static boolean hidden(File f) {
        String n = f.getName();
        return n.startsWith(".") || (f.isDirectory() && SKIP.contains(n.toLowerCase(Locale.ROOT)));
    }

    private static File[] sorted(File dir) {
        File[] l = dir.listFiles();
        if (l == null) return null;
        Arrays.sort(l, new Comparator<File>() {
            @Override
            public int compare(File x, File y) {
                return x.getName().compareToIgnoreCase(y.getName());
            }
        });
        return l;
    }

    /** O que tem numa pasta do pendrive: {path, root, parent, dirs:[nome], files:[{name, size, console}], more} ou {error}. */
    static JSONObject list(Context c, String path) {
        JSONObject o = new JSONObject();
        try {
            Drive[] drive = new Drive[1];
            File dir = inside(c, path, drive);
            if (dir == null) return o.put("error", "gone");
            if (!granted(c)) return o.put("error", "permission");
            File[] l = sorted(dir);
            if (l == null) return o.put("error", "read");
            // Os .bin de um disco de PS1 vêm junto com o .cue dele: não aparecem soltos.
            Set<String> tracks = new HashSet<String>();
            for (File f : l) if (f.isFile() && "cue".equals(Consoles.extension(f.getName()))) tracks.addAll(cueTracks(f));
            JSONArray dirs = new JSONArray(), files = new JSONArray();
            int n = 0;
            for (File f : l) {
                if (hidden(f)) continue;
                if (n++ >= MAX_LIST) {
                    o.put("more", true);
                    break;
                }
                if (f.isDirectory()) {
                    // As pastas vazias que o Android cria no pendrive (Alarms, DCIM, Music…) não aparecem.
                    File[] in = f.listFiles();
                    boolean any = false;
                    if (in != null) for (File x : in) if (!hidden(x)) { any = true; break; }
                    if (any) dirs.put(f.getName());
                } else if (FolderImport.supported(f.getName()) && !tracks.contains(f.getName().toLowerCase(Locale.ROOT))) {
                    String ext = Consoles.extension(f.getName());
                    Consoles.Console con = Consoles.of(f, f.getName());
                    files.put(new JSONObject().put("name", f.getName()).put("size", f.length()).put("console", "zip".equals(ext) || con == null ? "zip" : con.id));
                }
            }
            File root = new File(drive[0].path);
            o.put("path", dir.getPath()).put("root", root.getPath()).put("dirs", dirs).put("files", files);
            if (!canonical(dir).equals(canonical(root))) o.put("parent", dir.getParent());
        } catch (Exception ignored) {
        }
        return o;
    }

    /** Os arquivos que um .cue usa (FILE "jogo (Track 1).bin" BINARY), em minúsculas. */
    private static List<String> cueTracks(File cue) {
        List<String> out = new ArrayList<String>();
        if (cue.length() > 64 * 1024) return out;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(cue));
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = CUE_FILE.matcher(line);
                if (m.find()) out.add(new File(m.group(1).replace('\\', '/')).getName().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /**
     * Põe na biblioteca as pastas (com as subpastas) e os arquivos escolhidos, sem copiar: o jogo
     * continua no pendrive (Importer.link). Mesmos eventos da cópia (scan, start, done, finished).
     */
    static void importPaths(final Context c, final String json, final UploadServer.Host host) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Map<String, File> files = new LinkedHashMap<String, File>();
                Map<String, Drive> owner = new LinkedHashMap<String, Drive>();
                try {
                    JSONArray a = new JSONArray(json);
                    for (int i = 0; i < a.length(); i++) {
                        Drive[] d = new Drive[1];
                        File f = inside(c, a.optString(i), d);
                        if (f == null) continue;
                        Map<String, File> found = new LinkedHashMap<String, File>();
                        if (f.isDirectory()) walk(f, 0, found);
                        else if (f.isFile() && FolderImport.supported(f.getName())) found.put(f.getPath(), f);
                        for (String k : found.keySet()) owner.put(k, d[0]);
                        files.putAll(found);
                    }
                } catch (Exception ignored) {
                }
                emit(host, ev("scan").put("count", files.size()));
                for (Map.Entry<String, File> e : files.entrySet()) {
                    File f = e.getValue();
                    Drive d = owner.get(e.getKey());
                    String uri = Uri.fromFile(f).toString();
                    emit(host, ev("start").put("name", f.getName()).put("file", uri).put("size", f.length()));
                    JSONObject r = Importer.link(c, f, d == null || d.uuid == null ? "" : d.uuid, d == null || d.label == null ? "" : d.label.trim());
                    try {
                        r.put("type", "done").put("file", uri);
                        if (!r.has("name")) r.put("name", f.getName());
                    } catch (Exception ignored) {
                    }
                    emit(host, r);
                }
                emit(host, ev("finished").put("count", files.size()));
            }
        }, "gamehub-usb").start();
    }

    private static JSONObjectB ev(String type) {
        return new JSONObjectB().put("type", type);
    }

    private static void emit(UploadServer.Host host, Object o) {
        host.event(o instanceof JSONObjectB ? ((JSONObjectB) o).o : (JSONObject) o);
    }

    /** JSONObject.put sem o try/catch em cada linha. */
    private static final class JSONObjectB {
        final JSONObject o = new JSONObject();

        JSONObjectB put(String k, Object v) {
            try {
                o.put(k, v);
            } catch (Exception ignored) {
            }
            return this;
        }
    }

    /** Os jogos de uma pasta e das subpastas; os .bin de um .cue vêm com ele (não entram soltos). */
    private static void walk(File dir, int depth, Map<String, File> out) {
        if (depth > MAX_DEPTH || out.size() >= MAX_FILES) return;
        File[] l = sorted(dir);
        if (l == null) return;
        Set<String> tracks = new HashSet<String>();
        for (File f : l) if (f.isFile() && "cue".equals(Consoles.extension(f.getName()))) tracks.addAll(cueTracks(f));
        for (File f : l) {
            if (out.size() >= MAX_FILES) return;
            if (hidden(f)) continue;
            if (f.isDirectory()) walk(f, depth + 1, out);
            else if (FolderImport.supported(f.getName()) && !tracks.contains(f.getName().toLowerCase(Locale.ROOT))) out.put(f.getPath(), f);
        }
    }

}
