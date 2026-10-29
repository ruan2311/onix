package com.onix.gamehub;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * TV ou celular. O mesmo app roda nos dois: na TV, telas deitadas, navegação pelo controle e
 * jogos enviados pelo celular; no celular, telas em pé, toque, controle na tela e jogos tirados
 * de uma pasta do próprio aparelho.
 */
final class Device {

    private static Boolean mobile;

    private Device() {
    }

    static boolean mobile(Context c) {
        if (mobile == null) {
            PackageManager pm = c.getPackageManager();
            boolean tv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature("android.software.leanback_only");
            mobile = (!tv && pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) || forced();
        }
        return mobile;
    }

    /** Teste numa TV: adb shell setprop debug.onix.mobile 1 (vale até a TV reiniciar). */
    private static boolean forced() {
        return prop("debug.onix.mobile");
    }

    /** Teste: controle na tela mesmo com um controle conectado (setprop debug.onix.pad 1). */
    static boolean padAlways() {
        return prop("debug.onix.pad");
    }

    private static boolean prop(String name) {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, name);
            return "1".equals(v);
        } catch (Exception e) {
            return false;
        }
    }
}
