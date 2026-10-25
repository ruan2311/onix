package com.onix.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Quantas vezes cada serviço de streaming foi aberto pelo Onix (pela grade ou pelas indicações).
 * A partir da 4ª abertura o serviço entra nas indicações da semana; se ele ficar mais de 7 dias
 * sem ser aberto, a contagem zera e as indicações voltam ao padrão (Netflix).
 * Fica só na TV, numa SharedPreferences própria: nada disso sai do aparelho.
 */
final class AppUsage {

    /** "Mais de 3 vezes": a 4ª abertura ativa o serviço. */
    static final int MIN_OPENS = 4;
    static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

    private AppUsage() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("usage", Context.MODE_PRIVATE);
    }

    /** Soma uma abertura; uma semana parado (ou relógio que voltou no tempo) recomeça do zero. */
    static synchronized void record(Context ctx, String provider) {
        if (provider == null) return;
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        int n = p.getInt(provider + ".n", 0);
        long last = p.getLong(provider + ".t", 0);
        if (now < last || now - last > EXPIRE_MS) n = 0;
        p.edit().putInt(provider + ".n", n + 1).putLong(provider + ".t", now).apply();
    }

    /** Aberturas que ainda valem (0 se o serviço passou mais de uma semana sem ser aberto). */
    static synchronized int opens(Context ctx, String provider) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis(), last = p.getLong(provider + ".t", 0);
        if (now < last || now - last > EXPIRE_MS) return 0;
        return p.getInt(provider + ".n", 0);
    }

    static boolean active(Context ctx, String provider) {
        return opens(ctx, provider) >= MIN_OPENS;
    }
}
