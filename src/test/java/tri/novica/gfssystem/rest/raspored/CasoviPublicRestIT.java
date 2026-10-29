package tri.novica.gfssystem.rest.raspored;

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
import tri.novica.gfssystem.dto.raspored.RasporedCmd;
import tri.novica.gfssystem.dto.raspored.RasporedDetails;
import tri.novica.gfssystem.dto.raspored.StavkaCmd;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.service.raspored.NajavaService;
import tri.novica.gfssystem.service.raspored.RasporedService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /public/nalog/casovi} i {@code /rasporedi} kroz ceo bezbednosni lanac (MockMvc sa filterima) nad pravom bazom:
 * klase pristupa (STUDENT, ADMIN), provera porekla na upisu, student samo iz kolačića (polje {@code studentId} u telu se
 * ne čita), i da odgovor studentu ne nosi broj najava ni tuđa imena. Pravi sat: čas je sutra u 10, pa nikad nije počeo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CasoviPublicRestIT {

    @Autowired MockMvc mvc;
    @Autowired RasporedService rasporedi;
    @Autowired StudentskaPrijava studentskaPrijava;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    CasPodaci d;
    Long period;
    Long stavka;
    LocalDate sutra;
    Long ana;
    Long bora;
    Long tudji;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        Long grupa = d.grupa(2026);
        Long druga = d.grupa(2026);
        ana = d.student(grupa, "Ana");
        bora = d.student(grupa, "Bora");
        tudji = d.student(druga, "Tuđin");
        LocalDate danas = LocalDate.now(clock);
        sutra = danas.plusDays(1);
        RasporedDetails r = rasporedi.kreiraj(new RasporedCmd("Raspored " + d.oznaka, danas, danas.plusDays(60), 5,
                List.of(new StavkaCmd(null, d.predmetId, grupa, sutra.getDayOfWeek().getValue(), LocalTime.of(10, 0),
                        LocalTime.of(13, 0)))));
        period = r.id();
        stavka = r.stavke().get(0).id();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from rasporedi where id = ?", period);
        d.obrisi();
    }

    Cookie studentskiKolacic(Long studentId) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        studentskaPrijava.prijavi(studentId, NacinPrijave.KOD, new MockHttpServletRequest(), res);
        String h = res.getHeader("Set-Cookie");
        assertNotNull(h);
        return new Cookie(Kolacici.STUDENT, h.substring(h.indexOf('=') + 1, h.indexOf(';')));
    }

    String telo(String razlog) {
        return "{\"stavkaId\":" + stavka + ",\"datum\":\"" + sutra + "\""
                + (razlog == null ? "" : ",\"razlog\":\"" + razlog + "\"") + "}";
    }

    @Test
    void studentVidiINajavljujeBezTudjihPodataka() throws Exception {
        Cookie a = studentskiKolacic(ana);
        mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "same-origin").cookie(studentskiKolacic(bora))
                        .contentType(MediaType.APPLICATION_JSON).content(telo("Borin razlog")))
                .andExpect(status().isOk());
        String naredni = mvc.perform(get("/public/nalog/casovi").cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termini[0].stavkaId").value(stavka))
                .andExpect(jsonPath("$.termini[0].datum").value(sutra.toString()))
                .andExpect(jsonPath("$.termini[0].pocetak").value("10:00:00"))
                .andExpect(jsonPath("$.termini[0].najavljeno").value(false))
                .andReturn().getResponse().getContentAsString();
        // telo sa tuđim studentId: student je uvek iz kolačića
        String najava = mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "same-origin").cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stavkaId\":" + stavka + ",\"datum\":\"" + sutra + "\",\"studentId\":" + tudji + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.najavljeno").value(true))
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, (int) jdbc.queryForObject("select count(*) from najave_odsustva where student_id = ?", Integer.class, ana));
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from najave_odsustva where student_id = ?", Integer.class, tudji));
        for (String json : new String[]{naredni, najava}) {
            for (String zabranjeno : new String[]{"Bora", "Borin razlog", "brojNajava", "najave", "prag", "studentId"}) {
                assertFalse(json.contains(zabranjeno), zabranjeno + " u " + json);
            }
        }
        mvc.perform(post("/public/nalog/casovi/povuci").header("Sec-Fetch-Site", "same-origin").cookie(a)
                        .contentType(MediaType.APPLICATION_JSON).content(telo(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.najavljeno").value(false));
    }

    @Test
    void tudjiCas404INeispravnoTelo400() throws Exception {
        mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "same-origin").cookie(studentskiKolacic(tudji))
                        .contentType(MediaType.APPLICATION_JSON).content(telo(null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value(NajavaService.NIJE_TVOJ));
        mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "same-origin").cookie(studentskiKolacic(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datum\":\"" + sutra + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "same-origin").cookie(studentskiKolacic(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stavkaId\":" + stavka + ",\"datum\":\"nije-datum\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pristup() throws Exception {
        mvc.perform(get("/public/nalog/casovi")).andExpect(status().isUnauthorized());
        mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON).content(telo(null)))
                .andExpect(status().isUnauthorized());
        // upis sa tuđeg sajta: provera porekla
        mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "cross-site").cookie(studentskiKolacic(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(telo(null)))
                .andExpect(status().isForbidden());
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ?", Integer.class, stavka));
        // studentska sesija nije nastavnička
        mvc.perform(get("/rasporedi").cookie(studentskiKolacic(ana))).andExpect(status().isForbidden());
        mvc.perform(get("/rasporedi/termini")).andExpect(status().isUnauthorized());
    }

    @Test
    void nastavnikVidiImenaIRazloge() throws Exception {
        mvc.perform(post("/public/nalog/casovi/najavi").header("Sec-Fetch-Site", "same-origin").cookie(studentskiKolacic(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(telo("lekar")))
                .andExpect(status().isOk());
        String kolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        mvc.perform(get("/rasporedi/termini").param("od", sutra.toString()).param("dana", "1")
                        .with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stavkaId == " + stavka + ")].brojNajava").value(1))
                .andExpect(jsonPath("$[?(@.stavkaId == " + stavka + ")].najave[0].ime").value("Ana"))
                .andExpect(jsonPath("$[?(@.stavkaId == " + stavka + ")].najave[0].razlog").value("lekar"));
        mvc.perform(get("/rasporedi/termini").param("dana", "x").with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/rasporedi/" + period).with(PrijavaTestUtil.kaoNastavnik(kolacic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stavke[0].pocetak").value("10:00:00"));
    }
}
