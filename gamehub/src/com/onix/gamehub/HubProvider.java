package com.onix.gamehub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

/**
 * O que a aba Jogos do Onix mostra: os últimos jogos do perfil ("Continuar jogando") e quantos
 * jogos há em cada console. Só apps assinados com a mesma chave (o Onix) podem ler
 * (permissão com.onix.gamehub.permission.HUB, nível signature, no manifesto).
 * Uso: call("summary", "<perfil>", {kids: bool}) -> Bundle {json}.
 */
public class HubProvider extends ContentProvider {

    static final String PERMISSION = "com.onix.gamehub.permission.HUB";
    private static final int THUMB_W = 240;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if (!"summary".equals(method) || getContext() == null) return out;
        // call() não passa pela permissão do provider no manifesto: confere aqui.
        if (getContext().checkCallingPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return out;
        try {
            String profile = Files.profile(arg);
            boolean kids = extras != null && extras.getBoolean("kids");
            JSONObject o = new JSONObject();
            o.put("version", getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName);
            JSONArray recent = new JSONArray();
            List<JSONObject> list = Library.recent(getContext(), profile, kids, 6);
            for (JSONObject g : list) {
                String id = g.optString("id");
                JSONObject r = new JSONObject();
                r.put("id", id);
                r.put("name", g.optString("name"));
                r.put("console", g.optString("console"));
                r.put("last", g.optLong("last"));
                r.put("resume", g.optBoolean("resume"));
                String img = thumb(new File(Files.covers(getContext()), id + ".jpg"));
                if (img != null) r.put("cover", img);
                String shot = thumb(new File(Files.states(getContext(), profile, id), "auto.jpg"));
                if (shot != null) r.put("shot", shot);
                recent.put(r);
            }
            o.put("recent", recent);
            JSONObject counts = new JSONObject();
            for (Consoles.Console c : Consoles.ALL) counts.put(c.id, 0);
            JSONArray all = Library.list(getContext(), profile, kids);
            for (int i = 0; i < all.length(); i++) {
                String c = all.getJSONObject(i).optString("console");
                counts.put(c, counts.optInt(c) + 1);
            }
            o.put("counts", counts);
            out.putString("json", o.toString());
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Imagem pequena (~240 px) em data URL para a página do Onix, que não lê arquivos. */
    private static String thumb(File f) {
        if (!f.isFile()) return null;
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), b);
        if (b.outWidth <= 0) return null;
        b.inJustDecodeBounds = false;
        b.inSampleSize = 1;
        while (b.outWidth / (b.inSampleSize * 2) >= THUMB_W) b.inSampleSize *= 2;
        Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), b);
        if (bmp == null) return null;
        if (bmp.getWidth() > THUMB_W) {
            Bitmap s = Bitmap.createScaledBitmap(bmp, THUMB_W, Math.max(1, bmp.getHeight() * THUMB_W / bmp.getWidth()), true);
            bmp.recycle();
            bmp = s;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, o);
        bmp.recycle();
        return "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
