package tri.novica.gfssystem.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KolacicTest {

    @Test
    void imena() {
        assertEquals("gfs_nastavnik", Kolacici.NASTAVNIK);
        assertEquals("gfs_student", Kolacici.STUDENT);
        assertEquals("gfs_lti", Kolacici.LTI);
        assertEquals("gfs_lti_par", Kolacici.LTI_PAR);
    }

    @Test
    void postaviLaxBezHttpsNemaSecure() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        Kolacici.postavi(req, res, Kolacici.NASTAVNIK, "abc", Duration.ofDays(90), "Lax");
        String h = res.getHeader("Set-Cookie");
        assertTrue(h.startsWith("gfs_nastavnik=abc;"), h);
        assertTrue(h.contains("Path=/"), h);
        assertTrue(h.contains("Max-Age=7776000"), h);
        assertTrue(h.contains("HttpOnly"), h);
        assertTrue(h.contains("SameSite=Lax"), h);
        assertFalse(h.contains("Secure"), h);
    }

    @Test
    void postaviSaHttpsImaSecure() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(true);
        MockHttpServletResponse res = new MockHttpServletResponse();
        Kolacici.postavi(req, res, Kolacici.STUDENT, "xyz", Duration.ofDays(365), "Lax");
        String h = res.getHeader("Set-Cookie");
        assertTrue(h.contains("Max-Age=31536000"), h);
        assertTrue(h.contains("Secure"), h);
        assertTrue(h.contains("HttpOnly"), h);
        assertTrue(h.contains("SameSite=Lax"), h);
    }

    @Test
    void sameSiteNoneJeUvekSecure() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        Kolacici.postavi(req, res, Kolacici.LTI, "t", Duration.ofSeconds(600), "None");
        String h = res.getHeader("Set-Cookie");
        assertTrue(h.contains("Max-Age=600"), h);
        assertTrue(h.contains("Secure"), h);
        assertTrue(h.contains("SameSite=None"), h);
    }

    @Test
    void obrisiPostavljaMaxAgeNulaSaIstimAtributima() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(true);
        MockHttpServletResponse res = new MockHttpServletResponse();
        Kolacici.obrisi(req, res, Kolacici.NASTAVNIK);
        String h = res.getHeader("Set-Cookie");
        assertTrue(h.startsWith("gfs_nastavnik=;"), h);
        assertTrue(h.contains("Max-Age=0"), h);
        assertTrue(h.contains("Path=/"), h);
        assertTrue(h.contains("HttpOnly"), h);
        assertTrue(h.contains("Secure"), h);
        assertTrue(h.contains("SameSite=Lax"), h);
    }

    @Test
    void obrisiLtiKolacicOstajeSameSiteNone() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        Kolacici.obrisi(req, res, Kolacici.LTI);
        String h = res.getHeader("Set-Cookie");
        assertTrue(h.contains("Max-Age=0"), h);
        assertTrue(h.contains("SameSite=None"), h);
        assertTrue(h.contains("Secure"), h);
    }

    @Test
    void neispravanSameSiteBacaIzuzetak() {
        assertThrows(IllegalArgumentException.class, () -> Kolacici.postavi(new MockHttpServletRequest(),
                new MockHttpServletResponse(), "a", "b", Duration.ofSeconds(1), "Bogus"));
    }

    @Test
    void procitaj() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertEquals(Optional.empty(), Kolacici.procitaj(req, Kolacici.NASTAVNIK));
        req.setCookies(new Cookie("drugi", "1"), new Cookie(Kolacici.NASTAVNIK, "vred"), new Cookie("prazan", ""));
        assertEquals(Optional.of("vred"), Kolacici.procitaj(req, Kolacici.NASTAVNIK));
        assertEquals(Optional.empty(), Kolacici.procitaj(req, "prazan"));
        assertEquals(Optional.empty(), Kolacici.procitaj(req, Kolacici.STUDENT));
    }

    /** Podmetnuti kolačić sa sestrinskog poddomena ({@code Domain=.trif.rs}) nosi isto ime: čitaju se sve vrednosti. */
    @Test
    void procitajSveVracaSveNeprazneVrednostiRedom() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertEquals(List.of(), Kolacici.procitajSve(req, Kolacici.STUDENT));
        req.setCookies(new Cookie(Kolacici.STUDENT, "podmetnut"), new Cookie("drugi", "1"),
                new Cookie(Kolacici.STUDENT, ""), new Cookie(Kolacici.STUDENT, "pravi"));
        assertEquals(List.of("podmetnut", "pravi"), Kolacici.procitajSve(req, Kolacici.STUDENT));
        assertEquals(List.of(), Kolacici.procitajSve(req, Kolacici.NASTAVNIK));
    }

    @Test
    void procitajSveImaGornjuGranicu() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        Cookie[] mnogo = new Cookie[50];
        for (int i = 0; i < mnogo.length; i++) mnogo[i] = new Cookie(Kolacici.NASTAVNIK, "v" + i);
        req.setCookies(mnogo);
        List<String> v = Kolacici.procitajSve(req, Kolacici.NASTAVNIK);
        assertEquals(Kolacici.MAX_ISTOIMENIH, v.size());
        assertEquals("v0", v.getFirst());
    }

    @Test
    void brojVrednostiBrojiSveNeprazne() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertEquals(0, Kolacici.brojVrednosti(req, Kolacici.STUDENT));
        Cookie[] mnogo = new Cookie[8];
        for (int i = 0; i < mnogo.length; i++) mnogo[i] = new Cookie(Kolacici.STUDENT, i == 3 ? "" : "v" + i);
        req.setCookies(mnogo);
        assertEquals(7, Kolacici.brojVrednosti(req, Kolacici.STUDENT));
    }
}
