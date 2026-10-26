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
 * Com "controle" (java GenArt.java <res-dir> controle), gera o ícone do Onix Controle:
 * um controle remoto em traço branco com o Saturno no lugar do direcional, em versão comum
 * e adaptativa (Android 8+, que recorta o ícone no formato do celular).
 */
public class GenArt {
    static final Color BG = new Color(0x0a0a0b);

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        if (args.length > 1 && args[1].equals("controle")) {
            controle(res);
            return;
        }
        ImageIO.write(banner(), "png", new File(res, "drawable-xhdpi/banner.png"));
        ImageIO.write(icon(), "png", new File(res, "drawable-xxxhdpi/icon.png"));
    }

    /** Controle remoto (grade 48x48): corpo arredondado, botão de ligar, Saturno como direcional e o volume. */
    static Area remote(float cx, float cy, float size) {
        float k = size / 48f;
        AffineTransform t = new AffineTransform();
        t.translate(cx - 24 * k, cy - 24 * k);
        t.scale(k, k);
        BasicStroke line = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        Area body = new Area(line.createStrokedShape(new RoundRectangle2D.Float(13, 3, 22, 42, 14, 14)));
        // O anel do Saturno passa por cima do corpo: abre uma folga no contorno, como no planeta.
        AffineTransform rot = AffineTransform.getRotateInstance(Math.toRadians(-20), 24, 21);
        Shape ring = rot.createTransformedShape(new Ellipse2D.Float(24 - 14.5f, 21 - 4.2f, 29, 8.4f));
        body.subtract(new Area(new BasicStroke(6f).createStrokedShape(ring)));

        Area all = new Area(body);
        Area mark = new Area();
        saturnArea(mark, 24, 21, 33);
        all.add(mark);
        all.add(new Area(new Ellipse2D.Float(22.3f, 7.3f, 3.4f, 3.4f)));   // ligar
        all.add(new Area(line.createStrokedShape(new Line2D.Float(20.5f, 34, 27.5f, 34))));   // volume
        all.add(new Area(line.createStrokedShape(new Line2D.Float(20.5f, 39, 27.5f, 39))));
        return all.createTransformedArea(t);
    }

    static void controle(File res) throws Exception {
        new File(res, "mipmap-xxxhdpi").mkdirs();
        new File(res, "mipmap-anydpi-v26").mkdirs();
        new File(res, "values").mkdirs();
        // Ícone comum (192 px): fundo preto arredondado, como o do Onix.
        BufferedImage icon = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(icon);
        g.setColor(BG);
        g.fill(new RoundRectangle2D.Float(0, 0, 192, 192, 44, 44));
        g.setColor(Color.WHITE);
        g.fill(remote(96, 96, 150));
        g.dispose();
        ImageIO.write(icon, "png", new File(res, "mipmap-xxxhdpi/icon.png"));
        // Frente do ícone adaptativo (108 dp = 432 px; o celular mostra no mínimo os 66 dp do meio).
        BufferedImage fg = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        g = g(fg);
        g.setColor(Color.WHITE);
        g.fill(remote(216, 216, 250));
        g.dispose();
        ImageIO.write(fg, "png", new File(res, "mipmap-xxxhdpi/icon_fg.png"));
        write(new File(res, "values/colors.xml"), "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"
                + "    <color name=\"icon_bg\">#0a0a0b</color>\n</resources>\n");
        write(new File(res, "mipmap-anydpi-v26/icon.xml"), "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <background android:drawable=\"@color/icon_bg\" />\n"
                + "    <foreground android:drawable=\"@mipmap/icon_fg\" />\n</adaptive-icon>\n");
    }

    static void write(File f, String s) throws Exception {
        java.nio.file.Files.write(f.toPath(), s.getBytes("UTF-8"));
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
        Area all = new Area();
        saturnArea(all, cx, cy, size);
        g.setColor(Color.WHITE);
        g.fill(all);
    }

    static void saturnArea(Area out, float cx, float cy, float size) {
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
        out.add(all.createTransformedArea(t));
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
