package com.onix.gamehub;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
        /** Desenha em 3D (pede OpenGL ES 3 na tela do jogo). */
        boolean hw;
        /** O jogo anda pelo analógico: a cruz da tela e do controle remoto viram analógico. */
        boolean stick;
        /** Só em aparelhos de 64 bits, sem garantia de velocidade (GameCube, Wii e PS2). */
        boolean experimental;
        /** Arquivos de sistema que o núcleo precisa (Files.system), baixados na primeira vez. */
        String system;

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

        Console hw(boolean stick) {
            this.hw = true;
            this.stick = stick;
            return this;
        }

        Console experimental() {
            experimental = true;
            return this;
        }

        Console system(String zip) {
            system = zip;
            return this;
        }

        String coreFor(String file) {
            return altExt != null && altExt.equals(extension(file)) ? altCore : core;
        }

        String thumbsFor(String file) {
            return altExt != null && altExt.equals(extension(file)) ? altThumbs : thumbs;
        }
    }

    private static final long MB = 1024 * 1024, GB = 1024 * MB;

    static final Console[] ALL = available(new Console[]{
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
            // Os consoles 3D (no PC eles vão pelo RetroArch; aqui rodam no nosso motor, jni/retro.c).
            new Console("n64", "Nintendo 64", "parallel_n64", "Nintendo - Nintendo 64",
                    new String[]{"z64", "n64", "v64"},
                    new int[]{Native.B, Native.Y, Native.L2, Native.L, Native.R, Native.START}, 64 * MB).hw(true),
            new Console("ds", "Nintendo DS", "melondsds", "Nintendo - Nintendo DS",
                    new String[]{"nds"},
                    new int[]{Native.A, Native.B, Native.X, Native.Y, Native.L, Native.R, Native.SELECT, Native.START}, 512 * MB)
                    .hw(false),
            new Console("psp", "PSP", "ppsspp", "Sony - PlayStation Portable",
                    new String[]{"cso", "iso"},
                    new int[]{Native.B, Native.A, Native.Y, Native.X, Native.L, Native.R, Native.SELECT, Native.START}, 4 * GB)
                    .hw(false).system("system-PPSSPP.zip"),
            new Console("gc", "GameCube", "dolphin", "Nintendo - GameCube",
                    new String[]{"gcm", "gcz", "ciso", "rvz", "wia", "iso"},
                    new int[]{Native.A, Native.B, Native.X, Native.Y, Native.L2, Native.R2, Native.R, Native.START}, 4 * GB)
                    .hw(true).experimental().system("system-Dolphin.zip"),
            new Console("wii", "Wii", "dolphin", "Nintendo - Wii",
                    new String[]{"wbfs", "rvz", "wia", "gcz", "iso"},
                    new int[]{Native.A, Native.B, Native.X, Native.Y, Native.L2, Native.R2, Native.L, Native.R, Native.SELECT, Native.START, Native.R3}, 10 * GB)
                    .hw(true).experimental().system("system-Dolphin.zip"),
            new Console("ps2", "PlayStation 2", "pcsx2", "Sony - PlayStation 2",
                    new String[]{"iso", "chd"},
                    new int[]{Native.B, Native.A, Native.Y, Native.X, Native.L, Native.R, Native.L2, Native.R2, Native.L3, Native.R3, Native.SELECT, Native.START}, 10 * GB)
                    .hw(false).experimental(),
    });

    /**
     * GameCube, Wii e PS2 só nos aparelhos de 64 bits (os núcleos deles só vão no arm64-v8a do APK)
     * e, como os outros 3D, só a partir do mínimo de processador, memória e OpenGL (Hardware).
     */
    private static Console[] available(Console[] all) {
        boolean is64 = android.os.Build.SUPPORTED_64_BIT_ABIS.length > 0;
        List<Console> out = new ArrayList<Console>();
        for (Console c : all) if ((is64 || !c.experimental) && Hardware.runs(c.id)) out.add(c);
        return out.toArray(new Console[0]);
    }

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

    /**
     * O console de um arquivo. As extensões repetidas (.iso, .chd, .rvz…) pedem uma olhada no
     * começo do disco, como no PC (pc/src/store.js): números mágicos do GameCube e do Wii, o
     * sistema de arquivos do DVD (PSP x PS2) e, no .chd, o tamanho (CD de PS1 x DVD de PS2).
     */
    static Console of(File f, String fileName) {
        String e = extension(fileName);
        try {
            if ("iso".equals(e) || "gcm".equals(e)) {
                byte[] h = peek(f, 0, 0x20);
                if (h.length >= 0x20 && be32(h, 0x18) == 0x5D1C9EA3) return byId("wii");
                if (h.length >= 0x20 && be32(h, 0x1C) == 0xC2339F3D) return byId("gc");
                if ("gcm".equals(e)) return byId("gc");
                // Descritor do volume ISO 9660 no setor 16: "PSP GAME" ou "PLAYSTATION" no system id.
                byte[] pvd = peek(f, 16 * 2048 + 8, 32);
                String sys = new String(pvd, "ISO-8859-1").trim();
                if (sys.startsWith("PSP GAME")) return byId("psp");
                if (sys.startsWith("PLAYSTATION")) return byId("ps2");
                return null;
            }
            if ("rvz".equals(e) || "wia".equals(e)) {
                byte[] h = peek(f, 0x48, 4);
                return h.length == 4 ? byId(be32(h, 0) == 2 ? "wii" : "gc") : null;
            }
            if ("gcz".equals(e)) {
                byte[] h = peek(f, 4, 4);
                return h.length == 4 && (h[0] & 0xFF) == 1 && h[1] == 0 && h[2] == 0 && h[3] == 0 ? byId("wii") : byId("gc");
            }
            if ("chd".equals(e)) return f.length() > 900 * MB && byId("ps2") != null ? byId("ps2") : byId("ps1");
        } catch (Exception ignored) {
            return null;
        }
        return byExtension(fileName);
    }

    private static byte[] peek(File f, long at, int n) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            long left = at;
            while (left > 0) {
                long k = in.skip(left);
                if (k <= 0) return new byte[0];
                left -= k;
            }
            byte[] b = new byte[n];
            int got = 0;
            while (got < n) {
                int k = in.read(b, got, n - got);
                if (k < 0) break;
                got += k;
            }
            if (got == n) return b;
            byte[] out = new byte[got];
            System.arraycopy(b, 0, out, 0, got);
            return out;
        } finally {
            in.close();
        }
    }

    private static long be32(byte[] b, int at) {
        return ((b[at] & 0xFFL) << 24) | ((b[at + 1] & 0xFFL) << 16) | ((b[at + 2] & 0xFFL) << 8) | (b[at + 3] & 0xFFL);
    }

    static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
