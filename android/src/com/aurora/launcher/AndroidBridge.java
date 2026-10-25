package com.aurora.launcher;

import android.app.Activity;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ponte exposta ao JavaScript como "AndroidBridge".
 * Tudo que chega do JS é validado aqui.
 */
public class AndroidBridge {

    private static final Pattern PKG = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$");
    private static final int MAX_APPS = 300;
    private static final int MAX_PNG_BYTES = 380 * 1024;   // ~506 KB em base64 (limite do JS: 512 KB)
    // Tamanho real do tile na tela (~220 px na UI 1080p da TV): maior que isso só gasta RAM e CPU.
    private static final int BANNER_W = 256, BANNER_H = 144, ICON_PX = 128;

    private final Activity activity;
    private final WeeklyPicks picks;
    private final GameNews news;
    private volatile boolean trusted;
    private volatile String cache;
    // Ícone já convertido de cada app; só é redesenhado quando o app é atualizado.
    private final Map<String, Art> art = new HashMap<String, Art>();

    private static final class Art {
        final String stamp, icon;
        final boolean banner;

        Art(String stamp, String icon, boolean banner) {
            this.stamp = stamp;
            this.icon = icon;
            this.banner = banner;
        }
    }

    AndroidBridge(Activity activity) {
        this.activity = activity;
        this.picks = WeeklyPicks.get(activity);
        this.news = GameNews.get(activity);
    }

    void setTrusted(boolean value) {
        trusted = value;
    }

    /** Monta a lista numa thread de fundo enquanto o WebView carrega a página. */
    void preload() {
        background(false, null);
    }

    /**
     * Refaz a lista em segundo plano (apps instalados/removidos) e só então chama done na UI thread.
     * Enquanto isso, a página continua recebendo a lista anterior na hora, sem esperar.
     */
    void refresh(Runnable done) {
        background(true, done);
    }

    private void background(final boolean force, final Runnable done) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                build(force);
                if (done != null) activity.runOnUiThread(done);
            }
        }, "onix-apps");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    @JavascriptInterface
    public String getInstalledApps() {
        if (!trusted) return "[]";
        String c = cache;
        return c != null ? c : build(false);
    }

    private synchronized String build(boolean force) {
        if (!force && cache != null) return cache;

        PackageManager pm = activity.getPackageManager();
        Map<String, ResolveInfo> apps = new LinkedHashMap<String, ResolveInfo>();
        Map<String, ResolveInfo> system = new LinkedHashMap<String, ResolveInfo>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, apps, system);
        collect(pm, Intent.CATEGORY_LAUNCHER, apps, system);
        // Apps do usuário primeiro; os de sistema vão para o final (e são os cortados se passar de MAX_APPS).
        for (Map.Entry<String, ResolveInfo> e : system.entrySet()) {
            if (!apps.containsKey(e.getKey())) apps.put(e.getKey(), e.getValue());
        }

        JSONArray out = new JSONArray();
        HashSet<String> seen = new HashSet<String>();
        for (Map.Entry<String, ResolveInfo> e : apps.entrySet()) {
            if (out.length() >= MAX_APPS) break;
            try {
                ResolveInfo ri = e.getValue();
                JSONObject o = new JSONObject();
                o.put("package", e.getKey());
                o.put("label", String.valueOf(ri.loadLabel(pm)));
                if (system.containsKey(e.getKey())) o.put("system", true);

                Art a = artFor(pm, e.getKey(), ri);
                if (a.banner) o.put("banner", true);
                if (a.icon != null) o.put("icon", a.icon);
                out.put(o);
                seen.add(e.getKey());
            } catch (Exception ignored) {
                // App com metadados inválidos: simplesmente fica de fora.
            }
        }
        art.keySet().retainAll(seen);   // apps removidos liberam o ícone
        cache = out.toString();
        return cache;
    }

    private Art artFor(PackageManager pm, String pkg, ResolveInfo ri) {
        long updated = 0;
        try {
            updated = pm.getPackageInfo(pkg, 0).lastUpdateTime;
        } catch (Exception ignored) {
        }
        String stamp = ri.activityInfo.name + "@" + updated;
        Art a = art.get(pkg);
        if (a != null && a.stamp.equals(stamp)) return a;

        Drawable banner = loadBanner(pm, ri.activityInfo);
        String icon = banner != null ? toDataUrl(banner, BANNER_W, BANNER_H) : null;
        boolean isBanner = icon != null;
        if (icon == null) icon = toDataUrl(ri.loadIcon(pm), ICON_PX, ICON_PX);
        a = new Art(stamp, icon, isBanner);
        art.put(pkg, a);
        return a;
    }

    @JavascriptInterface
    public String getWeeklyPicks() {
        if (!trusted) return "[]";
        return picks.json();
    }

    @JavascriptInterface
    public String getGameNews() {
        if (!trusted) return "[]";
        return news.json();
    }

    private static final String[] NETFLIX_PKGS = {"com.netflix.ninja", "com.netflix.mediaclient"};

    /**
     * Abre uma indicação: se veio da Netflix, abre o app direto no título; se o app não aceitar
     * o link, abre só a Netflix; sem Netflix (ou indicação de outra fonte), cai na busca da TV.
     * O ID nunca vem do JS: é lido da seleção baixada pelo próprio app.
     * Retorna "title", "app", "search" ou "" (falhou).
     */
    @JavascriptInterface
    public String openPick(String title) {
        if (!trusted || title == null || title.length() > 120 || !picks.hasTitle(title)) return "";
        String id = picks.netflixId(title);
        if (id != null) {
            PackageManager pm = activity.getPackageManager();
            for (String pkg : NETFLIX_PKGS) {
                if (pm.getLaunchIntentForPackage(pkg) == null && pm.getLeanbackLaunchIntentForPackage(pkg) == null) continue;
                // https://www.netflix.com/title/<id> abre direto na página do título no app da TV
                // (testado na Philips); netflix://title só abre a Netflix, então fica como reserva.
                String[] uris = {"https://www.netflix.com/title/" + id, "netflix://title/" + id};
                for (String u : uris) {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage(pkg);
                    i.putExtra("source", "30");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    if (i.resolveActivity(pm) != null && start(i)) return "title";
                }
                if (launchApp(pkg)) return "app";
            }
        }
        return searchTitle(title) ? "search" : "";
    }

    private boolean start(final Intent i) {
        try {
            activity.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Abre a busca da Google TV ("onde assistir"); só para títulos da seleção atual. */
    @JavascriptInterface
    public boolean searchTitle(String title) {
        if (!trusted || title == null || title.length() > 120 || !picks.hasTitle(title)) return false;
        PackageManager pm = activity.getPackageManager();
        Intent[] tries = {
                new Intent("android.search.action.GLOBAL_SEARCH"),
                new Intent(Intent.ACTION_WEB_SEARCH),
        };
        for (Intent i : tries) {
            i.putExtra(SearchManager.QUERY, title);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(pm) == null) continue;
            final Intent launch = i;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        activity.startActivity(launch);
                    } catch (Exception ignored) {
                    }
                }
            });
            return true;
        }
        return false;
    }

    @JavascriptInterface
    public boolean launchApp(String pkg) {
        if (!trusted || pkg == null || pkg.length() > 150 || !PKG.matcher(pkg).matches()) return false;
        if (pkg.equals(activity.getPackageName())) return false;

        // Só abre a Activity de entrada oficial do app (nunca uma Intent arbitrária vinda do JS).
        PackageManager pm = activity.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        final Intent launch = i;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    activity.startActivity(launch);
                } catch (Exception ignored) {
                }
            }
        });
        return true;
    }

    /** Portas HDMI físicas da TV (sem os aparelhos HDMI-CEC, que são entradas "filhas"), em ordem de número. */
    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<TvInputInfo>();
        TvInputManager tim = (TvInputManager) activity.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null) out.add(info);
        }
        final Map<String, String> labels = new HashMap<String, String>();
        for (TvInputInfo info : out) labels.put(info.getId(), String.valueOf(info.loadLabel(activity)));
        Collections.sort(out, new Comparator<TvInputInfo>() {
            @Override
            public int compare(TvInputInfo a, TvInputInfo b) {
                int c = portNumber(labels.get(a.getId())) - portNumber(labels.get(b.getId()));
                return c != 0 ? c : a.getId().compareTo(b.getId());
            }
        });
        return out;
    }

    /** "HDMI 2" -> 2; sem número vai para o fim. */
    private static int portNumber(String label) {
        java.util.regex.Matcher m = Pattern.compile("(\\d+)").matcher(label == null ? "" : label);
        return m.find() ? Math.min(Integer.parseInt(m.group(1)), 99) : 100;
    }

    @JavascriptInterface
    public String getTvInputs() {
        if (!trusted) return "[]";
        JSONArray out = new JSONArray();
        try {
            for (TvInputInfo info : hdmiInputs()) {
                JSONObject o = new JSONObject();
                o.put("id", info.getId());
                o.put("label", String.valueOf(info.loadLabel(activity)));
                out.put(o);
            }
        } catch (Exception ignored) {
            // TV sem serviço de entradas: a página mostra as portas padrão.
        }
        return out.toString();
    }

    /** Muda para a entrada pelo app de TV do sistema; só aceita IDs que a própria TV listou. */
    @JavascriptInterface
    public boolean openInput(String id) {
        if (!trusted || id == null || id.length() > 200) return false;
        try {
            boolean known = false;
            for (TvInputInfo info : hdmiInputs()) known |= info.getId().equals(id);
            if (!known) return false;
        } catch (Exception e) {
            return false;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(i);
    }

    /** Abre as configurações do sistema; se a TV não aceitar a ação padrão, abre o app de configurações da TV. */
    @JavascriptInterface
    public boolean openSettings() {
        if (!trusted) return false;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (start(i)) return;
                PackageManager pm = activity.getPackageManager();
                Intent tv = pm.getLeanbackLaunchIntentForPackage("com.android.tv.settings");
                if (tv == null) tv = pm.getLaunchIntentForPackage("com.android.tv.settings");
                if (tv != null) start(tv.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        return true;
    }

    /** Abre os ajustes de imagem (Tela/Imagem do Android TV); sem eles, cai nas configurações gerais. */
    @JavascriptInterface
    public boolean openPictureSettings() {
        if (!trusted) return false;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (start(new Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return;
                openSettings();
            }
        });
        return true;
    }

    /** Botão "Atualizar" da tela inicial: baixa tudo agora; a página recebe Launcher.onRefreshed no fim. */
    @JavascriptInterface
    public boolean refreshAll() {
        if (!trusted || !(activity instanceof MainActivity)) return false;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((MainActivity) activity).forceRefresh();
            }
        });
        return true;
    }

    /**
     * Põe a TV em standby. "ok" = desligando; "ask" = abriu o pedido de permissão de administrador
     * (só na primeira vez); "fail" = a TV não deixou.
     */
    @JavascriptInterface
    public String powerOff() {
        if (!trusted) return "fail";
        // Alguns aparelhos (TV boxes, Chromecast) não têm administrador de dispositivo: "fail" e a página explica.
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) return "fail";
        final DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        final ComponentName admin = new ComponentName(activity, PowerAdmin.class);
        if (dpm == null) return "fail";
        boolean active;
        try { active = dpm.isAdminActive(admin); } catch (Exception e) { return "fail"; }
        if (active) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try { dpm.lockNow(); } catch (Exception ignored) { }
                }
            });
            return "ok";
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Permite que o botão Desligar TV do Onix coloque a TV em standby.");
        return start(i) ? "ask" : "fail";
    }

    private void collect(PackageManager pm, String category, Map<String, ResolveInfo> user, Map<String, ResolveInfo> system) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        String self = activity.getPackageName();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg == null || pkg.equals(self) || user.containsKey(pkg) || system.containsKey(pkg) || !PKG.matcher(pkg).matches()) continue;
            (isUserApp(ri.activityInfo.applicationInfo) ? user : system).put(pkg, ri);
        }
    }

    /** Só apps instalados pelo usuário: nem de sistema, nem de sistema atualizados pela Play Store. */
    private static boolean isUserApp(ApplicationInfo ai) {
        if (ai == null) return false;
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
    }

    private static Drawable loadBanner(PackageManager pm, ActivityInfo ai) {
        try {
            Drawable d = pm.getActivityBanner(new ComponentName(ai.packageName, ai.name));
            if (d == null) d = pm.getApplicationBanner(ai.applicationInfo);
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private static String toDataUrl(Drawable d, int maxW, int maxH) {
        if (d == null) return null;
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            w = maxW;
            h = maxH;
        }
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        int bw = Math.max(1, Math.round(w * scale)), bh = Math.max(1, Math.round(h * scale));

        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, bw, bh);
        d.draw(c);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, png);
        bmp.recycle();
        if (png.size() > MAX_PNG_BYTES) return null;
        return "data:image/png;base64," + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
    }
}
