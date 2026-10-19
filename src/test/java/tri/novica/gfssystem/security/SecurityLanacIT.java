package tri.novica.gfssystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.uzivo.MedijService;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bezbednosni lanac kao celina (spec 2.1, 2.3): firewall (400 JSON), javne putanje bez kolačića, MCP lanac, CORS
 * preflight pre pravila pristupa, bez Spring header writer-a (dugi keš {@code /public/mediji} ostaje) i bez sesije.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityLanacIT {

    static final String ORIGIN = PrijavaTestUtil.ORIGIN;

    @Autowired MockMvc mvc;
    @Autowired MedijService medijService;
    @Autowired NastavnikSesijaServis nastavnici;

    /** Sirov URI (MockMvc inače parsira {@code //x} kao host, a {@code %} kodira još jednom). */
    static RequestPostProcessor uri(String sirov) {
        return req -> {
            req.setRequestURI(sirov);
            req.setServletPath("");
            req.setPathInfo(null);
            return req;
        };
    }

    @Test
    void javnaPutanjaBezKolacicaNijeOdbijenaULancu() throws Exception {
        mvc.perform(get("/public/upis/nepostojeci"))
                .andExpect(status().is(not(401)))
                .andExpect(status().is(not(403)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminBezKolacicaJe401JsonBezSesije() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/studenti"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.reason").value("Potrebna je prijava."))
                .andExpect(jsonPath("$.time").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn().getResponse();
        assertNull(r.getHeader("X-Frame-Options"));
        // nepostojeća putanja je takođe ADMIN: 401, ne 404 (ne otkriva se šta postoji)
        mvc.perform(get("/ne-postoji/nigde")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ws")).andExpect(status().isUnauthorized());
    }

    @Test
    void firewallOdbijaSaJsonom400() throws Exception {
        for (String sirov : new String[]{"/studenti;x=1", "//studenti", "/public/..%2fstudenti",
                "/public/../studenti", "/public/upis/x/..;/studenti", "/studenti%2F1", "/studenti\\1"}) {
            mvc.perform(get("/").with(uri(sirov)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.reason").value("Neispravan zahtev."))
                    .andExpect(jsonPath("$.time").isNotEmpty());
        }
    }

    @Test
    void mcpLanacNemaEndpointa() throws Exception {
        // lanac MCP-a postoji (permitAll, bez provere porekla), endpoint stiže sa MCP granom: ovde nije 200
        mvc.perform(post("/mcp")).andExpect(status().is(not(200))).andExpect(status().is(not(401)))
                .andExpect(status().is(not(403)));
        mvc.perform(get("/mcp/slike/1")).andExpect(status().is(not(200)));
    }

    @Test
    void mediiZadrzavajuSvojCacheControl() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
        MedijInfo m = medijService.sacuvaj(new MockMultipartFile("fajl", "lanac.png", "image/png", png));
        try {
            MockHttpServletResponse r = mvc.perform(get("/public/mediji/{id}", m.id()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andReturn().getResponse();
            assertNull(r.getHeader("Pragma"), "Spring Security header writer-i su isključeni");
            assertNull(r.getHeader(HttpHeaders.EXPIRES));
            assertNull(r.getHeader("X-Frame-Options"));
            assertEquals(1, r.getHeaders(HttpHeaders.CACHE_CONTROL).size());
        } finally {
            medijService.obrisi(m.id());
        }
    }

    @Test
    void corsPreflightNaAdminPutanjuBezKolacica() throws Exception {
        MockHttpServletResponse r = mvc.perform(options("/studenti")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andReturn().getResponse();
        String metode = r.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        assertNotNull(metode);
        for (String m : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"}) {
            assertTrue(metode.contains(m), metode);
        }

        // nedozvoljen origin: CORS ga odbija (403), ne pravilo pristupa
        mvc.perform(options("/studenti")
                        .header(HttpHeaders.ORIGIN, "https://cvgen.trif.rs")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void corsOdgovorNaStvarniZahtevSaKolacicem() throws Exception {
        String kolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        mvc.perform(get("/studenti").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        // stvaran zahtev sa tuđim originom: 403 od CORS-a i sa važećim kolačićem
        String[] d = kolacic.split("=", 2);
        mvc.perform(get("/studenti").cookie(new jakarta.servlet.http.Cookie(d[0], d[1]))
                        .header(HttpHeaders.ORIGIN, "https://cvgen.trif.rs"))
                .andExpect(status().isForbidden());
    }
}
