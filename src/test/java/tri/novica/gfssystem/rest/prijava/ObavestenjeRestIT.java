package tri.novica.gfssystem.rest.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.ObavestenjeServis;
import tri.novica.gfssystem.service.prijava.TestStudenti;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code /obavestenja} (nastavnik, ADMIN) kroz ceo bezbednosni lanac nad pravom MySQL bazom (spec 6): lista sa stranicama,
 * brojač, pročitano/rešeno/pročitaj sve; bez nastavničke sesije 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LazniMejlServis.class)
class ObavestenjeRestIT {

    @Autowired MockMvc mvc;
    @Autowired ObavestenjeServis servis;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired StudentRepository studentRepository;
    @Autowired LazniMejlServis mejl;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    RequestPostProcessor nastavnik;
    final List<String> kljucevi = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mejl.ocisti();
        jdbc.update("delete from obavestenja");
        studenti = new TestStudenti(studentRepository, jdbc);
        nastavnik = PrijavaTestUtil.kaoNastavnik(PrijavaTestUtil.nastavnickiKolacic(nastavnici));
    }

    @AfterEach
    void ocisti() {
        mejl.ocisti();
        studenti.obrisi();
        jdbc.update("delete from obavestenja");
        jdbc.update("delete from pokusaji where vrsta = 'POMOC_INDEKS'");
        for (String k : kljucevi) {
            jdbc.update("delete from pokusaji where kljuc = ?", k);
        }
    }

    void pomoc(String indeks) {
        String ip = "2001:db8:b8::" + UUID.randomUUID().toString().substring(0, 4);
        kljucevi.add(ip);
        servis.neStizeKod(indeks, ip, Optional.empty());
    }

    @Test
    void bezNastavnickeSesijeJe401() throws Exception {
        mvc.perform(get("/obavestenja")).andExpect(status().isUnauthorized());
        mvc.perform(get("/obavestenja/broj")).andExpect(status().isUnauthorized());
        mvc.perform(post("/obavestenja/1/procitano").header("Origin", PrijavaTestUtil.ORIGIN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/obavestenja/1/reseno").header("Origin", PrijavaTestUtil.ORIGIN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/obavestenja/procitaj-sve").header("Origin", PrijavaTestUtil.ORIGIN)).andExpect(status().isUnauthorized());
    }

    @Test
    void listaImaOblikUgovora() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        pomoc(s.getIndeks());
        String nema = TestStudenti.noviIndeks();
        pomoc(nema);

        mvc.perform(get("/obavestenja").param("stanje", "otvorena").param("page", "0").param("size", "10").with(nastavnik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                // najnovije prvo: nepostojeći indeks je stvoren posle studenta
                .andExpect(jsonPath("$.content[0].studentId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].studentIme").value(nullValue()))
                .andExpect(jsonPath("$.content[0].indeks").value(nema.replace("/2025", "")))
                .andExpect(jsonPath("$.content[0].godina").value(2025))
                .andExpect(jsonPath("$.content[0].vrsta").value("NE_STIZE_KOD"))
                .andExpect(jsonPath("$.content[0].podaci.imaMejl").value(false))
                .andExpect(jsonPath("$.content[0].podaci.uredjaj").value("NEPOZNAT"))
                .andExpect(jsonPath("$.content[0].procitano").value(nullValue()))
                .andExpect(jsonPath("$.content[0].reseno").value(nullValue()))
                .andExpect(jsonPath("$.content[0].kreirano").isString())
                .andExpect(jsonPath("$.content[1].studentId").value(s.getId()))
                .andExpect(jsonPath("$.content[1].studentIme").value("Test Studentić"))
                .andExpect(jsonPath("$.content[1].indeks").value(s.getIndeks()))
                .andExpect(jsonPath("$.content[1].podaci.imaMejl").value(true))
                .andExpect(jsonPath("$.content[1].podaci.ip", startsWith("2001:db8:b8::")))
                .andExpect(jsonPath("$.content[0].podaci", instanceOf(java.util.Map.class)));
    }

    @Test
    void stanjeOtvorenaISva() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        pomoc(s.getIndeks());
        pomoc(TestStudenti.noviIndeks());
        Long id = jdbc.queryForObject("select id from obavestenja where student_id = ?", Long.class, s.getId());

        mvc.perform(post("/obavestenja/{id}/reseno", id).header("Origin", PrijavaTestUtil.ORIGIN).with(nastavnik))
                .andExpect(status().isNoContent());
        mvc.perform(get("/obavestenja").with(nastavnik))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/obavestenja").param("stanje", "sva").with(nastavnik))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].reseno", not(empty())));
        mvc.perform(get("/obavestenja").param("stanje", "nesto").with(nastavnik))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/obavestenja").param("sort", "podaci").with(nastavnik))
                .andExpect(status().isBadRequest());
    }

    @Test
    void brojProcitanoIProcitajSve() throws Exception {
        pomoc(TestStudenti.noviIndeks());
        pomoc(TestStudenti.noviIndeks());
        pomoc(TestStudenti.noviIndeks());
        mvc.perform(get("/obavestenja/broj").with(nastavnik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neprocitana").value(3))
                .andExpect(jsonPath("$.otvorena").value(3));

        Long id = jdbc.queryForObject("select min(id) from obavestenja", Long.class);
        mvc.perform(post("/obavestenja/{id}/procitano", id).header("Origin", PrijavaTestUtil.ORIGIN).with(nastavnik))
                .andExpect(status().isNoContent());
        mvc.perform(get("/obavestenja/broj").with(nastavnik))
                .andExpect(jsonPath("$.neprocitana").value(2))
                .andExpect(jsonPath("$.otvorena").value(3));

        mvc.perform(post("/obavestenja/procitaj-sve").header("Origin", PrijavaTestUtil.ORIGIN).with(nastavnik))
                .andExpect(status().isNoContent());
        mvc.perform(get("/obavestenja/broj").with(nastavnik))
                .andExpect(jsonPath("$.neprocitana").value(0))
                .andExpect(jsonPath("$.otvorena").value(3));
    }

    @Test
    void nepoznatIdJe404() throws Exception {
        mvc.perform(post("/obavestenja/{id}/procitano", -1).header("Origin", PrijavaTestUtil.ORIGIN).with(nastavnik))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Ne postoji."));
        mvc.perform(post("/obavestenja/{id}/reseno", -1).header("Origin", PrijavaTestUtil.ORIGIN).with(nastavnik))
                .andExpect(status().isNotFound());
        mvc.perform(post("/obavestenja/abc/reseno").header("Origin", PrijavaTestUtil.ORIGIN).with(nastavnik))
                .andExpect(status().isBadRequest());
    }

    @Test
    void odgovorNeNosiTokenIIpSeVidiSamoNastavniku() throws Exception {
        Student s = studenti.napravi("ana@example.test");
        pomoc(s.getIndeks());
        String telo = mvc.perform(get("/obavestenja").with(nastavnik)).andReturn().getResponse().getContentAsString();
        assertEquals(false, telo.contains("ana@example.test"), "mejl studenta ne ide u listu");
    }
}
