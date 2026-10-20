package tri.novica.gfssystem.mcp.servis;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Jedna PNG slika za Kloda (spec MCP 4.1, 6.4): zadatak gore, rešenje dole, između njih siva linija ({@value #LINIJA} px)
 * sa {@value #RAZMAK} px razmaka iznad i ispod, poravnato levo na beloj pozadini. Cela slika se smanjuje (nikad ne
 * uvećava) tako da duža ivica bude najviše {@value #MAX_IVICA} px, koliko Claude vidi bez sopstvenog smanjivanja. PNG
 * ulazi su iz {@code gfs-latex} (duža ivica 1400 px); slika se ne čuva. Čitanje i pisanje idu kroz memoriju, bez
 * privremenih fajlova ({@code ImageIO} sa {@code OutputStream}-om ih inače pravi u {@code java.io.tmpdir}).
 */
public final class McpSlika {

    public static final int MAX_IVICA = 1568;
    static final int RAZMAK = 16;
    static final int LINIJA = 2;
    static final Color BOJA_LINIJE = new Color(0xBD, 0xBD, 0xBD);
    /** Veći ulaz od ovoga nije iz {@code gfs-latex}-a: ne dekodira se (zaštita memorije). */
    private static final int NAJVECA_ULAZNA_IVICA = 8_000;
    private static final byte[] POTPIS_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private McpSlika() {
    }

    /**
     * Spaja zadatak i rešenje u jedan PNG; {@code resenje == null} daje samo zadatak, isto skaliran.
     *
     * @return PNG bajtovi, ili {@code null} kad bilo koji ulaz nije čitljiv PNG ({@code zadatak == null} takođe)
     */
    public static byte[] spoji(byte[] zadatak, byte[] resenje) {
        BufferedImage gore = procitaj(zadatak);
        if (gore == null) return null;
        BufferedImage dole = null;
        if (resenje != null) {
            dole = procitaj(resenje);
            if (dole == null) return null;
        }
        int sirina = dole == null ? gore.getWidth() : Math.max(gore.getWidth(), dole.getWidth());
        int visina = dole == null ? gore.getHeight() : gore.getHeight() + 2 * RAZMAK + LINIJA + dole.getHeight();
        double faktor = Math.min(1.0, (double) MAX_IVICA / Math.max(sirina, visina));
        int w = Math.clamp(Math.round(sirina * faktor), 1, MAX_IVICA);
        int h = Math.clamp(Math.round(visina * faktor), 1, MAX_IVICA);

        BufferedImage slika = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = slika.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            if (faktor < 1.0) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.scale(faktor, faktor);
            }
            g.drawImage(gore, 0, 0, null);
            if (dole != null) {
                g.setColor(BOJA_LINIJE);
                g.fillRect(0, gore.getHeight() + RAZMAK, sirina, LINIJA);
                g.drawImage(dole, 0, gore.getHeight() + 2 * RAZMAK + LINIJA, null);
            }
        } finally {
            g.dispose();
        }
        return upisi(slika);
    }

    /** PNG -> slika; {@code null} za sve što nije PNG ili se ne čita, i za prevelike dimenzije (pre dekodiranja). */
    private static BufferedImage procitaj(byte[] png) {
        if (png == null || png.length < POTPIS_PNG.length
                || !Arrays.equals(POTPIS_PNG, Arrays.copyOf(png, POTPIS_PNG.length))) {
            return null;
        }
        Iterator<ImageReader> citaci = ImageIO.getImageReadersByFormatName("png");
        if (!citaci.hasNext()) return null;
        ImageReader citac = citaci.next();
        try (ImageInputStream ulaz = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            citac.setInput(ulaz, true, true);
            if (citac.getWidth(0) > NAJVECA_ULAZNA_IVICA || citac.getHeight(0) > NAJVECA_ULAZNA_IVICA) {
                return null;
            }
            return citac.read(0);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            citac.dispose();
        }
    }

    private static byte[] upisi(BufferedImage slika) {
        ByteArrayOutputStream izlaz = new ByteArrayOutputStream();
        try (ImageOutputStream png = new MemoryCacheImageOutputStream(izlaz)) {
            if (!ImageIO.write(slika, "png", png)) return null;
        } catch (IOException e) {
            return null;
        }
        return izlaz.toByteArray();
    }
}
