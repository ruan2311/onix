package com.onix.gamehub;

import java.util.Locale;

/** Os consoles do GameHub: núcleo, extensões, banco de nomes e pasta das capas do libretro. */
final class Consoles {

    static final class Console {
        final String id, name, core, thumbs;
        final String[] ext;
        /** Botões do RetroPad que o console usa (para a tela de trocar botões e o turbo). */
        final int[] buttons;
        /** Tamanho máximo de uma ROM desse console. */
        final long maxBytes;

        /** Núcleo e pasta das capas próprios de uma extensão (o Atari 7800 no console Atari). */
        String altExt, altCore, altThumbs;

        Console(String id, String name, String core, String thumbs, String[] ext, int[] buttons, long maxBytes) {
            this.id = id;
            this.name = name;
            this.core = core;
            this.thumbs = thumbs;
            this.ext = ext;
            this.buttons = buttons;
            this.maxBytes = maxBytes;
        }

        Console alt(String ext, String core, String thumbs) {
            altExt = ext;
            altCore = core;
            altThumbs = thumbs;
            return this;
        }

        String coreFor(String file) {
            return altExt != null && altExt.equals(extension(file)) ? altCore : core;
        }

        String thumbsFor(String file) {
            return altExt != null && altExt.equals(extension(file)) ? altThumbs : thumbs;
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
            new Console("gbc", "Game Boy Color", "mgba", "Nintendo - Game Boy Color",
                    new String[]{"gbc", "gb"},
                    new int[]{Native.A, Native.B, Native.SELECT, Native.START}, 9 * MB),
            // Discos: .chd e .pbp num arquivo só, ou o .cue com os .bin dele (Importer.cue).
            new Console("ps1", "PlayStation", "pcsx_rearmed", "Sony - PlayStation",
                    new String[]{"chd", "pbp", "cue"},
                    new int[]{Native.B, Native.A, Native.Y, Native.X, Native.L, Native.R, Native.L2, Native.R2, Native.SELECT, Native.START}, 1024 * MB),
            new Console("atari", "Atari", "stella2014", "Atari - 2600",
                    new String[]{"a26", "a78"},
                    new int[]{Native.B, Native.A, Native.SELECT, Native.START}, 1 * MB)
                    .alt("a78", "prosystem", "Atari - 7800"),
            // .zip com o nome do romset (sf2.zip), guardado como veio em roms/arcade (Importer.arcade).
            new Console("arcade", "Fliperama", "fbneo", "FBNeo - Arcade Games",
                    new String[]{},
                    new int[]{Native.B, Native.A, Native.Y, Native.X, Native.L, Native.R, Native.SELECT, Native.START}, 1024 * MB),
    };

    /** Um .bin pequeno é do Mega Drive; os maiores são faixas de um disco de PS1 (com o .cue). */
    static final long MD_MAX = 10 * MB;

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
