package com.onix.gamehub;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Celular: os jogos vêm de uma pasta (ou de arquivos) escolhida no seletor do Android, no lugar
 * do envio pelo Wi-Fi da TV. Procura nas subpastas também (até 4 níveis), copia cada jogo para
 * um arquivo temporário e passa pelo mesmo Importer do envio pelo celular, com os mesmos
 * eventos "upload" (start/progress/done) para a tela.
 */
final class FolderImport {

    private static final int MAX_DEPTH = 4, MAX_FILES = 500;
    private static final long MAX_SIZE = 64L * 1024 * 1024;

    private FolderImport() {
    }

    /** Pasta (árvore do seletor). Roda numa thread própria. */
    static void tree(final Context ctx, final Uri tree, final UploadServer.Host host) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Object[]> files = new ArrayList<Object[]>();
                try {
                    String root = DocumentsContract.getTreeDocumentId(tree);
                    walk(ctx.getContentResolver(), tree, root, 0, files);
                } catch (Exception ignored) {
                }
                importAll(ctx, files, host);
            }
        }, "gamehub-folder").start();
    }

    /** Arquivos soltos (seleção múltipla). */
    static void files(final Context ctx, final List<Uri> uris, final UploadServer.Host host) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Object[]> files = new ArrayList<Object[]>();
                for (Uri u : uris) {
                    String name = null;
                    long size = -1;
                    Cursor c = null;
                    try {
                        c = ctx.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
                        if (c != null && c.moveToFirst()) {
                            name = c.getString(0);
                            size = c.isNull(1) ? -1 : c.getLong(1);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (c != null) c.close();
                    }
                    if (name != null) files.add(new Object[]{u, name, size});
                }
                importAll(ctx, files, host);
            }
        }, "gamehub-files").start();
    }

    private static void walk(ContentResolver cr, Uri tree, String doc, int depth, List<Object[]> out) {
        if (depth > MAX_DEPTH || out.size() >= MAX_FILES) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc);
        Cursor c = null;
        try {
            c = cr.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE}, null, null, null);
            while (c != null && c.moveToNext() && out.size() < MAX_FILES) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(cr, tree, id, depth + 1, out);
                } else if (name != null && supported(name)) {
                    out.add(new Object[]{DocumentsContract.buildDocumentUriUsingTree(tree, id), name, c.isNull(3) ? -1L : c.getLong(3)});
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    private static boolean supported(String name) {
        return "zip".equals(Consoles.extension(name)) || Consoles.byExtension(name) != null;
    }

    private static void importAll(Context ctx, List<Object[]> files, UploadServer.Host host) {
        emit(host, new JSONObjectBuilder().put("type", "scan").put("count", files.size()).o);
        for (Object[] f : files) {
            Uri uri = (Uri) f[0];
            String name = (String) f[1];
            long size = (Long) f[2];
            emit(host, new JSONObjectBuilder().put("type", "start").put("name", name).put("file", uri.toString()).put("size", Math.max(size, 0)).o);
            JSONObject r;
            File tmp = new File(ctx.getCacheDir(), "import-" + System.nanoTime());
            try {
                if (!supported(name)) {
                    r = new JSONObject().put("ok", false).put("error", "Esse tipo de arquivo não é de NES, SNES, GBA ou Mega Drive.");
                } else if (size > MAX_SIZE) {
                    r = new JSONObject().put("ok", false).put("error", "Arquivo grande demais.");
                } else if (!copy(ctx, uri, tmp)) {
                    r = new JSONObject().put("ok", false).put("error", "Não deu para ler esse arquivo.");
                } else {
                    r = Importer.add(ctx, tmp, name);
                }
            } catch (Exception e) {
                r = new JSONObject();
            } finally {
                tmp.delete();
                new File(tmp.getPath() + ".rom").delete();
            }
            try {
                r.put("type", "done").put("file", uri.toString());
                if (!r.has("name")) r.put("name", name);
            } catch (Exception ignored) {
            }
            emit(host, r);
        }
        emit(host, new JSONObjectBuilder().put("type", "finished").put("count", files.size()).o);
    }

    private static boolean copy(Context ctx, Uri uri, File to) {
        try {
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_SIZE) return false;
                    out.write(buf, 0, n);
                }
                return true;
            } finally {
                in.close();
                out.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void emit(UploadServer.Host host, JSONObject o) {
        if (o != null) host.event(o);
    }

    /** JSONObject.put sem o try/catch em cada linha. */
    private static final class JSONObjectBuilder {
        final JSONObject o = new JSONObject();

        JSONObjectBuilder put(String k, Object v) {
            try {
                o.put(k, v);
            } catch (Exception ignored) {
            }
            return this;
        }
    }
}
