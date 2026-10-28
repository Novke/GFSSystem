package tri.novica.gfssystem.rest.cas;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.cas.CekiranjeService;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;

import java.time.Clock;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /public/nalog/cas} i {@code /predavanja/{id}/cekiranje} kroz ceo bezbednosni lanac (MockMvc sa filterima) nad
 * pravom bazom: kolačić {@code gfs_uredjaj}, klase pristupa, provera porekla, nastavnička sesija na skeniranju i da
 * odgovori ne nose tajnu ni heševe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CasPublicRestIT {

    @Autowired MockMvc mvc;
    @Autowired CekiranjeService cekiranje;
    @Autowired StudentskaPrijava studentskaPrijava;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    CasPodaci d;
    Long predavanje;
    Long ana;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        Long grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now(clock));
        ana = d.student(grupa, "Ana");
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    Cookie studentskiKolacic(Long studentId) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        studentskaPrijava.prijavi(studentId, NacinPrijave.KOD, new MockHttpServletRequest(), res);
        String h = res.getHeader("Set-Cookie");
        assertNotNull(h);
        return new Cookie(Kolacici.STUDENT, h.substring(h.indexOf('=') + 1, h.indexOf(';')));
    }

    String telo(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    @Test
    void skeniranjeBezPrijaveVracaKljucIPostavljaUredjaj() throws Exception {
        MvcResult r = mvc.perform(post("/public/nalog/cas/skeniraj").header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content(telo(cekiranje.zaNastavnika(predavanje).token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kljuc").isString())
                .andExpect(jsonPath("$.stanje").doesNotExist())
                .andReturn();
        String kolacic = r.getResponse().getHeader("Set-Cookie");
        assertNotNull(kolacic);
        assertTrue(kolacic.startsWith("gfs_uredjaj="), kolacic);
        assertTrue(kolacic.contains("HttpOnly"), kolacic);
        assertTrue(kolacic.contains("SameSite=Lax"), kolacic);
        assertTrue(kolacic.contains("Max-Age=31536000"), kolacic);
        assertTrue(kolacic.contains("Path=/"), kolacic);
    }

    @Test
    void skeniranjeSaPrijavomVracaStanjeIPamtiUredjaj() throws Exception {
        Cookie student = studentskiKolacic(ana);
        Cookie uredjaj = new Cookie(Kolacici.UREDJAJ, "abcdefghjkmnpqrstuvwxyzABCDEFGHJ");
        MvcResult r = mvc.perform(post("/public/nalog/cas/skeniraj").header("Sec-Fetch-Site", "same-origin")
                        .cookie(student, uredjaj)
                        .contentType(MediaType.APPLICATION_JSON).content(telo(cekiranje.zaNastavnika(predavanje).token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kljuc").doesNotExist())
                .andExpect(jsonPath("$.stanje.predavanje.id").value(predavanje))
                .andReturn();
        assertNull(r.getResponse().getHeader("Set-Cookie"), "postojeći kolačić uređaja se ne menja");
        assertEquals(64, jdbc.queryForObject("select char_length(uredjaj_hash) from cekiranja where predavanje_id = ?",
                Integer.class, predavanje));
        mvc.perform(get("/public/nalog/cas").cookie(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predavanje.id").value(predavanje))
                .andExpect(jsonPath("$.izvodjenje").doesNotExist());
    }

    @Test
    void potvrdaKljucaPoslePrijaveSamoSaIstogTelefona() throws Exception {
        Cookie telefon = new Cookie(Kolacici.UREDJAJ, "abcdefghjkmnpqrstuvwxyzABCDEFGHJ");
        Cookie drugiTelefon = new Cookie(Kolacici.UREDJAJ, "BCDEFGHJKLMNPQRSTUVWXYZabcdefghj");
        String kljuc = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/public/nalog/cas/skeniraj")
                        .header("Sec-Fetch-Site", "same-origin").cookie(telefon)
                        .contentType(MediaType.APPLICATION_JSON).content(telo(cekiranje.zaNastavnika(predavanje).token())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.kljuc");
        String potvrda = "{\"kljuc\":\"" + kljuc + "\"}";
        // ključ poslat drugom telefonu (i telefon bez kolačića uređaja) ne čekira, a ključ ostaje
        for (Cookie[] kolaci : new Cookie[][]{{studentskiKolacic(ana), drugiTelefon}, {studentskiKolacic(ana)}}) {
            mvc.perform(post("/public/nalog/cas/potvrdi").header("Sec-Fetch-Site", "same-origin").cookie(kolaci)
                            .contentType(MediaType.APPLICATION_JSON).content(potvrda))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.reason").value(CekiranjeService.KLJUC_ISTEKAO));
        }
        mvc.perform(post("/public/nalog/cas/potvrdi").header("Sec-Fetch-Site", "same-origin").cookie(studentskiKolacic(ana), telefon)
                        .contentType(MediaType.APPLICATION_JSON).content(potvrda))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predavanje.id").value(predavanje));
        mvc.perform(post("/public/nalog/cas/potvrdi").header("Sec-Fetch-Site", "same-origin").cookie(studentskiKolacic(ana), telefon)
                        .contentType(MediaType.APPLICATION_JSON).content(potvrda))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.reason").value(CekiranjeService.KLJUC_ISTEKAO));
    }

    @Test
    void istekaoTokenJe410() throws Exception {
        mvc.perform(post("/public/nalog/cas/skeniraj").header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content(telo(predavanje + ".xxxxxxxxxxxxxxxxxxxxxx")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.reason").value(CekiranjeService.QR_ISTEKAO));
    }

    @Test
    void skeniranjeSaNastavnickomSesijom409() throws Exception {
        String kolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        mvc.perform(post("/public/nalog/cas/skeniraj").with(PrijavaTestUtil.kaoNastavnik(kolacic))
                        .header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content(telo(cekiranje.zaNastavnika(predavanje).token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("Odjavi se kao nastavnik."));
        assertEquals(0, d.brojCekiranja(predavanje));
    }

    @Test
    void stanjeBezKolacica401() throws Exception {
        mvc.perform(get("/public/nalog/cas")).andExpect(status().isUnauthorized());
    }

    @Test
    void potvrdaBezStudenta401() throws Exception {
        mvc.perform(post("/public/nalog/cas/potvrdi").header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kljuc\":\"abcdefghjkmnpqrstuvwxyzABCDEFGHJ\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upisBezPoreklaOdbijen403() throws Exception {
        mvc.perform(post("/public/nalog/cas/skeniraj").header("Sec-Fetch-Site", "cross-site")
                        .contentType(MediaType.APPLICATION_JSON).content(telo(cekiranje.zaNastavnika(predavanje).token())))
                .andExpect(status().isForbidden());
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from cekiranja_na_cekanju where predavanje_id = ?",
                Integer.class, predavanje));
    }

    @Test
    void cekiranjeZaNastavnikaBezSesije401() throws Exception {
        mvc.perform(get("/predavanja/" + predavanje + "/cekiranje")).andExpect(status().isUnauthorized());
        // studentska sesija nije nastavnička
        mvc.perform(get("/predavanja/" + predavanje + "/cekiranje").cookie(studentskiKolacic(ana)))
                .andExpect(status().isForbidden());
    }

    @Test
    void odgovorNemaTajnuNiHash() throws Exception {
        Cookie student = studentskiKolacic(ana);
        String kolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        String info = mvc.perform(get("/predavanja/" + predavanje + "/cekiranje").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(info, "$.token");
        String skeniranje = mvc.perform(post("/public/nalog/cas/skeniraj").header("Sec-Fetch-Site", "same-origin")
                        .cookie(student).contentType(MediaType.APPLICATION_JSON).content(telo(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String stanje = mvc.perform(get("/public/nalog/cas").cookie(student))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String posle = mvc.perform(get("/predavanja/" + predavanje + "/cekiranje").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cekirani[0].studentId").value(ana))
                .andReturn().getResponse().getContentAsString();
        String tajna = d.tajna(predavanje);
        for (String json : new String[]{info, skeniranje, stanje, posle}) {
            assertFalse(json.contains(tajna), json);
            for (String zabranjeno : new String[]{"cekiranjeTajna", "uredjajHash", "kljucHash", "tajna", "grupa", "napomen"}) {
                assertFalse(json.contains(zabranjeno), zabranjeno + " u " + json);
            }
        }
    }
}
