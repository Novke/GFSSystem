package tri.novica.gfssystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Provera porekla (spec 2.4) bez Spring konteksta: sve kombinacije {@code Sec-Fetch-Site} / {@code Origin}. */
class ProveraPoreklaFilterTest {

    static final String DOZVOLJEN = "http://localhost:4200";

    final ProveraPoreklaFilter filter = new ProveraPoreklaFilter(
            List.of(DOZVOLJEN, "http://127.0.0.1:4200"), ProveraPoreklaFilter.LTI_IZUZECI);

    record Ishod(boolean prosao, MockHttpServletResponse odgovor) {
    }

    Ishod pusti(String metod, String putanja, String secFetchSite, String origin) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metod, putanja);
        if (secFetchSite != null) req.addHeader("Sec-Fetch-Site", secFetchSite);
        if (origin != null) req.addHeader("Origin", origin);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain lanac = new MockFilterChain();
        filter.doFilter(req, res, lanac);
        return new Ishod(lanac.getRequest() != null, res);
    }

    static void odbijen(Ishod i) throws Exception {
        assertFalse(i.prosao(), "zahtev nije smeo proći");
        assertEquals(403, i.odgovor().getStatus());
        assertTrue(i.odgovor().getContentType().startsWith("application/json"));
        String telo = i.odgovor().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(telo.contains("\"reason\":\"Zahtev nije dozvoljen.\""), telo);
        assertTrue(telo.contains("\"time\":"), telo);
    }

    @Test
    void getBezZaglavljaProlazi() throws Exception {
        assertTrue(pusti("GET", "/studenti", null, null).prosao());
        assertTrue(pusti("HEAD", "/studenti", "cross-site", "https://cvgen.trif.rs").prosao());
        assertTrue(pusti("OPTIONS", "/studenti", null, null).prosao());
    }

    @Test
    void secFetchSiteSameOriginINoneProlaze() throws Exception {
        assertTrue(pusti("POST", "/studenti", "same-origin", null).prosao());
        assertTrue(pusti("PUT", "/studenti/1", "none", null).prosao());
        // Sec-Fetch-Site odlučuje i kad Origin nije na listi (isti origin iza nginx-a)
        assertTrue(pusti("DELETE", "/beleske/1", "same-origin", "https://gfs.trif.rs").prosao());
    }

    @Test
    void secFetchSiteSameSiteICrossSiteSuOdbijeni() throws Exception {
        odbijen(pusti("POST", "/studenti", "same-site", null));
        odbijen(pusti("PATCH", "/test/1/prag-prolaza", "cross-site", null));
        // ni sa dozvoljenim Origin-om: Sec-Fetch-Site ima prednost
        odbijen(pusti("POST", "/studenti", "same-site", DOZVOLJEN));
        odbijen(pusti("DELETE", "/beleske/1", "cross-site", DOZVOLJEN));
    }

    @Test
    void bezSecFetchSiteOdlucujeOrigin() throws Exception {
        assertTrue(pusti("POST", "/studenti", null, DOZVOLJEN).prosao());
        assertTrue(pusti("PUT", "/studenti/1", null, "http://127.0.0.1:4200").prosao());
        odbijen(pusti("POST", "/studenti", null, "https://cvgen.trif.rs"));
        odbijen(pusti("POST", "/studenti", null, "null"));
        // origin se poredi tačno (bez prefiksa, bez kose crte na kraju)
        odbijen(pusti("POST", "/studenti", null, DOZVOLJEN + ".evil.example"));
        odbijen(pusti("POST", "/studenti", null, DOZVOLJEN + "/"));
    }

    @Test
    void bezObaZaglavljaJeOdbijeno() throws Exception {
        odbijen(pusti("POST", "/studenti", null, null));
        odbijen(pusti("DELETE", "/auth/sesije/1", null, null));
        odbijen(pusti("POST", "/public/upis/abc", null, null));
        odbijen(pusti("POST", "/auth/prijava", null, null));
    }

    @Test
    void ltiPrijavaIStartSuIzuzeti() throws Exception {
        assertTrue(pusti("POST", "/public/lti/start", null, null).prosao());
        assertTrue(pusti("POST", "/public/lti/prijava", "cross-site", "https://moodle.example.com").prosao());
        // samo te dve putanje: povezivanje i ostalo pod /public/lti nisu izuzeti
        odbijen(pusti("POST", "/public/lti/povezi", null, null));
        odbijen(pusti("POST", "/public/lti/start/x", null, null));
    }
}
