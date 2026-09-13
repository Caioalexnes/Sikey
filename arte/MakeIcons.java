import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Gera os PNG de lançador a partir da arte original. */
public class MakeIcons {

    // Legado (todo o ícone) e primeiro plano adaptativo (canvas de 108dp).
    private static final int[][] LEGACY = {{48, 0}, {72, 1}, {96, 2}, {144, 3}, {192, 4}};
    private static final int[] FOREGROUND = {108, 162, 216, 324, 432};
    private static final String[] DENSITIES =
            {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        File res = new File(args[1]);

        BufferedImage art = trim(src);
        System.out.println("arte recortada: " + art.getWidth() + "x" + art.getHeight());

        for (int i = 0; i < DENSITIES.length; i++) {
            File dir = new File(res, "mipmap-" + DENSITIES[i]);
            dir.mkdirs();

            // Ícone legado: a arte ocupa quase todo o quadrado.
            int legacy = LEGACY[i][0];
            write(fit(art, legacy, 0.92), new File(dir, "ic_launcher.png"));

            // Primeiro plano adaptativo: a arte tem que caber na zona segura de
            // 66dp dentro dos 108dp do canvas, senão a máscara do lançador corta.
            int fg = FOREGROUND[i];
            write(fit(art, fg, 66.0 / 108.0), new File(dir, "ic_launcher_foreground.png"));
        }
        System.out.println("pronto");
    }

    /** Remove as bordas transparentes. */
    private static BufferedImage trim(BufferedImage src) {
        int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                if ((src.getRGB(x, y) >>> 24) >= 8) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Arte centrada num quadrado de lado `size`, ocupando `ratio` dele. */
    private static BufferedImage fit(BufferedImage art, int size, double ratio) {
        double target = size * ratio;
        double scale = Math.min(target / art.getWidth(), target / art.getHeight());
        int w = Math.max(1, (int) Math.round(art.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(art.getHeight() * scale));

        BufferedImage scaled = downscale(art, w, h);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(scaled, (size - w) / 2, (size - h) / 2, null);
        g.dispose();
        return out;
    }

    /**
     * Reduz pela metade de cada vez até chegar perto do alvo.
     * Reduzir 1024 para 48 num passo só borra os traços finos do desenho.
     */
    private static BufferedImage downscale(BufferedImage src, int w, int h) {
        BufferedImage current = src;
        int cw = src.getWidth(), ch = src.getHeight();
        while (cw / 2 > w && ch / 2 > h) {
            cw /= 2;
            ch /= 2;
            current = resize(current, cw, ch);
        }
        return resize(current, w, h);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static void write(BufferedImage image, File file) throws Exception {
        ImageIO.write(image, "png", file);
        System.out.println("  " + file.getParentFile().getName() + "/" + file.getName()
                + "  " + image.getWidth() + "x" + image.getHeight());
    }
}
