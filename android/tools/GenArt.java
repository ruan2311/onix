import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Gera icon.png (192x192) e banner.png (320x180) da identidade Onix:
 * fundo preto e Saturno em traço branco. Uso: java GenArt.java <res-dir>
 * A geometria (grade 48x48) é a mesma do .brand-mark no index.html.
 */
public class GenArt {
    static final Color BG = new Color(0x0a0a0b);

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        ImageIO.write(banner(), "png", new File(res, "drawable-xhdpi/banner.png"));
        ImageIO.write(icon(), "png", new File(res, "drawable-xxxhdpi/icon.png"));
    }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Saturno em linhas: anel traseiro escondido pelo planeta, anel dianteiro corta o contorno. */
    static void saturn(Graphics2D g, float cx, float cy, float size) {
        float k = size / 48f;
        AffineTransform t = new AffineTransform();
        t.translate(cx - 24 * k, cy - 24 * k);
        t.scale(k, k);
        AffineTransform rot = AffineTransform.getRotateInstance(Math.toRadians(-20), 24, 24);

        BasicStroke line = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Shape back = rot.createTransformedShape(new Arc2D.Float(3, 18, 42, 12, 0, 180, Arc2D.OPEN));
        Shape front = rot.createTransformedShape(new Arc2D.Float(3, 18, 42, 12, 180, 180, Arc2D.OPEN));
        Ellipse2D planet = new Ellipse2D.Float(14, 14, 20, 20);

        Area backA = new Area(line.createStrokedShape(back));
        backA.subtract(new Area(new Ellipse2D.Float(12.4f, 12.4f, 23.2f, 23.2f)));
        Area planetA = new Area(line.createStrokedShape(planet));
        planetA.subtract(new Area(new BasicStroke(6f).createStrokedShape(front)));
        Area frontA = new Area(line.createStrokedShape(front));

        Area all = new Area();
        all.add(backA);
        all.add(planetA);
        all.add(frontA);
        g.setColor(Color.WHITE);
        g.fill(all.createTransformedArea(t));
    }

    static BufferedImage banner() {
        int w = 320, h = 180;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        saturn(g, 92, 90, 76);

        Map<TextAttribute, Object> attrs = new HashMap<TextAttribute, Object>();
        attrs.put(TextAttribute.FAMILY, "DejaVu Sans");
        attrs.put(TextAttribute.SIZE, 30f);
        attrs.put(TextAttribute.TRACKING, .32f);
        g.setFont(new Font(attrs));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString("ONIX", 146, 90 + (fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return img;
    }

    static BufferedImage icon() {
        int s = 192;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(img);
        g.setColor(BG);
        g.fill(new RoundRectangle2D.Float(0, 0, s, s, 44, 44));
        saturn(g, 96, 96, 132);
        g.dispose();
        return img;
    }
}
