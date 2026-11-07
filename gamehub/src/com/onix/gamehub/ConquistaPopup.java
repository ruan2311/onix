package com.onix.gamehub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayDeque;
import java.util.List;

/**
 * O aviso de conquista desbloqueada, nativo (views do Android, sem página web): na TV, uma página por cima do jogo
 * derrubava o FPS e nem aparecia. Comum (5 s), rara (7 s) e platina (1 min), com som, brilho, borda em degradê,
 * faíscas e enfeites parados. Só anima na entrada (e nos reflexos da platina); parado, não pesa no jogo.
 *
 * A janela "fura" a área do jogo (SurfaceView) do tamanho do que está na tela quando o aviso surge, e o que passa
 * para fora é cortado: por isso tudo acontece dentro de um palco quase invisível, que reserva a área.
 * Várias de uma vez entram em fila: a da tela sai depois de 3 s e a próxima entra.
 */
final class ConquistaPopup {

    private static final int COMUM = 0, RARA = 1, PLATINA = 2;
    private static final int SLIDE = 56;   // quanto o cartão desliza ao entrar (dp)
    private static final int MIN_WITH_QUEUE = 3000;   // com outra na fila, a da tela fica pelo menos isto

    private final Context ctx;
    private final boolean mobile;
    private final String lang;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Conquistas.Def> queue = new ArrayDeque<>();
    private View card, icon, pts, shine;
    private android.graphics.drawable.Drawable iconBg;
    private TextView kicker, title, sub;
    private LinearLayout row;
    private Stage stage;
    private android.media.SoundPool sound;
    private final int[] soundId = new int[3];
    private int shown;
    private long shownAt;
    private boolean busy;

    private String tr(String pt, String es) {
        return "es".equals(lang) ? es : pt;
    }

    /** Entram na fila e aparecem uma de cada vez. */
    void show(List<Conquistas.Def> list) {
        queue.addAll(list);
        if (!busy) next();
        else {   // a da tela sai antes (depois de 3 s) para a próxima entrar
            final int n = shown;
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (n == shown) leave(n);
                }
            }, Math.max(0, MIN_WITH_QUEUE - (System.currentTimeMillis() - shownAt)));
        }
    }

    void release() {
        ui.removeCallbacksAndMessages(null);
        queue.clear();
        if (stage != null) stage.stop();
        if (sound != null) sound.release();
    }

    private void next() {
        Conquistas.Def c = queue.poll();
        if (c == null) {
            busy = false;
            return;
        }
        busy = true;
        enter(c, ++shown);
    }

    /** Sai deslizando e esmaecendo; depois entra a próxima da fila. */
    private void leave(final int n) {
        if (n != shown) return;
        shown++;   // os avisos atrasados desta não valem mais
        stage.stop();
        final float dx = (mobile ? 32 : SLIDE) * ctx.getResources().getDisplayMetrics().density;
        card.animate().translationX(dx).setStartDelay(0).setDuration(320)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(2)).withLayer().start();
        stage.animate().alpha(0).setDuration(320).withEndAction(new Runnable() {
            @Override
            public void run() {
                stage.setVisibility(View.GONE);
                next();
            }
        }).start();
    }

    /** Entrada: o cartão desliza (a platina também cresce), o ícone pula, os pontos pipocam e o brilho passa.
     *  O resto (brilho em volta, borda, clarão, faíscas) é o palco que desenha. Depois fica parado até sair. */
    private void enter(Conquistas.Def c, final int n) {
        final Tier d = tier(c);
        shownAt = System.currentTimeMillis();
        stage.setVisibility(View.GONE);
        apply(d);
        final float dx = (mobile ? 32 : SLIDE) * ctx.getResources().getDisplayMetrics().density;
        for (View v : new View[] { card, icon, pts, shine }) v.animate().cancel();
        card.setAlpha(0);
        card.setTranslationX(dx);
        float s0 = d.spin ? 0.85f : 1;
        card.setScaleX(s0);
        card.setScaleY(s0);
        icon.setScaleX(0.3f);
        icon.setScaleY(0.3f);
        icon.setRotation(d.spin ? -360 : -25);
        pts.setScaleX(0);
        pts.setScaleY(0);
        shine.setVisibility(View.INVISIBLE);
        stage.animate().cancel();
        stage.setAlpha(1);
        stage.setVisibility(View.VISIBLE);
        card.animate().alpha(1).translationX(0).scaleX(1).scaleY(1).setStartDelay(0).setDuration(d.spin ? 520 : 380)
                .setInterpolator(d.spin ? new android.view.animation.OvershootInterpolator(1.4f) : new android.view.animation.DecelerateInterpolator(2))
                .withLayer().start();
        icon.animate().scaleX(1).scaleY(1).rotation(0).setStartDelay(180).setDuration(d.spin ? 800 : 420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3)).start();
        pts.animate().scaleX(1).scaleY(1).setStartDelay(d.spin ? 700 : 420).setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator(4)).start();
        stage.play();
        for (int at : d.glints ? new int[] { 500, 1600 } : new int[] { 500 }) ui.postDelayed(new Runnable() {   // já medido
            @Override
            public void run() {
                if (n == shown) sweep();
            }
        }, at);
        int tier = "platina".equals(c.tier) ? PLATINA : "rara".equals(c.tier) ? RARA : COMUM;
        if (soundId[tier] != 0) sound.play(soundId[tier], 1, 1, 1, 0, 1);
        if (d.glints) ui.postDelayed(new Runnable() {   // platina: um reflexo de vez em quando, enquanto está na tela
            @Override
            public void run() {
                if (n != shown || stage.getVisibility() != View.VISIBLE) return;
                stage.glint();
                sweep();
                ui.postDelayed(this, 8000);
            }
        }, 8000);
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                leave(n);
            }
        }, queue.isEmpty() ? d.stay : MIN_WITH_QUEUE);
    }

    /** Como cada conquista aparece: textos, cores e o quanto ela se mexe na entrada. */
    private static final class Tier {
        String kicker, title, sub, pts, icon;
        android.graphics.Bitmap badge;
        int[] colors;          // borda e brilho (o primeiro se repete no fim, para a borda fechar o círculo)
        int accent, badgeInk;  // cor do título pequeno e do selo de pontos; cor do texto do selo
        float scale = 1, glowRest, glowPeak = 1;
        int stay, anim, sparks, waves = 1, rings, turns = 1, pad = 22;
        boolean flash, spin, twinkle, glints;
        int decor;             // enfeites parados da borda: 0 nenhum, 1 rara, 2 platina
    }

    private Tier tier(Conquistas.Def c) {
        int tier = "platina".equals(c.tier) ? PLATINA : "rara".equals(c.tier) ? RARA : COMUM;
        Tier d = new Tier();
        if (tier == COMUM) {
            d.kicker = tr("CONQUISTA DESBLOQUEADA", "LOGRO DESBLOQUEADO");
            d.colors = new int[] { Color.rgb(255, 179, 71), Color.rgb(255, 216, 74), Color.rgb(255, 61, 154), Color.rgb(255, 179, 71) };
            d.accent = Color.rgb(255, 179, 71);
            d.badgeInk = Color.rgb(40, 22, 0);
            d.glowRest = 0.35f;
            d.glowPeak = 0.7f;
            d.stay = 5000;
            d.anim = 1300;
            d.sparks = 14;
        } else if (tier == RARA) {
            d.kicker = tr("CONQUISTA RARA", "LOGRO RARO");
            d.colors = new int[] { Color.rgb(255, 61, 154), Color.rgb(61, 225, 255), Color.rgb(167, 139, 250), Color.rgb(255, 61, 154) };
            d.accent = Color.rgb(74, 222, 128);
            d.badgeInk = Color.rgb(10, 30, 20);
            d.glowRest = 0.55f;
            d.stay = 7000;
            d.anim = 1900;
            d.sparks = 40;
            d.flash = true;
            d.decor = 1;
        } else {
            d.kicker = tr("PLATINA · 100% COMPLETO", "PLATINO · 100% COMPLETADO");
            d.colors = new int[] { Color.WHITE, Color.rgb(159, 232, 255), Color.rgb(255, 211, 107), Color.rgb(255, 154, 213), Color.WHITE };
            d.accent = Color.rgb(201, 241, 255);
            d.badgeInk = Color.rgb(16, 24, 44);
            d.scale = 1.15f;
            d.glowRest = 0.7f;
            d.stay = 60000;
            d.anim = 3400;
            d.sparks = 70;
            d.waves = 2;
            d.rings = 2;
            d.turns = 2;
            d.pad = 34;
            d.flash = d.spin = d.twinkle = d.glints = true;
            d.decor = 2;
        }
        d.title = c.title(lang);
        d.sub = c.desc(lang);
        d.icon = c.icon;
        d.badge = c.badge;
        if (c.kicker != null) d.kicker = c.kicker;
        d.pts = (tier == PLATINA ? "+" : "") + c.pts + " pts";
        return d;
    }

    /** Monta o cartão da conquista (escondido) e carrega os três sons. O texto e as cores mudam a cada nível. */
    ConquistaPopup(Context ctx, FrameLayout root, boolean mobile, String lang) {
        this.ctx = ctx;
        this.mobile = mobile;
        this.lang = lang;
        float dp = ctx.getResources().getDisplayMetrics().density;
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        row = line;
        FrameLayout box = new FrameLayout(ctx);   // moldura com os cantos redondos: recorta o brilho
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(235, 14, 12, 34));
        bg.setCornerRadius(14 * dp);
        box.setBackground(bg);
        box.setClipToOutline(true);

        TextView ic = new TextView(ctx);
        ic.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable ibg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[] { Color.rgb(32, 92, 88), Color.rgb(18, 40, 60) });
        ibg.setCornerRadius(10 * dp);
        ic.setBackground(ibg);
        iconBg = ibg;
        final float radius = 10 * dp;
        ic.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        ic.setClipToOutline(true);
        line.addView(ic, new LinearLayout.LayoutParams(0, 0));
        icon = ic;

        LinearLayout txt = new LinearLayout(ctx);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Math.round(12 * dp), 0, 0, 0);
        kicker = new TextView(ctx);
        kicker.setLetterSpacing(0.08f);
        kicker.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title = new TextView(ctx);
        title.setTextColor(Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sub = new TextView(ctx);
        sub.setTextColor(Color.rgb(170, 170, 195));
        txt.addView(kicker);
        txt.addView(title);
        txt.addView(sub);
        line.addView(txt);

        TextView badge = new TextView(ctx);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.setMargins(Math.round(14 * dp), 0, 0, 0);
        line.addView(badge, pp);
        pts = badge;
        box.addView(line, new FrameLayout.LayoutParams(-2, -2));
        View sh = new View(ctx);   // faixa de luz que atravessa o cartão
        sh.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.TRANSPARENT, Color.argb(70, 160, 255, 235), Color.TRANSPARENT }));
        sh.setRotation(18);
        sh.setVisibility(View.INVISIBLE);
        // Altura fixa igual à do cartão: com "ocupar a altura toda", a moldura esticava até a altura da tela.
        final FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Math.round(70 * dp), 1);
        box.addView(sh, sp);
        line.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (sp.height != b - t) {
                    sp.height = b - t;
                    shine.setLayoutParams(sp);
                }
            }
        });
        shine = sh;

        // A janela "fura" a área do jogo do tamanho do que está na tela quando o aviso surge, e o que passa para
        // fora é cortado. O palco (quase invisível) reserva a área do deslize e do brilho; o cartão anda só dentro dele.
        Stage st = new Stage(ctx);
        st.setBackgroundColor(Color.argb(1, 0, 0, 0));
        st.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.END));
        st.setVisibility(View.GONE);
        root.addView(st, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        stage = st;
        card = box;

        // Os sons ficam em arquivos prontos e tocam pelo SoundPool (não mexe no som do jogo).
        sound = new android.media.SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        String[] names = { "conquista-comum.ogg", "conquista-rara.ogg", "conquista-platina.ogg" };
        for (int i = 0; i < names.length; i++) {
            try {
                File f = new File(ctx.getCacheDir(), names[i]);
                java.io.InputStream in = ctx.getAssets().open(names[i]);
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0;) out.write(b, 0, n);
                out.close();
                in.close();
                soundId[i] = sound.load(f.getPath(), 1);
            } catch (Exception e) {
                soundId[i] = 0;
            }
        }
    }

    /** Põe os textos, cores e tamanhos do nível no cartão (com ele escondido, para a tela medir de novo). */
    private void apply(Tier d) {
        float dp = ctx.getResources().getDisplayMetrics().density, k = d.scale * 0.85f;   // 85%: um pouco menor
        row.setPadding(Math.round(12 * k * dp), Math.round(10 * k * dp), Math.round(18 * k * dp), Math.round(10 * k * dp));
        TextView ic = (TextView) icon;
        // RetroAchievements: a imagem da conquista; Cobalt: o emoji no fundo verde.
        ic.setText(d.badge != null ? "" : d.icon);
        ic.setBackground(d.badge != null ? new android.graphics.drawable.BitmapDrawable(ctx.getResources(), d.badge) : iconBg);
        ic.setTextSize(22 * k);
        int is = Math.round(46 * k * dp);
        ic.setLayoutParams(new LinearLayout.LayoutParams(is, is));
        kicker.setText(d.kicker);
        kicker.setTextColor(d.accent);
        kicker.setTextSize(10 * k);
        title.setText(d.title);
        title.setTextSize(16 * k);
        sub.setText(d.sub);
        sub.setTextSize(12 * k);
        TextView badge = (TextView) pts;
        badge.setText(d.pts);
        badge.setTextColor(d.badgeInk);
        badge.setTextSize(11 * k);
        badge.setPadding(Math.round(8 * dp), Math.round(2 * dp), Math.round(8 * dp), Math.round(2 * dp));
        android.graphics.drawable.GradientDrawable pbg = new android.graphics.drawable.GradientDrawable();
        pbg.setColor(d.accent);
        pbg.setCornerRadius(20 * dp);
        badge.setBackground(pbg);
        // Celular: o cartão ocupa no máximo 80% da largura da tela (em pé, o texto quebra em duas linhas em vez de
        // crescer). Na TV sobra espaço e nada muda.
        int pad = Math.round(d.pad * dp), slide = Math.round((mobile ? 32 : SLIDE) * dp);
        stage.setPadding(slide + pad, pad, pad, pad);
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int room = Math.round((mobile ? 0.8f : 0.9f) * dm.widthPixels) - slide - 2 * pad;
        int text = room - is - Math.round((12 + 18 + 12 + 14 + 8) * k * dp) - Math.round((16 + 34 * k) * dp + d.pts.length() * 6 * k * dp);
        for (TextView v : new TextView[] { kicker, title, sub }) {
            v.setMaxWidth(Math.max(Math.round(120 * dp), text));
            v.setMaxLines(2);
        }
        stage.tier = d;
    }

    /** A faixa de luz atravessa o cartão uma vez. */
    private void sweep() {
        shine.animate().cancel();
        shine.setTranslationX(-shine.getWidth() * 1.5f);
        shine.setVisibility(View.VISIBLE);
        shine.animate().translationX(card.getWidth() + shine.getWidth()).setStartDelay(0).setDuration(650)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        shine.setVisibility(View.INVISIBLE);
                    }
                }).start();
    }

    /** Palco da conquista: brilho colorido em volta, borda em degradê que gira, clarão, ondas, faíscas em pixel e
     *  estrelinhas. Desenhado à mão e só anima na entrada (tier.anim ms) e nos reflexos da platina; no resto do tempo
     *  fica parado e não pesa no jogo. */
    private final class Stage extends FrameLayout {
        final String[] ALIEN = { "..#..#..", ".######.", "##.##.##", "########", ".#.##.#.", "#......#" };
        final String[] ALIEN2 = { "...##...", ".######.", "#.####.#", "########", "..#..#..", ".#.##.#." };
        final String[] CROWN = { "#...#...#", "##.###.##", "#########", "#.#.#.#.#", "#########", ".#######.", "" };
        static final int BURST = 250, WAVE2 = 950, MAX = 80, GLINT = 1000;
        final float dp = getResources().getDisplayMetrics().density;
        final android.graphics.Paint paint = new android.graphics.Paint();
        final android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Paint border = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Matrix m = new android.graphics.Matrix();
        final android.graphics.RectF r = new android.graphics.RectF();
        final float[] vx = new float[MAX], vy = new float[MAX], life = new float[MAX], size = new float[MAX];
        final float[] tx = new float[12], ty = new float[12];
        final int[] col = new int[MAX];
        Tier tier, glowTier;
        android.graphics.SweepGradient sweep;
        int glints;
        Bitmap glow, decor;
        long t0 = -1, g0 = -1;
        android.animation.ValueAnimator anim;

        Stage(Context c) {
            super(c);
            border.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStyle(android.graphics.Paint.Style.STROKE);
        }

        /** Começa a entrada: as faíscas saem do troféu. */
        void play() {
            java.util.Random rnd = new java.util.Random();
            int[] c = tier.colors;
            for (int i = 0; i < tier.sparks; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, v = (90 + rnd.nextDouble() * (tier.spin ? 380 : 260)) * dp;
                vx[i] = (float) (Math.cos(a) * v);
                vy[i] = (float) (Math.sin(a) * v) - 60 * dp;
                life[i] = 0.7f + rnd.nextFloat() * (tier.spin ? 1.1f : 0.7f);
                size[i] = Math.round((2 + rnd.nextInt(tier.spin ? 4 : 3)) * dp);
                col[i] = i % 5 == 0 ? Color.WHITE : c[i % (c.length - 1)];
            }
            for (int i = 0; i < tx.length; i++) {   // estrelinhas da platina: em volta do cartão
                tx[i] = rnd.nextFloat();
                ty[i] = rnd.nextFloat();
            }
            t0 = android.os.SystemClock.uptimeMillis();
            g0 = -1;
            glints = 0;
            run(tier.anim);
        }

        /** Platina parada na tela: o brilho pulsa e a borda dá um quarto de volta. */
        void glint() {
            glints++;
            g0 = android.os.SystemClock.uptimeMillis();
            run(GLINT);
        }

        void stop() {
            if (anim != null) anim.cancel();
        }

        private void run(int ms) {
            stop();
            anim = android.animation.ValueAnimator.ofFloat(0, 1).setDuration(ms);
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    invalidate();
                }
            });
            anim.start();
        }

        /** O brilho é desfocado uma vez num bitmap (o desfoque na hora seria pesado). */
        private void makeGlow(int w, int h) {
            int g = Math.round(tier.pad * dp);
            if (glow != null && glowTier == tier && glow.getWidth() == w + 2 * g && glow.getHeight() == h + 2 * g) return;
            glowTier = tier;
            glow = Bitmap.createBitmap(w + 2 * g, h + 2 * g, Bitmap.Config.ARGB_8888);
            android.graphics.Paint gp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            int[] c = tier.colors;
            gp.setShader(new android.graphics.LinearGradient(g, 0, g + w, 0, new int[] { c[0], c[2], c[1] }, null,
                    android.graphics.Shader.TileMode.CLAMP));
            gp.setMaskFilter(new android.graphics.BlurMaskFilter((tier.spin ? 16 : 12) * dp, android.graphics.BlurMaskFilter.Blur.NORMAL));
            new Canvas(glow).drawRoundRect(new android.graphics.RectF(g, g, g + w, g + h), 14 * dp, 14 * dp, gp);
            decor = tier.decor == 0 ? null : Bitmap.createBitmap(w + 2 * g, h + 2 * g, Bitmap.Config.ARGB_8888);
            if (decor != null) drawDecor(new Canvas(decor), g, g, w, h);
            sweep = new android.graphics.SweepGradient(0, 0, c, null);
            border.setShader(sweep);
            border.setStrokeWidth((tier.spin ? 3 : 2.5f) * dp);
        }

        @Override
        protected void dispatchDraw(Canvas c) {
            View b = card;
            Tier d = tier;
            if (d == null) {
                super.dispatchDraw(c);
                return;
            }
            float bw = b.getWidth() * b.getScaleX(), bh = b.getHeight() * b.getScaleY(), a = b.getAlpha();
            float bx = b.getLeft() + b.getTranslationX() + (b.getWidth() - bw) / 2, by = b.getTop() + (b.getHeight() - bh) / 2;
            long now = android.os.SystemClock.uptimeMillis();
            float t = t0 < 0 ? d.anim : Math.min(d.anim, now - t0);
            float gt = g0 < 0 ? GLINT : Math.min(GLINT, now - g0), gk = (float) Math.sin(Math.PI * gt / GLINT);
            float pad = d.pad * dp, rad = 14 * dp;
            View ic = icon, row = (View) ic.getParent();   // as faíscas saem do ícone (já no lugar final)
            float ox = b.getLeft() + row.getLeft() + ic.getLeft() + ic.getWidth() / 2f, oy = b.getTop() + row.getTop() + ic.getTop() + ic.getHeight() / 2f;
            if (bw > 0) {
                makeGlow(b.getWidth(), b.getHeight());
                float lv;   // acende forte e assenta (a platina pulsa duas vezes)
                if (t < 300) lv = d.glowPeak * t / 300f;
                else if (d.spin && t > 900 && t < 1500) lv = d.glowRest + (1 - d.glowRest) * (float) Math.sin(Math.PI * (t - 900) / 600);
                else if (t < 1200) lv = d.glowPeak - (d.glowPeak - d.glowRest) * (t - 300) / 900f;
                else lv = d.glowRest;
                lv = Math.min(1, lv + 0.3f * gk);
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(255 * a * lv));
                r.set(bx - pad, by - pad, bx + bw + pad, by + bh + pad);
                c.drawBitmap(glow, null, r, paint);
            }
            super.dispatchDraw(c);
            if (bw <= 0) return;
            float k = t / d.anim, ang = 360 * d.turns * (1 - (1 - k) * (1 - k) * (1 - k)) + 90 * (glints == 0 ? 0 : glints - 1 + gt / GLINT);   // cada reflexo gira mais um quarto
            m.setRotate(ang - 90);
            m.postTranslate(bx + bw / 2, by + bh / 2);
            sweep.setLocalMatrix(m);
            border.setAlpha(Math.round(255 * a));
            float in = border.getStrokeWidth() / 2;
            r.set(bx + in, by + in, bx + bw - in, by + bh - in);
            c.drawRoundRect(r, rad - in, rad - in, border);
            if (decor != null) {   // enfeites da borda: desenhados uma vez, aqui só colados
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(255 * a));
                r.set(bx - pad, by - pad, bx + bw + pad, by + bh + pad);
                c.drawBitmap(decor, null, r, paint);
            }
            float age = (t - BURST) / 1000f;
            if (d.flash && age > 0 && age < 0.4f) {   // clarão quando o cartão chega
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round((d.spin ? 120 : 80) * (1 - age / 0.4f) * a));
                r.set(bx, by, bx + bw, by + bh);
                c.drawRoundRect(r, rad, rad, paint);
            }
            for (int w = 0; w < d.rings; w++) {   // ondas que saem do cartão (platina)
                float ra = (t - BURST - w * (WAVE2 - BURST)) / 700f;
                if (ra <= 0 || ra >= 1) continue;
                float grow = pad * (1 - (1 - ra) * (1 - ra));
                line.setColor(w == 0 ? Color.WHITE : d.colors[2]);
                line.setStrokeWidth(2 * dp);
                line.setAlpha(Math.round(220 * (1 - ra)));
                r.set(bx - grow, by - grow, bx + bw + grow, by + bh + grow);
                c.drawRoundRect(r, rad + grow, rad + grow, line);
            }
            for (int w = 0; w < d.waves; w++) {   // faíscas em pixel (quadradinhos, sem suavizar)
                float ag = (t - (w == 0 ? BURST : WAVE2)) / 1000f;
                if (ag <= 0) continue;
                int from = w * d.sparks / d.waves, to = (w + 1) * d.sparks / d.waves;
                float px = w == 0 ? ox : bx + bw / 2, py = w == 0 ? oy : by + bh / 2;   // a 2ª onda sai do meio do cartão
                for (int i = from; i < to; i++) {
                    if (ag >= life[i]) continue;
                    float x = px + vx[i] * ag, y = py + vy[i] * ag + 260 * dp * ag * ag, s2 = size[i] / 2;
                    paint.setColor(col[i]);
                    paint.setAlpha(Math.round(255 * (1 - ag / life[i])));
                    c.drawRect(x - s2, y - s2, x + s2, y + s2, paint);
                    if (i % 7 == 0) star(c, x, y, s2);
                }
            }
            if (d.twinkle && t > 300 && t < d.anim) {   // estrelinhas piscando em volta do cartão
                for (int i = 0; i < tx.length; i++) {
                    float ph = (float) Math.sin(Math.PI * 2 * ((t - 300) / 900f + tx[i]));
                    if (ph <= 0) continue;
                    boolean side = i % 2 == 0;   // metade em cima/embaixo, metade nas laterais
                    float x = side ? bx + tx[i] * bw : (i % 4 == 1 ? bx - pad * 0.45f : bx + bw + pad * 0.45f);
                    float y = side ? (i % 4 == 0 ? by - pad * 0.45f : by + bh + pad * 0.45f) : by + ty[i] * bh;
                    paint.setColor(i % 3 == 0 ? d.colors[2] : Color.WHITE);
                    paint.setAlpha(Math.round(255 * ph * (1 - k * 0.5f)));
                    star(c, x, y, Math.round(1.5f * dp));
                }
            }
        }

        /** Enfeites em pixel da borda (cartão em x, y, w, h dentro do bitmap). Rara: cantoneiras, invasores sobre a
         *  borda, pontinhos e losangos. Platina: cantoneiras duplas douradas com gemas, coroa no alto, gemas, invasores. */
        private void drawDecor(Canvas c, float x, float y, float w, float h) {
            float u = Math.round(2 * dp), o = 4 * dp;   // tamanho do pixel e distância da borda
            int[] k = tier.colors;
            android.graphics.Paint q = new android.graphics.Paint();
            if (tier.decor == 1) {
                int cyan = k[1], pink = k[0], violet = k[2], green = tier.accent;
                corner(c, q, x - o, y - o, 1, 1, u, 9, cyan);
                corner(c, q, x + w + o, y - o, -1, 1, u, 9, pink);
                corner(c, q, x - o, y + h + o, 1, -1, u, 9, pink);
                corner(c, q, x + w + o, y + h + o, -1, -1, u, 9, cyan);
                sprite(c, q, ALIEN, x + w * 0.3f - 4 * u, y - 6 * u - u, u, green);
                sprite(c, q, ALIEN2, x + w * 0.7f - 4 * u, y - 6 * u - u, u, cyan);
                q.setColor(violet);
                q.setAlpha(150);
                for (float px = x + w * 0.22f; px < x + w * 0.78f; px += 5 * u) c.drawRect(px, y + h + o, px + u, y + h + o + u, q);
                gem(c, q, x - o - u, y + h / 2, u, pink, Color.WHITE);
                gem(c, q, x + w + o + u, y + h / 2, u, pink, Color.WHITE);
            } else {
                int gold = k[2], ice = k[1], pink = k[3];
                for (int i = 0; i < 4; i++) {
                    int sx = i % 2 == 0 ? 1 : -1, sy = i < 2 ? 1 : -1;
                    float cx = sx > 0 ? x - o : x + w + o, cy = sy > 0 ? y - o : y + h + o;
                    corner(c, q, cx, cy, sx, sy, u, 14, gold);
                    corner(c, q, cx + sx * 2 * u, cy + sy * 2 * u, sx, sy, u, 7, Color.WHITE);
                    gem(c, q, cx - sx * u, cy - sy * u, u, ice, Color.WHITE);
                }
                sprite(c, q, CROWN, x + w / 2 - 4.5f * u, y - 7 * u - u, u, gold);
                gem(c, q, x + w / 2 - 9 * u, y - 3 * u, u, pink, Color.WHITE);
                gem(c, q, x + w / 2 + 9 * u, y - 3 * u, u, pink, Color.WHITE);
                q.setColor(ice);
                q.setAlpha(170);
                for (float px = x + w * 0.2f; px < x + w * 0.8f; px += 4 * u)
                    if (Math.abs(px - (x + w / 2)) > 5 * u) c.drawRect(px, y + h + o, px + u, y + h + o + u, q);
                gem(c, q, x + w / 2, y + h + o + u / 2, u, gold, Color.WHITE);
                sprite(c, q, ALIEN, x + w * 0.14f - 4 * u, y + h + o + 2 * u, u, ice);
                sprite(c, q, ALIEN2, x + w * 0.86f - 4 * u, y + h + o + 2 * u, u, pink);
                gem(c, q, x - o - u, y + h / 2, u, gold, Color.WHITE);
                gem(c, q, x + w + o + u, y + h / 2, u, gold, Color.WHITE);
            }
        }

        /** Cantoneira em L (n pixels de braço) com um pontinho na diagonal; sx/sy = para onde os braços vão. */
        private void corner(Canvas c, android.graphics.Paint q, float x, float y, int sx, int sy, float u, int n, int color) {
            q.setColor(color);
            for (int i = 0; i < n; i++) {
                cell(c, q, x + sx * i * u, y, u);
                if (i > 0) cell(c, q, x, y + sy * i * u, u);
            }
            cell(c, q, x - sx * 2 * u, y - sy * 2 * u, u);
        }

        /** Losango de 5×5 pixels com o miolo claro, centrado em (x, y). */
        private void gem(Canvas c, android.graphics.Paint q, float x, float y, float u, int color, int core) {
            q.setColor(color);
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -2 + Math.abs(dy); dx <= 2 - Math.abs(dy); dx++) cell(c, q, x + dx * u - u / 2, y + dy * u - u / 2, u);
            q.setColor(core);
            cell(c, q, x - u / 2, y - u / 2, u);
            cell(c, q, x - u / 2 - u, y - u / 2 - u, u);
        }

        private void sprite(Canvas c, android.graphics.Paint q, String[] m, float x, float y, float u, int color) {
            q.setColor(color);
            for (int j = 0; j < m.length; j++)
                for (int i = 0; i < m[j].length(); i++) if (m[j].charAt(i) == '#') cell(c, q, x + i * u, y + j * u, u);
        }

        private void cell(Canvas c, android.graphics.Paint q, float x, float y, float u) {
            c.drawRect(x, y, x + u, y + u, q);
        }

        private void star(Canvas c, float x, float y, float s2) {
            c.drawRect(x - s2 * 3, y - s2 / 2, x + s2 * 3, y + s2 / 2, paint);
            c.drawRect(x - s2 / 2, y - s2 * 3, x + s2 / 2, y + s2 * 3, paint);
        }
    }
}
