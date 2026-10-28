package com.onix.gamehub;

import java.util.Locale;

/** Os 4 consoles do GameHub: núcleo, extensões, banco de nomes e pasta das capas do libretro. */
final class Consoles {

    static final class Console {
        final String id, name, core, thumbs;
        final String[] ext;
        /** Botões do RetroPad que o console usa (para a tela de trocar botões e o turbo). */
        final int[] buttons;
        /** Tamanho máximo de uma ROM desse console. */
        final long maxBytes;

        Console(String id, String name, String core, String thumbs, String[] ext, int[] buttons, long maxBytes) {
            this.id = id;
            this.name = name;
            this.core = core;
            this.thumbs = thumbs;
            this.ext = ext;
            this.buttons = buttons;
            this.maxBytes = maxBytes;
        }
    }

    private static final long MB = 1024 * 1024;

    static final Console[] ALL = {
            new Console("nes", "Nintendinho", "fceumm", "Nintendo - Nintendo Entertainment System",
                    new String[]{"nes", "unf", "unif", "fds"},
                    new int[]{Native.A, Native.B, Native.SELECT, Native.START}, 4 * MB),
            new Console("snes", "Super Nintendo", "snes9x", "Nintendo - Super Nintendo Entertainment System",
                    new String[]{"sfc", "smc", "swc", "fig"},
                    new int[]{Native.A, Native.B, Native.X, Native.Y, Native.L, Native.R, Native.SELECT, Native.START}, 12 * MB),
            new Console("gba", "Game Boy Advance", "mgba", "Nintendo - Game Boy Advance",
                    new String[]{"gba"},
                    new int[]{Native.A, Native.B, Native.L, Native.R, Native.SELECT, Native.START}, 33 * MB),
            new Console("md", "Mega Drive", "gpgx", "Sega - Mega Drive - Genesis",
                    new String[]{"md", "gen", "smd", "bin"},
                    new int[]{Native.B, Native.A, Native.Y, Native.X, Native.L, Native.R, Native.START, Native.SELECT}, 10 * MB),
    };

    private Consoles() {
    }

    static Console byId(String id) {
        for (Console c : ALL) if (c.id.equals(id)) return c;
        return null;
    }

    static Console byExtension(String fileName) {
        String ext = extension(fileName);
        for (Console c : ALL) for (String e : c.ext) if (e.equals(ext)) return c;
        return null;
    }

    static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
