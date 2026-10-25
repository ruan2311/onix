import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Imprime a cor média (#rrggbb) de cada imagem passada; usado pelo gen_seed.py. */
public class AvgColor {
    public static void main(String[] args) throws Exception {
        for (String path : args) {
            BufferedImage src = ImageIO.read(new File(path));
            if (src == null) { System.out.println("-"); continue; }
            BufferedImage one = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = one.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src.getScaledInstance(1, 1, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
            g.dispose();
            System.out.println(String.format("#%06x", one.getRGB(0, 0) & 0xffffff));
        }
    }
}
