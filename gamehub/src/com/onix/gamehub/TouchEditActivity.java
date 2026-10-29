package com.onix.gamehub;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Configurações → Controles → Controles na tela (celular): o mesmo editor do menu do jogo, sem
 * jogo aberto. No lugar do jogo, um quadro; embaixo do quadro, os consoles (o esquema de botões
 * de cada um) para ver como fica. Gire o celular para ajustar o jeito deitado.
 */
public class TouchEditActivity extends Activity {

    private static final String[] CONSOLES = {"nes", "snes", "gba", "md"};
    private static final String[] NAMES = {"NES", "SNES", "GBA", "Mega Drive"};
    private static final int[] COLORS = {0xFFFF5A6E, 0xFFA78BFA, 0xFF38BDF8, 0xFFFBBF24};

    private FrameLayout root;
    private TextView screen;
    private TouchPad pad;
    private int console = 1;
    private boolean es;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        es = "es".equals(getIntent().getStringExtra("lang"));
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        screen = new TextView(this);
        screen.setGravity(Gravity.CENTER);
        screen.setTextColor(0xFFA99FD0);
        screen.setTextSize(15);
        screen.setText(es ? "Toca un console para ver sus botones" : "Toque num console para ver os botões dele");
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) arrange();
            }
        });
        showPad();
    }

    private void showPad() {
        if (pad != null) root.removeView(pad);
        pad = new TouchPad(this, CONSOLES[console], new TouchPad.Listener() {
            @Override
            public void padButtons(int bits) {
            }

            @Override
            public void padFastForward(boolean on) {
            }

            @Override
            public void padMenu() {
            }

            @Override
            public void padEditDone() {
                finish();
            }
        });
        pad.setSpanish(es);
        root.addView(pad, new FrameLayout.LayoutParams(-1, -1));
        pad.setEditing(true);
        arrange();
    }

    /** Como no jogo: em pé, o quadro do jogo em cima e o controle embaixo; deitado, um por cima do outro. */
    private void arrange() {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || pad == null) return;
        FrameLayout.LayoutParams g = (FrameLayout.LayoutParams) screen.getLayoutParams();
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) pad.getLayoutParams();
        if (h > w) {
            int gh = Math.min(w * 3 / 4, h / 2), top = Math.max(0, (h / 2 - gh) / 3);
            g.height = gh;
            g.topMargin = top;
            p.height = h - gh - top;
            p.topMargin = gh + top;
        } else {
            g.height = -1;
            g.topMargin = 0;
            p.height = -1;
            p.topMargin = 0;
        }
        g.gravity = Gravity.TOP;
        screen.setLayoutParams(g);
        pad.setLayoutParams(p);
        screen.setBackground(frame());
        screen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                console = (console + 1) % CONSOLES.length;
                screen.setText(NAMES[console] + "\n\n" + (es ? "Toca aquí para cambiar de consola" : "Toque aqui para trocar de console"));
                showPad();
            }
        });
        screen.setText(NAMES[console] + "\n\n" + (es ? "Toca aquí para cambiar de consola" : "Toque aqui para trocar de console"));
        screen.setTextColor(COLORS[console]);
    }

    /** O quadro onde fica o jogo: fundo escuro com borda fina na cor do console. */
    private GradientDrawable frame() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF0E0820);
        d.setStroke(Math.round(2 * getResources().getDisplayMetrics().density), (COLORS[console] & 0x00FFFFFF) | 0x66000000);
        return d;
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
