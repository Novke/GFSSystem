package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpisUredjajaTest {

    @Test
    void androidChrome() {
        assertEquals("Android · Chrome", OpisUredjaja.iz("Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"));
    }

    @Test
    void windowsEdge() {
        assertEquals("Windows · Edge", OpisUredjaja.iz("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"));
    }

    @Test
    void iphoneSafari() {
        assertEquals("iPhone · Safari", OpisUredjaja.iz("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void linuxFirefox() {
        assertEquals("Linux · Firefox", OpisUredjaja.iz("Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"));
    }

    @Test
    void macSafariIOpera() {
        assertEquals("macOS · Safari", OpisUredjaja.iz("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.4 Safari/605.1.15"));
        assertEquals("Windows · Opera", OpisUredjaja.iz("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/115.0.0.0"));
    }

    @Test
    void chromeNaIosu() {
        assertEquals("iPhone · Chrome", OpisUredjaja.iz("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0.6723.90 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void praznoINepoznato() {
        assertEquals("Nepoznat uređaj", OpisUredjaja.iz(null));
        assertEquals("Nepoznat uređaj", OpisUredjaja.iz(""));
        assertEquals("Nepoznat uređaj", OpisUredjaja.iz("   "));
        assertEquals("Nepoznat uređaj", OpisUredjaja.iz("curl/8.5.0"));
    }

    @Test
    void samoJednoPoznato() {
        assertEquals("Linux", OpisUredjaja.iz("Mozilla/5.0 (X11; Linux x86_64)"));
        assertEquals("Firefox", OpisUredjaja.iz("Firefox/131.0"));
    }

    @Test
    void neBeleziVerzijeINeProlaziStoDvadeset() {
        String ua = "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0.0.0 " + "x".repeat(5000);
        String opis = OpisUredjaja.iz(ua);
        assertTrue(opis.length() <= 120);
        assertEquals("Windows · Chrome", opis);
    }
}
