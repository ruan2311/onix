package com.onix.gamehub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Controle na tela do celular, no traço do Onix: linhas na cor de cada botão, com o símbolo
 * dentro, e preenchimento leve quando apertado. Cada console tem o seu esquema (NES: B e A; SNES:
 * o losango X, A, B, Y nas cores do controle; GBA: B, A, L e R; Mega Drive: A, B, C e X, Y, Z).
 * Em pé, ocupa a parte de baixo da tela (o jogo fica em cima); deitado, fica por cima do jogo,
 * nos cantos. Vários dedos ao mesmo tempo e arrastar de um botão para outro funcionam; o
 * direcional aceita diagonais.
 *
 * Editor (menu do jogo ou Configurações → Controles): arrastar cada botão por conta própria (o
 * direcional, cada botão de ação, L, R, SELECT, START, menu e avançar), mudar o
 * tamanho e a transparência de todos ou, tocando num botão, só daquele; fica guardado em touch.json (TouchLayout), em pé e deitado separados.
 * O tamanho 100% é calculado para caber na tela: o direcional e os botões de ação não se encostam.
 * Os botões de ação guardam a posição por console ("snes.B"), porque cada um tem o seu esquema.
 */
final class TouchPad extends View {

    interface Listener {
        void padButtons(int bits);

        void padFastForward(boolean on);

        void padMenu();

        /** Editor fechado (Concluir). */
        void padEditDone();
    }

    private static final int ROUND = 0, PILL = 1, MENU = 2, FAST = 3;
    /* O que se arrasta no editor: cada botão. Os de ação vêm de G_FACE em diante, um por botão
     * (o 1 era o conjunto todo na versão 1 e não é mais usado). */
    private static final int G_DPAD = 0, G_L = 2, G_R = 3, G_SELECT = 4, G_START = 5, G_MENU = 6, G_FAST = 7, G_FACE = 8;
    /* Barra do editor. */
    private static final int T_SMALLER = -10, T_BIGGER = -11, T_LESS = -12, T_MORE = -13, T_RESET = -14, T_DONE = -15, T_SIZE = -16, T_ALPHA = -17;

    private static final class Btn {
        String label;
        final int bit, color, kind;
        int group;
        final float rx, ry, size;   // posição e tamanho relativos ao grupo
        float cx, cy, r, w, h;

        Btn(String label, int bit, int color, int kind, int group, float rx, float ry, float size) {
            this.label = label;
            this.bit = bit;
            this.color = color;
            this.kind = kind;
            this.group = group;
            this.rx = rx;
            this.ry = ry;
            this.size = size;
        }

        boolean hit(float x, float y, float slack) {
            if (kind == ROUND || kind == MENU || kind == FAST) {
                float dx = x - cx, dy = y - cy, rr = r * slack;
                return dx * dx + dy * dy <= rr * rr;
            }
            float sw = w / 2 * slack + (slack - 1) * h, sh = h / 2 * slack + (slack - 1) * h;
            return Math.abs(x - cx) <= sw && Math.abs(y - cy) <= sh;
        }

        void bounds(RectF out) {
            if (kind == PILL) out.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            else out.set(cx - r, cy - r, cx + r, cy + r);
        }
    }

    private final Listener listener;
    private final int accent;
    private final boolean six;   // Mega Drive: 6 botões
    private final String console;
    private final int groups;
    private final float dp;
    private final List<Btn> face = new ArrayList<Btn>(), shoulders = new ArrayList<Btn>(), system = new ArrayList<Btn>();
    private final List<Btn> all = new ArrayList<Btn>(), tools = new ArrayList<Btn>();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF(), box = new RectF();
    private float dpadX, dpadY, dpadR;
    private boolean landscape;
    private Paint backdrop;

    private final TouchLayout layout;
    private boolean editing;
    private int dragGroup = -1, dragId = -1, pressTool;
    private float lastX, lastY, downX, downY;
    private boolean dragging;
    private int selected = -1;   // botão escolhido no editor: a barra mexe só nele
    private boolean es;

    /* Dedo → o que ele aperta: bits do RetroPad, ou -1 menu, -2 avançar. */
    private final SparseIntArray pointers = new SparseIntArray();
    private int bits, dpadBits;
    private boolean fast;

    TouchPad(Context c, String console, Listener l) {
        super(c);
        listener = l;
        layout = TouchLayout.load(c);
        dp = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // o brilho das linhas (setShadowLayer)
        int nes = 0xFFFF5A6E, snes = 0xFFA78BFA, gba = 0xFF38BDF8, md = 0xFFFBBF24;
        String sel = "SELECT";
        this.console = console;
        six = "md".equals(console);
        if ("snes".equals(console)) {
            accent = snes;
            face.add(new Btn("X", Native.X, 0xFF38BDF8, ROUND, G_FACE, 0, -1.35f, 1));
            face.add(new Btn("A", Native.A, 0xFFFF5A6E, ROUND, G_FACE, 1.35f, 0, 1));
            face.add(new Btn("B", Native.B, 0xFFFBBF24, ROUND, G_FACE, 0, 1.35f, 1));
            face.add(new Btn("Y", Native.Y, 0xFF4ADE80, ROUND, G_FACE, -1.35f, 0, 1));
            shoulders.add(new Btn("L", Native.L, snes, PILL, G_L, -1, 0, 1));
            shoulders.add(new Btn("R", Native.R, snes, PILL, G_R, 1, 0, 1));
        } else if ("gba".equals(console)) {
            accent = gba;
            face.add(new Btn("B", Native.B, gba, ROUND, G_FACE, -0.62f, 0.38f, 1.12f));
            face.add(new Btn("A", Native.A, gba, ROUND, G_FACE, 0.62f, -0.38f, 1.12f));
            shoulders.add(new Btn("L", Native.L, gba, PILL, G_L, -1, 0, 1));
            shoulders.add(new Btn("R", Native.R, gba, PILL, G_R, 1, 0, 1));
        } else if (six) {
            accent = md;
            // A, B e C embaixo; X, Y e Z menores em cima (o controle de 6 botões).
            face.add(new Btn("A", Native.Y, md, ROUND, G_FACE, -1.45f, 0.55f, 0.95f));
            face.add(new Btn("B", Native.B, md, ROUND, G_FACE, 0, 0.35f, 0.95f));
            face.add(new Btn("C", Native.A, md, ROUND, G_FACE, 1.45f, 0.15f, 0.95f));
            int dim = 0xCCFBBF24;
            face.add(new Btn("X", Native.L, dim, ROUND, G_FACE, -1.45f, -0.85f, 0.7f));
            face.add(new Btn("Y", Native.X, dim, ROUND, G_FACE, 0, -1.05f, 0.7f));
            face.add(new Btn("Z", Native.R, dim, ROUND, G_FACE, 1.45f, -1.25f, 0.7f));
            sel = "MODE";
        } else {
            accent = nes;
            face.add(new Btn("B", Native.B, nes, ROUND, G_FACE, -0.62f, 0.38f, 1.12f));
            face.add(new Btn("A", Native.A, nes, ROUND, G_FACE, 0.62f, -0.38f, 1.12f));
        }
        system.add(new Btn(sel, Native.SELECT, 0xFFA99FD0, PILL, G_SELECT, -1, 0, 1));
        system.add(new Btn("START", Native.START, 0xFFA99FD0, PILL, G_START, 1, 0, 1));
        system.add(new Btn("", -1, 0xFFF1EDFF, MENU, G_MENU, -1, 0, 1));
        system.add(new Btn("»", -2, 0xFF3DE1FF, FAST, G_FAST, 1, 0, 1));
        for (int i = 0; i < face.size(); i++) face.get(i).group = G_FACE + i;
        groups = G_FACE + face.size();
        all.addAll(face);
        all.addAll(shoulders);
        all.addAll(system);
        int t = 0xFFF1EDFF, cyan = 0xFF3DE1FF;
        tools.add(new Btn("−", T_SMALLER, t, PILL, -1, 0, 0, 0.6f));
        tools.add(new Btn("", T_SIZE, t, PILL, -1, 1, 0, 1.6f));
        tools.add(new Btn("+", T_BIGGER, t, PILL, -1, 2, 0, 0.6f));
        tools.add(new Btn("−", T_LESS, t, PILL, -1, 3, 0, 0.6f));
        tools.add(new Btn("", T_ALPHA, t, PILL, -1, 4, 0, 1.6f));
        tools.add(new Btn("+", T_MORE, t, PILL, -1, 5, 0, 0.6f));
        tools.add(new Btn("", T_RESET, t, PILL, -1, 6, 1, 2.2f));
        tools.add(new Btn("", T_DONE, cyan, PILL, -1, 7, 1, 1.3f));

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(1.5f * dp);
        dash.setPathEffect(new DashPathEffect(new float[]{6 * dp, 5 * dp}, 0));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setHapticFeedbackEnabled(true);
    }

    void setSpanish(boolean on) {
        es = on;
        invalidate();
    }

    /* ---------------- Posições ---------------- */

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        place(w, h);
    }

    /** Posições padrão (com o tamanho escolhido) e, por cima, onde a pessoa deixou cada grupo. */
    private void place(int w, int h) {
        if (w == 0 || h == 0) return;
        landscape = w > h;
        float s = Math.min(w, h), k = layout.scale;
        float reachX = faceReach(true), reachY = faceReach(false);
        if (landscape) {
            // Por cima do jogo: direcional embaixo à esquerda, botões embaixo à direita.
            dpadR = Math.min(s * 0.17f, 92 * dp) * k;
            float m = dpadR * 1.25f + 16 * dp;
            dpadX = m + 8 * dp;
            dpadY = h - m;
            placeFace(w - m - 8 * dp, h - m, dpadR);
            placeShoulders(m * 0.9f, 30 * dp, w - m * 0.9f, dpadR);
            placeSystem(w / 2f, h - 26 * dp, 26 * dp * k, w / 2f, 30 * dp, k);
        } else {
            // Em pé: a área inteira é do controle (o jogo fica em cima dela). O tamanho 100% é o
            // maior que deixa o direcional e o conjunto de ação lado a lado com folga, entre os
            // ombros (em cima) e SELECT/START (embaixo).
            float edge = 12 * dp, mid = 18 * dp, top = 26 * dp, bottom = h - 34 * dp - 30 * dp * k;
            float fitW = (w - 2 * edge - mid) / (2 + 2 * reachX);
            float fitH = (h - 26 * dp - 34 * dp - 30 * dp - 24 * dp) / (2 * Math.max(1, reachY) + 0.42f);
            dpadR = Math.min(Math.min(w * 0.2f, h * 0.24f), Math.min(fitW, fitH)) * k;
            float cy = (top + dpadR * 0.42f + bottom) / 2;
            dpadX = edge + dpadR;
            dpadY = cy;
            placeFace(w - edge - reachX * dpadR, cy, dpadR);
            placeShoulders(dpadR * 1.1f, top, w - dpadR * 1.1f, dpadR);
            placeSystem(w / 2f, h - 34 * dp, 30 * dp * k, w / 2f, 28 * dp, k);
        }
        // Ajustes da versão 1: SELECT+START e menu+avançar andavam juntos.
        legacy("sys", new int[]{G_SELECT, G_START}, w, h, true);
        legacy("top", new int[]{G_MENU, G_FAST}, w, h, true);
        // O conjunto de ação salvo na versão 1 vale para todos os consoles: fica guardado.
        int[] faceGroups = new int[face.size()];
        for (int i = 0; i < faceGroups.length; i++) faceGroups[i] = G_FACE + i;
        legacy("face", faceGroups, w, h, false);
        // Tamanho de cada botão, em volta do próprio centro.
        for (int g = 0; g < groups; g++) {
            float m = layout.size(key(g));
            if (m == 1) continue;
            if (g == G_DPAD) dpadR *= m;
            for (Btn b : all) {
                if (b.group != g) continue;
                b.r *= m;
                b.w *= m;
                b.h *= m;
            }
        }
        for (int g = 0; g < groups; g++) {
            float[] p = layout.get(landscape, key(g));
            if (p == null) continue;
            center(g, box);
            move(g, p[0] * w - box.centerX(), p[1] * h - box.centerY());
        }
        // Nada fica fora da área (tamanho maior, outra tela).
        for (int g = 0; g < groups; g++) {
            center(g, box);
            if (box.isEmpty()) continue;
            float dx = box.left < 0 ? -box.left : box.right > w ? w - box.right : 0;
            float dy = box.top < 0 ? -box.top : box.bottom > h ? h - box.bottom : 0;
            if (dx != 0 || dy != 0) move(g, dx, dy);
        }
        placeTools(w, h);
        backdrop = null;
        if (!landscape) {
            backdrop = new Paint();
            backdrop.setShader(new LinearGradient(0, 0, 0, h, Color.argb(0, 0, 0, 0), withAlpha(accent, 40), Shader.TileMode.CLAMP));
        }
        invalidate();
    }

    /** Até onde o conjunto de ação vai a partir do centro, em raios do direcional (x ou y). */
    private float faceReach(boolean horizontal) {
        float br = 0.42f, gap = br * 1.25f * (six ? 1.05f : 1f), out = 0;
        for (Btn b : face) out = Math.max(out, Math.abs(horizontal ? b.rx : b.ry) * gap + br * b.size);
        return out;
    }

    /** Nome do grupo no touch.json: o número, ou console.botão para os de ação. */
    private String key(int g) {
        return g >= G_FACE ? console + "." + face.get(g - G_FACE).label : String.valueOf(g);
    }

    /**
     * Um grupo salvo na versão 1 (vários botões juntos) move os botões dele. Com split, cada um
     * ganha a sua posição e o grupo antigo some; sem, o grupo fica (vale para outros consoles).
     */
    private void legacy(String key, int[] gs, int w, int h, boolean split) {
        float[] p = layout.get(landscape, key);
        if (p == null) return;
        box.setEmpty();
        for (int g : gs) {
            center(g, rect);
            box.union(rect);
        }
        float dx = p[0] * w - box.centerX(), dy = p[1] * h - box.centerY();
        for (int g : gs) move(g, dx, dy);
        if (!split) return;
        for (int g : gs) {
            center(g, box);
            layout.set(landscape, key(g), box.centerX() / w, box.centerY() / h);
        }
        layout.drop(landscape, key);
        layout.save(getContext());
    }

    private void placeFace(float cx, float cy, float r) {
        float br = r * 0.42f, gap = br * 1.25f * (six ? 1.05f : 1f);
        for (Btn b : face) {
            b.r = br * b.size;
            b.cx = cx + b.rx * gap;
            b.cy = cy + b.ry * gap;
        }
    }

    private void placeShoulders(float lx, float y, float rx, float r) {
        for (Btn b : shoulders) {
            b.w = r * 1.25f;
            b.h = r * 0.42f;
            b.cx = b.rx < 0 ? lx : rx;
            b.cy = y + b.h / 2;
        }
    }

    private void placeSystem(float cx, float bottomY, float pillH, float topX, float topY, float k) {
        float small = 30 * dp * 0.62f * k;
        for (Btn b : system) {
            if (b.kind == PILL) {
                b.h = pillH;
                b.w = pillH * 3.1f;
                b.cx = cx + b.rx * (b.w / 2 + 10 * dp);
                b.cy = bottomY - b.h / 2;
            } else {
                b.r = small;
                b.w = b.h = b.r * 2;
                b.cx = topX + b.rx * (b.r + 8 * dp);
                b.cy = topY + b.r;
            }
        }
    }

    /**
     * A barra do editor: duas fileiras (tamanho e transparência; restaurar e concluir), no alto da
     * área ou, se ali houver botões, na primeira faixa livre abaixo deles.
     */
    private void placeTools(int w, int areaH) {
        float h = 36 * dp, gap = 8 * dp, unit = 54 * dp;
        float row0 = 0, row1 = 0;
        for (Btn b : tools) {
            b.h = h;
            b.w = unit * b.size;
            if (b.ry == 0) row0 += b.w + gap;
            else row1 += b.w + gap;
        }
        float barH = 2 * h + gap, left = (w - Math.max(row0, row1) + gap) / 2, top = 12 * dp;
        List<Float> tries = new ArrayList<Float>();
        tries.add(top);
        for (int g = 0; g < groups; g++) {
            center(g, box);
            if (!box.isEmpty()) tries.add(box.bottom + 14 * dp);
        }
        java.util.Collections.sort(tries);
        for (float y : tries) {
            if (y + barH > areaH - 8 * dp) break;
            if (free(left, y, w - left, y + barH)) {
                top = y;
                break;
            }
        }
        float x0 = (w - row0 + gap) / 2, x1 = (w - row1 + gap) / 2, y0 = top + h / 2, y1 = y0 + h + gap;
        for (Btn b : tools) {
            if (b.ry == 0) {
                b.cx = x0 + b.w / 2;
                b.cy = y0;
                x0 += b.w + gap;
            } else {
                b.cx = x1 + b.w / 2;
                b.cy = y1;
                x1 += b.w + gap;
            }
        }
    }

    private boolean free(float l, float t, float r, float b) {
        for (int g = 0; g < groups; g++) {
            center(g, box);
            if (box.isEmpty()) continue;
            box.inset(-10 * dp, -10 * dp);
            if (box.intersects(l, t, r, b)) return false;
        }
        return true;
    }

    /** Caixa do grupo (para arrastar e para o contorno no editor). */
    private void center(int g, RectF out) {
        if (g == G_DPAD) {
            out.set(dpadX - dpadR, dpadY - dpadR, dpadX + dpadR, dpadY + dpadR);
            return;
        }
        boolean first = true;
        for (Btn b : all) {
            if (b.group != g) continue;
            b.bounds(rect);
            if (first) out.set(rect);
            else out.union(rect);
            first = false;
        }
        if (first) out.setEmpty();
    }

    private void move(int g, float dx, float dy) {
        if (g == G_DPAD) {
            dpadX += dx;
            dpadY += dy;
            return;
        }
        for (Btn b : all) {
            if (b.group != g) continue;
            b.cx += dx;
            b.cy += dy;
        }
    }

    /* ---------------- Editor ---------------- */

    void setEditing(boolean on) {
        editing = on;
        dragGroup = -1;
        selected = -1;
        pointers.clear();
        update();
        invalidate();
    }

    boolean isEditing() {
        return editing;
    }

    private void tool(int id) {
        float step = id == T_BIGGER || id == T_MORE ? 0.1f : -0.1f;
        if (selected >= 0 && id != T_DONE) {
            // Só o botão escolhido.
            String k = key(selected);
            if (id == T_SMALLER || id == T_BIGGER) layout.setSize(k, Math.round((layout.size(k) + step) * 10) / 10f);
            else if (id == T_LESS || id == T_MORE) layout.setOpacity(k, Math.round((layout.opacity(k) + step) * 10) / 10f);
            else if (id == T_RESET) layout.resetButton(landscape, k);
            else return;
        } else if (id == T_SMALLER || id == T_BIGGER) {
            layout.scale = TouchLayout.clamp(Math.round((layout.scale + (id == T_BIGGER ? 0.1f : -0.1f)) * 10) / 10f, TouchLayout.MIN_SCALE, TouchLayout.MAX_SCALE);
        } else if (id == T_LESS || id == T_MORE) {
            layout.opacity = TouchLayout.clamp(Math.round((layout.opacity + (id == T_MORE ? 0.1f : -0.1f)) * 10) / 10f, TouchLayout.MIN_OPACITY, 1);
        } else if (id == T_RESET) {
            layout.reset(landscape);
        } else if (id == T_DONE) {
            layout.save(getContext());
            setEditing(false);
            listener.padEditDone();
            return;
        } else {
            return;
        }
        layout.save(getContext());
        place(getWidth(), getHeight());
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private boolean editTouch(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // Toque rápido num botão da barra usa a barra; arrastar move o botão que estiver ali.
            float x = e.getX(), y = e.getY();
            pressTool = 0;
            for (Btn b : tools) {
                if (b.bit != T_SIZE && b.bit != T_ALPHA && b.hit(x, y, 1.1f)) pressTool = b.bit;
            }
            dragGroup = groupAt(x, y);
            dragging = false;
            dragId = e.getPointerId(0);
            lastX = downX = x;
            lastY = downY = y;
            if (dragGroup >= 0 && pressTool == 0) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (pressTool == 0) invalidate();
        } else if (action == MotionEvent.ACTION_UP && pressTool == 0 && !dragging) {
            // Toque sem arrastar: escolhe o botão (de novo, ou fora de todos, desmarca).
            selected = dragGroup == selected ? -1 : dragGroup;
            dragGroup = -1;
            invalidate();
        } else if (action == MotionEvent.ACTION_UP && pressTool != 0 && !dragging) {
            int id = pressTool;
            pressTool = 0;
            dragGroup = -1;
            tool(id);
        } else if (action == MotionEvent.ACTION_MOVE && dragGroup >= 0) {
            int i = e.findPointerIndex(dragId);
            if (i < 0) return true;
            float x = e.getX(i), y = e.getY(i);
            if (!dragging) {
                if (Math.hypot(x - downX, y - downY) < 10 * dp) return true;
                if (pressTool != 0) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (selected >= 0) selected = dragGroup;
            }
            dragging = true;
            center(dragGroup, box);
            // Não deixa o grupo sair da área.
            float dx = Math.max(-box.left, Math.min(getWidth() - box.right, x - lastX));
            float dy = Math.max(-box.top, Math.min(getHeight() - box.bottom, y - lastY));
            move(dragGroup, dx, dy);
            lastX = x;
            lastY = y;
            invalidate();
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && dragGroup >= 0) {
            center(dragGroup, box);
            layout.set(landscape, key(dragGroup), box.centerX() / getWidth(), box.centerY() / getHeight());
            layout.save(getContext());
            dragGroup = -1;
            pressTool = 0;
            placeTools(getWidth(), getHeight());
            invalidate();
        }
        return true;
    }

    /** O grupo sob o dedo; com dois perto, o mais próximo (botões soltos ficam lado a lado). */
    private int groupAt(float x, float y) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int g = 0; g < groups; g++) {
            center(g, box);
            if (box.isEmpty()) continue;
            float out = (float) Math.hypot(Math.max(0, Math.max(box.left - x, x - box.right)), Math.max(0, Math.max(box.top - y, y - box.bottom)));
            if (out > 14 * dp) continue;
            float d = out * 1000 + (float) Math.hypot(x - box.centerX(), y - box.centerY());
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    /* ---------------- Desenho ---------------- */

    private static int withAlpha(int c, int a) {
        return (c & 0x00FFFFFF) | (a << 24);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (backdrop != null) c.drawRect(0, 0, getWidth(), getHeight(), backdrop);
        if (editing) c.drawColor(Color.argb(110, 4, 2, 10));
        drawDpad(c, alpha(G_DPAD));
        for (Btn b : all) drawButton(c, b, alpha(b.group));
        if (editing) drawEditor(c);
    }

    /** Opacidade do botão (no editor, nunca some de vez). */
    private int alpha(int g) {
        float o = layout.opacity(key(g));
        return Math.round(255 * (editing ? Math.max(o, 0.6f) : o));
    }

    /** Nome curto do botão escolhido, para a barra do editor. */
    private String name(int g) {
        if (g == G_DPAD) return es ? "Cruceta" : "Direcional";
        if (g == G_MENU) return "Menu";
        for (Btn b : all) if (b.group == g) return b.label;
        return "";
    }

    private void drawEditor(Canvas c) {
        for (int g = 0; g < groups; g++) {
            center(g, box);
            if (box.isEmpty()) continue;
            box.inset(-10 * dp, -10 * dp);
            if (g == selected) {
                stroke(0xFF3DE1FF, 255, 2.2f * dp);
                c.drawRoundRect(box, 14 * dp, 14 * dp, line);
                continue;
            }
            dash.setColor(g == dragGroup ? 0xFF3DE1FF : 0x88FFFFFF);
            c.drawRoundRect(box, 14 * dp, 14 * dp, dash);
        }
        boolean one = selected >= 0;
        for (Btn b : tools) {
            if (b.bit == T_SIZE) b.label = (es ? "Tamaño " : "Tamanho ") + Math.round((one ? layout.size(key(selected)) : layout.scale) * 100) + "%";
            else if (b.bit == T_ALPHA) b.label = (es ? "Opacidad " : "Opacidade ") + Math.round((one ? layout.opacity(key(selected)) : layout.opacity) * 100) + "%";
            else if (b.bit == T_RESET) b.label = one ? (es ? "Restaurar " : "Restaurar ") + name(selected) : "Restaurar";
            else if (b.bit == T_DONE) b.label = es ? "Listo" : "Concluir";
            rect.set(b.cx - b.w / 2, b.cy - b.h / 2, b.cx + b.w / 2, b.cy + b.h / 2);
            fill.setColor(0xE60E0820);
            c.drawRoundRect(rect, b.h / 2, b.h / 2, fill);
            boolean info = b.bit == T_SIZE || b.bit == T_ALPHA;
            // Com um botão escolhido, a barra fica ciano: vale só para ele.
            int col = one && b.bit != T_DONE ? 0xFF3DE1FF : b.color;
            stroke(col, info ? (one ? 160 : 70) : 220, 1.6f * dp);
            c.drawRoundRect(rect, b.h / 2, b.h / 2, line);
            text.setColor(col);
            text.setShadowLayer(0, 0, 0, 0);
            text.setLetterSpacing(0);
            text.setTextSize(b.label.length() <= 1 ? 20 * dp : 13 * dp);
            float fit = (b.w - 14 * dp) / text.measureText(b.label);
            if (fit < 1) text.setTextSize(text.getTextSize() * fit);
            c.drawText(b.label, b.cx, b.cy - (text.descent() + text.ascent()) / 2, text);
        }
    }

    private void stroke(int color, int alpha, float width) {
        line.setColor(withAlpha(color, alpha));
        line.setStrokeWidth(width);
        line.setShadowLayer(7 * dp, 0, 0, withAlpha(color, alpha * 3 / 5));
    }

    private void drawDpad(Canvas c, int base) {
        float r = dpadR, a = r * 0.34f;   // meia largura do braço
        path.reset();
        path.moveTo(dpadX - a, dpadY - r);
        path.lineTo(dpadX + a, dpadY - r);
        path.lineTo(dpadX + a, dpadY - a);
        path.lineTo(dpadX + r, dpadY - a);
        path.lineTo(dpadX + r, dpadY + a);
        path.lineTo(dpadX + a, dpadY + a);
        path.lineTo(dpadX + a, dpadY + r);
        path.lineTo(dpadX - a, dpadY + r);
        path.lineTo(dpadX - a, dpadY + a);
        path.lineTo(dpadX - r, dpadY + a);
        path.lineTo(dpadX - r, dpadY - a);
        path.lineTo(dpadX - a, dpadY - a);
        path.close();
        // Braços apertados ganham preenchimento.
        fill.setColor(withAlpha(accent, 90 * base / 255));
        if ((dpadBits & 1 << Native.UP) != 0) c.drawRect(dpadX - a, dpadY - r, dpadX + a, dpadY - a, fill);
        if ((dpadBits & 1 << Native.DOWN) != 0) c.drawRect(dpadX - a, dpadY + a, dpadX + a, dpadY + r, fill);
        if ((dpadBits & 1 << Native.LEFT) != 0) c.drawRect(dpadX - r, dpadY - a, dpadX - a, dpadY + a, fill);
        if ((dpadBits & 1 << Native.RIGHT) != 0) c.drawRect(dpadX + a, dpadY - a, dpadX + r, dpadY + a, fill);
        stroke(accent, base, 2.4f * dp);
        c.drawPath(path, line);
        // Setas e o centro.
        fill.setColor(withAlpha(accent, base));
        float t = a * 0.55f, o = r * 0.72f;
        arrow(c, dpadX, dpadY - o, 0, -1, t);
        arrow(c, dpadX, dpadY + o, 0, 1, t);
        arrow(c, dpadX - o, dpadY, -1, 0, t);
        arrow(c, dpadX + o, dpadY, 1, 0, t);
        stroke(accent, base / 2, 1.6f * dp);
        c.drawCircle(dpadX, dpadY, a * 0.45f, line);
    }

    private void arrow(Canvas c, float x, float y, int dx, int dy, float s) {
        path.reset();
        path.moveTo(x + dx * s * 0.6f, y + dy * s * 0.6f);
        path.lineTo(x - dx * s * 0.4f + dy * s * 0.6f, y - dy * s * 0.4f + dx * s * 0.6f);
        path.lineTo(x - dx * s * 0.4f - dy * s * 0.6f, y - dy * s * 0.4f - dx * s * 0.6f);
        path.close();
        c.drawPath(path, fill);
    }

    private void drawButton(Canvas c, Btn b, int base) {
        boolean on = b.bit >= 0 ? (bits & 1 << b.bit) != 0 : (b.bit == -2 && fast);
        int alpha = Math.min(255, base * (b.color >>> 24) / 255);
        int col = b.color | 0xFF000000;
        fill.setColor(withAlpha(col, on ? 110 : 0));
        stroke(col, alpha, (b.kind == ROUND ? 2.6f : 2f) * dp);
        if (b.kind == ROUND || b.kind == MENU || b.kind == FAST) {
            if (on) c.drawCircle(b.cx, b.cy, b.r, fill);
            c.drawCircle(b.cx, b.cy, b.r, line);
        } else {
            rect.set(b.cx - b.w / 2, b.cy - b.h / 2, b.cx + b.w / 2, b.cy + b.h / 2);
            if (on) c.drawRoundRect(rect, b.h / 2, b.h / 2, fill);
            c.drawRoundRect(rect, b.h / 2, b.h / 2, line);
        }
        if (b.kind == MENU) {
            // Três traços: abre o menu do jogo.
            stroke(col, alpha, 2f * dp);
            float hw = b.r * 0.45f;
            for (int i = -1; i <= 1; i++) c.drawLine(b.cx - hw, b.cy + i * b.r * 0.32f, b.cx + hw, b.cy + i * b.r * 0.32f, line);
            return;
        }
        text.setColor(withAlpha(col, alpha));
        text.setShadowLayer(6 * dp, 0, 0, withAlpha(col, alpha / 2));
        float size = b.kind == ROUND ? b.r * 0.85f : b.kind == FAST ? b.r * 1.15f : b.h * 0.42f;
        text.setTextSize(size);
        text.setLetterSpacing(b.kind == PILL && b.label.length() > 1 ? 0.12f : 0);
        c.drawText(b.label, b.cx, b.cy - (text.descent() + text.ascent()) / 2, text);
    }

    /* ---------------- Toque ---------------- */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editing) return editTouch(e);
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            pointers.clear();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = e.getPointerId(e.getActionIndex());
            if (pointers.get(id, 0) == -1 && hitMenu(e.getX(e.getActionIndex()), e.getY(e.getActionIndex()))) listener.padMenu();
            pointers.delete(id);
        } else {
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                int t = target(e.getX(i), e.getY(i));
                // O menu só conta para o dedo que começou nele (arrastar não abre o menu).
                if (t == -1 && action == MotionEvent.ACTION_MOVE && pointers.get(id, 0) != -1) t = 0;
                pointers.put(id, t);
            }
        }
        update();
        return true;
    }

    private boolean hitMenu(float x, float y) {
        for (Btn b : system) if (b.kind == MENU) return b.hit(x, y, 1.4f);
        return false;
    }

    /** O que está sob o dedo: bits do RetroPad (direcional + botão), -1 menu, -2 avançar, 0 nada. */
    private int target(float x, float y) {
        float dx = x - dpadX, dy = y - dpadY, dist = (float) Math.sqrt(dx * dx + dy * dy);
        Btn best = null;
        float bestD = Float.MAX_VALUE;
        for (Btn b : all) {
            if (!b.hit(x, y, 1.3f)) continue;
            float d = (x - b.cx) * (x - b.cx) + (y - b.cy) * (y - b.cy);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        // A folga do direcional só vale onde não há botão: perto dele, o botão vizinho ganha.
        if (dist <= dpadR || (best == null && dist <= dpadR * 1.35f)) {
            if (dist < dpadR * 0.16f) return 0;
            int out = 0;
            if (dy < -0.38f * dist) out |= 1 << Native.UP;
            if (dy > 0.38f * dist) out |= 1 << Native.DOWN;
            if (dx < -0.38f * dist) out |= 1 << Native.LEFT;
            if (dx > 0.38f * dist) out |= 1 << Native.RIGHT;
            return out | 1 << 30;   // marca: veio do direcional
        }
        if (best == null) return 0;
        if (best.bit == -1) return -1;
        if (best.bit == -2) return -2;
        return 1 << best.bit;
    }

    private void update() {
        int nb = 0, nd = 0;
        boolean nf = false;
        for (int i = 0; i < pointers.size(); i++) {
            int t = pointers.valueAt(i);
            if (t == -2) nf = true;
            else if (t > 0) {
                int clean = t & ~(1 << 30);
                nb |= clean;
                if ((t & 1 << 30) != 0) nd |= clean;
            }
        }
        boolean changed = nb != bits || nf != fast || nd != dpadBits;
        if ((nb & ~bits) != 0 || (nf && !fast)) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (nb != bits) listener.padButtons(nb);
        if (nf != fast) listener.padFastForward(nf);
        bits = nb;
        dpadBits = nd;
        fast = nf;
        if (changed) invalidate();
    }

    /** Solta tudo (menu aberto, controle conectado). */
    void release() {
        pointers.clear();
        update();
    }
}
