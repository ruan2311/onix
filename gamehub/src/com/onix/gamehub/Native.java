package com.onix.gamehub;

/**
 * Frontend libretro em C (jni/retro.c). Tudo que fala com o núcleo roda na thread do
 * GLSurfaceView (queueEvent); só os botões podem vir de qualquer thread.
 */
final class Native {
    static {
        System.loadLibrary("onixretro");
    }

    private Native() {
    }

    static native String probe(String core, int language);

    static native void setOption(String key, String value);

    static native void clearOptions();

    static native boolean load(String core, String rom, String systemDir, String saveDir, int language);

    static native void unload();

    static native double fps();

    static native void startAudio(int rate, int burst, int latencyMs);

    static native void setVolume(float v);

    static native void setMuted(boolean m);

    static native void setPaused(boolean p);

    static native void setButtons(int port, int bits);

    static native void setAnalog(int port, int stick, int x, int y);

    static native void setHotkeys(boolean fastForward, boolean rewind);

    static native void configure(int filter, int scale, int aspect, int ffSpeed, int runAhead, boolean rewind, int rewindMb, double displayHz);

    static native void setFastForward(boolean on);

    static native boolean fastForward();

    static native void surfaceCreated();

    static native void surfaceChanged(int w, int h);

    static native void frame();

    static native void reset();

    static native boolean saveState(String path);

    static native boolean loadState(String path);

    static native boolean loadSram(String path);

    static native boolean saveSram(String path);

    static native void cheatReset();

    static native void cheatSet(int index, boolean on, String code);

    static native String options();

    static native int snapshot(int[] argb);

    static native void setTurbo(int mask, int period);

    /* Botões do RetroPad (libretro.h) */
    static final int B = 0, Y = 1, SELECT = 2, START = 3, UP = 4, DOWN = 5, LEFT = 6, RIGHT = 7,
            A = 8, X = 9, L = 10, R = 11, L2 = 12, R2 = 13, L3 = 14, R3 = 15;

    /* Idiomas do libretro: as opções dos núcleos vêm traduzidas quando o núcleo tem a tradução. */
    static final int LANG_PT_BR = 7, LANG_ES = 3;
}
