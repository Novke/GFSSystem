package tri.novica.gfssystem.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code /auth} nastavnika kroz ceo bezbednosni lanac (MockMvc sa filterima) nad pravom MySQL bazom. Bez
 * {@code @Transactional}: brojač pokušaja piše u {@code REQUIRES_NEW}; svaki test ima svoj IP i briše svoje redove.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthRestIT {

    static final String ORIGIN = PrijavaTestUtil.ORIGIN;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    final List<String> ipovi = new ArrayList<>();

    @AfterEach
    void ocisti() {
        for (String ip : ipovi) {
            jdbc.update("delete from nastavnicke_sesije where ip = ?", ip);
            jdbc.update("delete from pokusaji where kljuc = ?", ip);
        }
    }

    String noviIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String ip = "198.51." + r.nextInt(1, 255) + "." + r.nextInt(1, 255);
        ipovi.add(ip);
        return ip;
    }

    static RequestPostProcessor sa(String ip) {
        return req -> {
            req.setRemoteAddr(ip);
            return req;
        };
    }

    static String telo(String korisnik, String lozinka) {
        return "{\"korisnik\":\"" + korisnik + "\",\"lozinka\":\"" + lozinka + "\"}";
    }

    MockHttpServletRequestBuilder prijava(String korisnik, String lozinka, String ip) {
        return post("/auth/prijava").with(sa(ip)).header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0")
                .contentType(MediaType.APPLICATION_JSON).content(telo(korisnik, lozinka));
    }

    /** Prijava kroz HTTP; vraća {@code gfs_nastavnik=<token>}. */
    String prijaviSe(String ip) throws Exception {
        MockHttpServletResponse r = mvc.perform(prijava(PrijavaTestUtil.KORISNIK, PrijavaTestUtil.LOZINKA, ip))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        String setCookie = r.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        return setCookie.split(";", 2)[0];
    }

    static String kolacicBrisanja(MockHttpServletResponse r) {
        return r.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(Kolacici.NASTAVNIK + "=;") && h.contains("Max-Age=0"))
                .findFirst().orElse(null);
    }

    @Test
    void jaBezKolacicaJe401() throws Exception {
        mvc.perform(get("/auth/ja"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("Potrebna je prijava."))
                .andExpect(jsonPath("$.time").isNotEmpty());
    }

    @Test
    void tacnaPrijavaDajeKolacicIJa() throws Exception {
        String ip = noviIp();
        MockHttpServletResponse r = mvc.perform(prijava(PrijavaTestUtil.KORISNIK, PrijavaTestUtil.LOZINKA, ip))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        List<String> setCookie = r.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(1, setCookie.size(), setCookie.toString());
        String k = setCookie.get(0);
        assertTrue(k.matches("gfs_nastavnik=[A-Za-z0-9_-]{32};.*"), k);
        assertTrue(k.contains("HttpOnly"), k);
        assertTrue(k.contains("SameSite=Lax"), k);
        assertTrue(k.contains("Max-Age=7776000"), k);
        assertTrue(k.contains("Path=/"), k);
        assertFalse(k.contains("Secure"), "http zahtev: bez Secure: " + k);
        assertNull(r.getHeader("Pragma"));

        String kolacic = k.split(";", 2)[0];
        mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uloga").value("NASTAVNIK"))
                .andExpect(jsonPath("$.korisnik").value(PrijavaTestUtil.KORISNIK));
        Integer sesija = jdbc.queryForObject("select count(*) from nastavnicke_sesije where ip = ?", Integer.class, ip);
        assertEquals(1, sesija);
    }

    @Test
    void pogresnaLozinkaIImeSu401() throws Exception {
        String ip = noviIp();
        mvc.perform(prijava(PrijavaTestUtil.KORISNIK, "pogresna-lozinka", ip))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.reason").value("Pogrešno korisničko ime ili lozinka."));
        mvc.perform(prijava("neko-drugi", PrijavaTestUtil.LOZINKA, ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("Pogrešno korisničko ime ili lozinka."));
    }

    @Test
    void praznaPoljaSu400() throws Exception {
        String ip = noviIp();
        mvc.perform(prijava("", "", ip)).andExpect(status().isBadRequest());
    }

    @Test
    void prijavaBezPoreklaJe403() throws Exception {
        mvc.perform(post("/auth/prijava").with(sa(noviIp())).contentType(MediaType.APPLICATION_JSON)
                        .content(telo(PrijavaTestUtil.KORISNIK, PrijavaTestUtil.LOZINKA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("Zahtev nije dozvoljen."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void adminPutanjaTraziKolacic() throws Exception {
        mvc.perform(get("/studenti"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("Potrebna je prijava."));
        String kolacic = prijaviSe(noviIp());
        mvc.perform(get("/studenti").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk());
    }

    @Test
    void upisSaKolacicemABezPoreklaJe403() throws Exception {
        String kolacic = prijaviSe(noviIp());
        String[] d = kolacic.split("=", 2);
        mvc.perform(post("/studenti").cookie(new Cookie(d[0], d[1]))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("Zahtev nije dozvoljen."));
        mvc.perform(post("/studenti").cookie(new Cookie(d[0], d[1])).header("Sec-Fetch-Site", "same-site")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nastavnikNaStudentskojPutanjiJe403() throws Exception {
        String kolacic = prijaviSe(noviIp());
        mvc.perform(get("/public/nalog/ja").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("Nemaš pristup."));
        // bez kolačića: 401
        mvc.perform(get("/public/nalog/ja"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("Potrebna je prijava."));
    }

    @Test
    void odjavaPoništavaKolacic() throws Exception {
        String kolacic = prijaviSe(noviIp());
        MockHttpServletResponse r = mvc.perform(post("/auth/odjava").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertNotNull(kolacicBrisanja(r), "odjava briše kolačić: " + r.getHeaders(HttpHeaders.SET_COOKIE));

        mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/studenti").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nevazeciKolacicJeAnonimanIBriseSe() throws Exception {
        String lazni = Kolacici.NASTAVNIK + "=" + "A".repeat(32);
        MockHttpServletResponse r = mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(lazni)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("Potrebna je prijava."))
                .andReturn().getResponse();
        assertNotNull(kolacicBrisanja(r), r.getHeaders(HttpHeaders.SET_COOKIE).toString());

        // na ADMIN putanji: 401 i brisanje; na javnoj: zahtev prolazi anonimno, kolačić se briše
        r = mvc.perform(get("/studenti").with(PrijavaTestUtil.kaoNastavnik(lazni)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertNotNull(kolacicBrisanja(r));
        r = mvc.perform(get("/public/upis/nepostojeci").with(PrijavaTestUtil.kaoNastavnik("gfs_nastavnik=kratak")))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        assertNotNull(kolacicBrisanja(r));

        // opozvana sesija se ponaša isto
        String kolacic = prijaviSe(noviIp());
        mvc.perform(post("/auth/odjava").with(PrijavaTestUtil.kaoNastavnik(kolacic))).andExpect(status().isNoContent());
        r = mvc.perform(get("/studenti").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertNotNull(kolacicBrisanja(r));
    }

    @Test
    void vazeciKolacicSeNeBriseNiNeObnavljaUSvakomZahtevu() throws Exception {
        String kolacic = prijaviSe(noviIp());
        MockHttpServletResponse r = mvc.perform(get("/studenti").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk()).andReturn().getResponse();
        // tek prijavljena sesija se ne produžava (najviše jednom na 10 min), pa nema ni novog Set-Cookie
        assertTrue(r.getHeaders(HttpHeaders.SET_COOKIE).isEmpty(), r.getHeaders(HttpHeaders.SET_COOKIE).toString());
    }

    @Test
    void sesijeIOpozivJedne() throws Exception {
        String ip = noviIp();
        String a = prijaviSe(ip);
        String b = prijaviSe(ip);
        String odgovor = mvc.perform(get("/auth/sesije").with(PrijavaTestUtil.kaoNastavnik(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.trenutna == true)]", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(odgovor.contains("token"), "spisak sesija ne nosi tokene ni heševe: " + odgovor);
        Long idA = jdbc.queryForObject("select id from nastavnicke_sesije where token_hes = ?", Long.class,
                tri.novica.gfssystem.service.uzivo.TokenHash.od(a.split("=", 2)[1]));
        Long idB = jdbc.queryForObject("select id from nastavnicke_sesije where token_hes = ?", Long.class,
                tri.novica.gfssystem.service.uzivo.TokenHash.od(b.split("=", 2)[1]));
        mvc.perform(get("/auth/sesije").with(PrijavaTestUtil.kaoNastavnik(a)))
                .andExpect(jsonPath("$[?(@.id == " + idA + ")].trenutna").value(contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + idB + ")].trenutna").value(contains(false)));

        // opoziv tuđe sesije (B) iz A: B više ne radi, A radi
        MockHttpServletResponse r = mvc.perform(delete("/auth/sesije/{id}", idB).with(PrijavaTestUtil.kaoNastavnik(a)))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertNull(kolacicBrisanja(r), "opoziv druge sesije ne briše kolačić ovog uređaja");
        mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(b))).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(a))).andExpect(status().isOk());

        // opoziv trenutne: brisanje kolačića
        r = mvc.perform(delete("/auth/sesije/{id}", idA).with(PrijavaTestUtil.kaoNastavnik(a)))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertNotNull(kolacicBrisanja(r));
        mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(a))).andExpect(status().isUnauthorized());

        // nepoznata sesija
        String c = prijaviSe(ip);
        mvc.perform(delete("/auth/sesije/{id}", Long.MAX_VALUE).with(PrijavaTestUtil.kaoNastavnik(c)))
                .andExpect(status().isNotFound());
    }

    @Test
    void odjaviSveOstavljaTrenutnu() throws Exception {
        String ip = noviIp();
        String a = prijaviSe(ip);
        String b = prijaviSe(ip);
        mvc.perform(post("/auth/sesije/odjavi-sve").with(PrijavaTestUtil.kaoNastavnik(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.odjavljeno").value(greaterThanOrEqualTo(1)));
        mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(a))).andExpect(status().isOk());
        mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(b))).andExpect(status().isUnauthorized());
    }

    @Test
    void sesijeBezKolacicaSu401() throws Exception {
        mvc.perform(get("/auth/sesije")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/odjava").header(HttpHeaders.ORIGIN, ORIGIN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/sesije/odjavi-sve").header(HttpHeaders.ORIGIN, ORIGIN)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/auth/sesije/1").header(HttpHeaders.ORIGIN, ORIGIN)).andExpect(status().isUnauthorized());
    }

    /** Prijava nastavnika nije podešena (prazna svojstva): 503, a ADMIN putanje ostaju zaključane (fail-closed). */
    @Nested
    @TestPropertySource(properties = {"gfs.nastavnik.korisnik=", "gfs.nastavnik.lozinka-bcrypt="})
    class Nepodeseno {

        @Autowired MockMvc mvcNepodeseno;
        @Autowired JdbcTemplate jdbcNepodeseno;

        @Test
        void prijavaJe503IAdminOstaje401() throws Exception {
            MockMvc mvc = mvcNepodeseno;
            mvc.perform(prijava(PrijavaTestUtil.KORISNIK, PrijavaTestUtil.LOZINKA, noviIp()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.reason").value("Prijava nastavnika nije podešena."))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
            mvc.perform(get("/studenti")).andExpect(status().isUnauthorized());
            mvc.perform(get("/auth/ja")).andExpect(status().isUnauthorized());
        }

        @Test
        void sesijaIzVremenaPrePraznjenjaSvojstavaNeVazi() throws Exception {
            // važeća sesija (nije opozvana, ističe za 90 dana) napravljena dok je prijava bila podešena
            String token = "B".repeat(32);
            String ip = noviIp();
            jdbcNepodeseno.update("delete from nastavnicke_sesije where token_hes = ?", TokenHash.od(token));
            jdbcNepodeseno.update("insert into nastavnicke_sesije (token_hes, opis, ip, kreirano, poslednji_ulaz, istice)"
                    + " values (?, 'JUnit', ?, now(3), now(3), now(3) + interval 90 day)", TokenHash.od(token), ip);
            String kolacic = Kolacici.NASTAVNIK + "=" + token;
            MockMvc mvc = mvcNepodeseno;
            MockHttpServletResponse r = mvc.perform(get("/studenti").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.reason").value("Potrebna je prijava."))
                    .andReturn().getResponse();
            assertNotNull(kolacicBrisanja(r), "kolačić se briše: " + r.getHeaders(HttpHeaders.SET_COOKIE));
            r = mvc.perform(get("/auth/ja").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.korisnik").doesNotExist())
                    .andReturn().getResponse();
            assertNotNull(kolacicBrisanja(r));
            mvc.perform(get("/auth/sesije").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
