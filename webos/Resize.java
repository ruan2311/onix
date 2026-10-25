import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Ícones da LG (80×80 e 130×130) a partir do ícone do Android: java webos/Resize.java ENTRADA SAÍDA LADO */
public class Resize {
    public static void main(String[] a) throws Exception {
        BufferedImage in = ImageIO.read(new File(a[0]));
        int n = Integer.parseInt(a[2]);
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(in, 0, 0, n, n, null);
        g.dispose();
        ImageIO.write(out, "png", new File(a[1]));
    }
}
