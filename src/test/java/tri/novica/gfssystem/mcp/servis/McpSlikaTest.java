package tri.novica.gfssystem.mcp.servis;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link McpSlika#spoji}: zadatak gore, rešenje dole, siva linija i razmak između, bela pozadina, duža ivica najviše
 * {@link McpSlika#MAX_IVICA} (smanjuje, nikad ne uvećava), nečitljiv ulaz daje {@code null}.
 */
class McpSlikaTest {

    static final Color ZADATAK = new Color(200, 0, 0);
    static final Color RESENJE = new Color(0, 0, 200);
    static final byte[] PNG_POTPIS = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    static byte[] png(int sirina, int visina, Color boja) {
        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = slika.createGraphics();
        g.setColor(boja);
        g.fillRect(0, 0, sirina, visina);
        g.dispose();
        return upisi(slika);
    }

    static byte[] upisi(BufferedImage slika) {
        ByteArrayOutputStream izlaz = new ByteArrayOutputStream();
        try {
            assertTrue(ImageIO.write(slika, "png", izlaz));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return izlaz.toByteArray();
    }

    static BufferedImage procitaj(byte[] png) {
        assertNotNull(png);
        assertArrayEquals(PNG_POTPIS, Arrays.copyOf(png, PNG_POTPIS.length), "rezultat nije PNG");
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static Color boja(BufferedImage slika, int x, int y) {
        return new Color(slika.getRGB(x, y), false);
    }

    /** Visina spojene slike pre skaliranja. */
    static int prirodnaVisina(int gore, int dole) {
        return gore + 2 * McpSlika.RAZMAK + McpSlika.LINIJA + dole;
    }

    static void odnosOcuvan(BufferedImage slika, int prirodnaSirina, int prirodnaVisina) {
        double ocekivanaVisina = (double) slika.getWidth() * prirodnaVisina / prirodnaSirina;
        assertTrue(Math.abs(slika.getHeight() - ocekivanaVisina) <= 1.0,
                "odnos stranica: " + slika.getWidth() + "x" + slika.getHeight() + " za " + prirodnaSirina + "x" + prirodnaVisina);
    }

    @Test
    void zadatakIResenjeJednoIspodDrugogSaLinijomURazmaku() {
        BufferedImage slika = procitaj(McpSlika.spoji(png(1400, 300, ZADATAK), png(1400, 900, RESENJE)));

        assertTrue(slika.getWidth() <= McpSlika.MAX_IVICA);
        assertTrue(slika.getHeight() <= McpSlika.MAX_IVICA);
        odnosOcuvan(slika, 1400, prirodnaVisina(300, 900));
        // ispod granice: bez skaliranja, pikseli tačno na mestu
        assertEquals(1400, slika.getWidth());
        assertEquals(prirodnaVisina(300, 900), slika.getHeight());
        assertEquals(ZADATAK, boja(slika, 700, 150));
        assertEquals(Color.WHITE, boja(slika, 700, 300 + McpSlika.RAZMAK / 2));
        assertEquals(McpSlika.BOJA_LINIJE, boja(slika, 700, 300 + McpSlika.RAZMAK));
        assertEquals(McpSlika.BOJA_LINIJE, boja(slika, 700, 300 + McpSlika.RAZMAK + McpSlika.LINIJA - 1));
        assertEquals(Color.WHITE, boja(slika, 700, 300 + McpSlika.RAZMAK + McpSlika.LINIJA + McpSlika.RAZMAK / 2));
        assertEquals(RESENJE, boja(slika, 700, slika.getHeight() - 1));
        assertEquals(new Color(0xBD, 0xBD, 0xBD), McpSlika.BOJA_LINIJE);
    }

    @Test
    void visokSpojSeSmanjujeDoMaksimumaUzOcuvanOdnos() {
        BufferedImage slika = procitaj(McpSlika.spoji(png(1400, 1000, ZADATAK), png(1400, 900, RESENJE)));

        assertEquals(McpSlika.MAX_IVICA, slika.getHeight());
        assertTrue(slika.getWidth() < 1400);
        odnosOcuvan(slika, 1400, prirodnaVisina(1000, 900));
    }

    @Test
    void uziDeoJeLevoNaBelojPozadini() {
        BufferedImage slika = procitaj(McpSlika.spoji(png(1400, 300, ZADATAK), png(600, 200, RESENJE)));

        assertEquals(1400, slika.getWidth());
        assertEquals(prirodnaVisina(300, 200), slika.getHeight());
        assertEquals(RESENJE, boja(slika, 300, slika.getHeight() - 100));
        assertEquals(Color.WHITE, boja(slika, 1000, slika.getHeight() - 100));
    }

    @Test
    void samoZadatakSeNeUvecava() {
        BufferedImage slika = procitaj(McpSlika.spoji(png(600, 200, ZADATAK), null));

        assertEquals(600, slika.getWidth());
        assertEquals(200, slika.getHeight());
        assertEquals(ZADATAK, boja(slika, 300, 100));
    }

    @Test
    void samoZadatakSeSmanjujeKaoISpoj() {
        BufferedImage slika = procitaj(McpSlika.spoji(png(2000, 500, ZADATAK), null));

        assertEquals(McpSlika.MAX_IVICA, slika.getWidth());
        odnosOcuvan(slika, 2000, 500);
    }

    @Test
    void providnaPozadinaPostajeBela() {
        BufferedImage providna = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage slika = procitaj(McpSlika.spoji(upisi(providna), null));

        assertEquals(Color.WHITE, boja(slika, 50, 25));
    }

    @Test
    void necitljivPngJeNull() {
        byte[] nijePng = "nije png".getBytes(StandardCharsets.US_ASCII);
        byte[] dobar = png(10, 10, ZADATAK);

        assertNull(McpSlika.spoji(nijePng, null));
        assertNull(McpSlika.spoji(null, null));
        assertNull(McpSlika.spoji(new byte[0], null));
        // potpis PNG-a bez ispravnog sadržaja
        assertNull(McpSlika.spoji(Arrays.copyOf(dobar, 20), null));
        assertNull(McpSlika.spoji(dobar, nijePng));
    }
}
