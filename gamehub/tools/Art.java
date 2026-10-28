import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Gera icon.png (192x192) e banner.png (320x180) do Onix Game Hub: fundo índigo profundo, um
 * controle em traço neon (magenta para ciano) e o anel do Saturno do Onix cruzando o controle.
 * Uso: java Art.java <res-dir>
 */
public class Art {
    static final Color BG1 = new Color(0x1b0f3d), BG2 = new Color(0x07040f);
    static final Color PINK = new Color(0xff3d9a), CYAN = new Color(0x3de1ff);

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        new File(res, "drawable-xxxhdpi").mkdirs();
        new File(res, "drawable-xhdpi").mkdirs();
        ImageIO.write(icon(192), "png", new File(res, "drawable-xxxhdpi/icon.png"));
        ImageIO.write(banner(), "png", new File(res, "drawable-xhdpi/banner.png"));
    }

    static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** O controle com o anel, numa grade 48x48 centrada em (cx, cy). */
    static Area mark(float cx, float cy, float size) {
        float k = size / 48f;
        BasicStroke line = new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Area body = new Area(new RoundRectangle2D.Float(7, 15, 34, 17, 14, 14));
        body.add(new Area(new Ellipse2D.Float(6, 20, 14, 16)));
        body.add(new Area(new Ellipse2D.Float(28, 20, 14, 16)));
        Area outline = new Area(line.createStrokedShape(body));
        // Anel inclinado: abre uma folga no contorno onde ele passa por cima.
        AffineTransform rot = AffineTransform.getRotateInstance(Math.toRadians(-18), 24, 25);
        Shape ring = rot.createTransformedShape(new Ellipse2D.Float(1, 19.5f, 46, 11));
        outline.subtract(new Area(new BasicStroke(5.5f).createStrokedShape(ring)));
        Area front = new Area(line.createStrokedShape(ring));
        // só a metade da frente do anel (a de baixo) aparece por cima do controle
        Area back = new Area(new Rectangle2D.Float(0, 0, 48, 25));
        back.transform(rot);
        Area hidden = new Area(front);
        hidden.intersect(back);
        hidden.intersect(body);
        front.subtract(hidden);
        Area all = new Area(outline);
        all.add(front);
        // direcional
        all.add(new Area(line.createStrokedShape(new Line2D.Float(12.5f, 23.5f, 18.5f, 23.5f))));
        all.add(new Area(line.createStrokedShape(new Line2D.Float(15.5f, 20.5f, 15.5f, 26.5f))));
        // dois botões
        all.add(new Area(new Ellipse2D.Float(30.2f, 22.4f, 3.6f, 3.6f)));
        all.add(new Area(new Ellipse2D.Float(34.6f, 19.2f, 3.6f, 3.6f)));
        AffineTransform t = new AffineTransform();
        t.translate(cx - 24 * k, cy - 24 * k);
        t.scale(k, k);
        return all.createTransformedArea(t);
    }

    static void glow(Graphics2D g, Shape s, float x1, float y1, float x2, float y2) {
        g.setPaint(new GradientPaint(x1, y1, PINK, x2, y2, CYAN));
        Composite old = g.getComposite();
        for (int i = 6; i >= 1; i--) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f));
            g.setStroke(new BasicStroke(i * 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(s);
        }
        g.setComposite(old);
        g.fill(s);
    }

    static BufferedImage icon(int px) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        g.setPaint(new GradientPaint(0, 0, BG1, px, px, BG2));
        g.fill(new RoundRectangle2D.Float(0, 0, px, px, px * 0.36f, px * 0.36f));
        Area m = mark(px / 2f, px / 2f + px * 0.02f, px * 0.86f);
        glow(g, m, px * 0.15f, px * 0.3f, px * 0.85f, px * 0.7f);
        g.dispose();
        return img;
    }

    static BufferedImage banner() {
        int w = 320, h = 180;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        g.setPaint(new GradientPaint(0, 0, BG1, w, h, BG2));
        g.fillRect(0, 0, w, h);
        // grade em perspectiva discreta no chão
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0x3de1ff & 0xffffff | 0x22000000, true));
        for (int i = 0; i < 6; i++) {
            float y = 128 + i * i * 2.2f + i * 4;
            g.draw(new Line2D.Float(0, y, w, y));
        }
        for (int i = -8; i <= 8; i++) g.draw(new Line2D.Float(w / 2f + i * 9, 128, w / 2f + i * 60, h));
        Area m = mark(78, 86, 112);
        glow(g, m, 30, 60, 130, 120);
        g.setPaint(new Color(0xbfb6e6));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString("ONIX", 148, 72);
        g.setPaint(new GradientPaint(148, 0, PINK, 300, 0, CYAN));
        int size = 34;
        Font f;
        do { f = new Font(Font.SANS_SERIF, Font.BOLD, size--); } while (g.getFontMetrics(f).stringWidth("GAME HUB") > w - 160 && size > 12);
        g.setFont(f);
        g.drawString("GAME HUB", 146, 106);
        g.dispose();
        return img;
    }
}
