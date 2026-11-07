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

    /** Dedo na tela (DS), em pixels da GLSurfaceView. */
    static native void setPointer(float x, float y, boolean down);

    /** Tipo de controle do jogador (RETRO_DEVICE_*; no Wii, os do Dolphin). */
    static native void setDevice(int port, int device);

    static final int DEVICE_JOYPAD = 1, DEVICE_WII_NUNCHUK = (3 << 8) | 1, DEVICE_WII_CLASSIC = (4 << 8) | 1;

    /** O jogo desenha em 3D (sem rebobinar nem run-ahead). */
    static native boolean hardware();

    /* RetroAchievements (jni/cheevos.c) */

    /** Antes do load(): liga o RetroAchievements com a conta (usuário + token) e o console (rc_consoles.h). */
    static native void raStart(String user, String token, int console);

    /** O "rcheevos/x.y" do User-Agent. */
    static native String raAgent();

    /** Pedidos e avisos na fila, uma linha JSON por item (ou null). Qualquer thread. */
    static native String raPoll();

    /** Resposta de um pedido (status 0 = sem internet). Thread do GL. */
    static native void raResponse(int id, int status, byte[] body);

    /* Botões do RetroPad (libretro.h) */
    static final int B = 0, Y = 1, SELECT = 2, START = 3, UP = 4, DOWN = 5, LEFT = 6, RIGHT = 7,
            A = 8, X = 9, L = 10, R = 11, L2 = 12, R2 = 13, L3 = 14, R3 = 15;
    /** Botões C do N64 na tela: não existem no RetroPad, viram o analógico direito (GameActivity.push). */
    static final int C_UP = 16, C_DOWN = 17, C_LEFT = 18, C_RIGHT = 19;

    /* Idiomas do libretro: as opções dos núcleos vêm traduzidas quando o núcleo tem a tradução. */
    static final int LANG_PT_BR = 7, LANG_ES = 3;
}
