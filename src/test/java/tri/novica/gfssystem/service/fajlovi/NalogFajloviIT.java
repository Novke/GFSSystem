package tri.novica.gfssystem.service.fajlovi;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Nalog studenta i "Pošalji mi na mejl" za fajlove uz prezentaciju (spec fajlova, odeljci 5 i 9), kroz ceo bezbednosni
 * lanac nad pravom bazom: spisak po predmetu, {@code pregled} sa fajlovima, preuzimanje sa zaglavljima, mejl i njegovi limiti.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PomerljivSatConfig.class, LazniMejlServis.class})
class NalogFajloviIT {

    @Autowired MockMvc mvc;
    @Autowired StudentskaPrijava studentskaPrijava;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired IzvodjenjeRepository izvodjenjeRepository;
    @Autowired KodoviFajlova kodovi;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired MedijService medijService;
    @Autowired LazniMejlServis mejl;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    FajloviPodaci p;
    Long g0, g1, g2;
    Long ana, bora, ponavlja, bezMejla;
    Long l1, prez, prez2, slajd1, slajd2, fBez, f1, f2, fDrugo;

    @BeforeEach
    void setUp() {
        mejl.ocisti();
        jdbc.update("delete from pokusaji where vrsta = 'MEJL_FAJLA'");
        d = new CasPodaci(jdbc);
        p = new FajloviPodaci(jdbc, medijService, prezentacijaService, d.predmetId);
        g0 = d.grupa(2025);
        g1 = d.grupa(2026);
        g2 = d.grupa(2026);
        ana = d.student(g1, "Ana");
        bora = d.student(g2, "Bora");
        ponavlja = d.student(g0, "Ponavlja");
        bezMejla = d.student(g1, "Bez");
        l1 = d.predavanje(g1, LocalDate.now());
        d.aktivnost(l1, ponavlja, "PRISUSTVO");
        postaviMejl(ana, "ana.anic@example.test");
        postaviMejl(bora, "bora@example.test");
        postaviMejl(ponavlja, "ponavlja@example.test");

        prez = p.prezentacija("Vežbe A");
        slajd1 = p.slajd(prez, "Prikazan");
        slajd2 = p.slajd(prez, "Preskočen");
        fBez = p.fajl(prez, null, "Vežba č.docx");
        f1 = p.fajl(prez, slajd1, "uz-slajd-1.xlsx");
        f2 = p.fajl(prez, slajd2, "uz-slajd-2.docx");
        prez2 = p.prezentacija("Vežbe B");
        fDrugo = p.fajl(prez2, null, "drugo.docx");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from pokusaji where vrsta = 'MEJL_FAJLA'");
        d.obrisi();
        p.ocisti();
    }

    void postaviMejl(Long studentId, String adresa) {
        jdbc.update("update studenti set email = ? where id = ?", adresa, studentId);
    }

    Long pokreni(Long prezId, Long predavanjeId) {
        return izvodjenjeService.pokreni(prezId, new PokreniCmd(false, null, predavanjeId)).id();
    }

    Cookie kolacic(Long studentId) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        studentskaPrijava.prijavi(studentId, NacinPrijave.KOD, new MockHttpServletRequest(), res);
        String h = res.getHeader("Set-Cookie");
        assertNotNull(h);
        return new Cookie(Kolacici.STUDENT, h.substring(h.indexOf('=') + 1, h.indexOf(';')));
    }

    /** Završeno izvođenje prezentacije sa grupom predavanja l1 (nalog tada otvara prezentaciju). */
    void odrzi(Long prezId) {
        p.zavrsiIzvodjenje(pokreni(prezId, l1));
    }

    org.springframework.test.web.servlet.ResultActions mejlPost(Cookie c, Long fajlId) throws Exception {
        return mvc.perform(post("/public/nalog/fajlovi/{id}/mejl", fajlId).header("Sec-Fetch-Site", "same-origin").cookie(c));
    }

    // ---------------------------------------------------------------- spisak i pregled

    @Test
    void spisakPoPredmetuNosiSveFajloveOdrzanePrezentacijeIUkljucujeNeprikazanSlajd() throws Exception {
        odrzi(prez);
        mvc.perform(get("/public/nalog/predmeti/{id}/fajlovi", d.predmetId).cookie(kolacic(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prezentacijaId").value(prez))
                .andExpect(jsonPath("$[0].naziv").value("Vežbe A"))
                .andExpect(jsonPath("$[0].odrzana").exists())
                .andExpect(jsonPath("$[0].fajlovi.length()").value(3))   // i f2 (slajd nikad prikazan) i fBez
                .andExpect(jsonPath("$[0].fajlovi[0].id").value(fBez))
                .andExpect(jsonPath("$[0].fajlovi[0].vrsta").value("DOCX"))
                .andExpect(jsonPath("$[0].fajlovi[0].velicina").value(FajloviPodaci.PK.length))
                .andExpect(jsonPath("$[0].fajlovi[2].id").value(f2));
    }

    @Test
    void izvodjenjeUTokuNeOtvaraSpisak() throws Exception {
        pokreni(prez, l1);
        mvc.perform(get("/public/nalog/predmeti/{id}/fajlovi", d.predmetId).cookie(kolacic(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dodatnaGrupaVidiSpisakATudjaGrupaDobija404() throws Exception {
        odrzi(prez);
        mvc.perform(get("/public/nalog/predmeti/{id}/fajlovi", d.predmetId).cookie(kolacic(ponavlja)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fajlovi.length()").value(3));
        mvc.perform(get("/public/nalog/predmeti/{id}/fajlovi", d.predmetId).cookie(kolacic(bora)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Nije pronađeno."));
    }

    @Test
    void bezSesije401() throws Exception {
        mvc.perform(get("/public/nalog/predmeti/{id}/fajlovi", d.predmetId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/nalog/fajlovi/{id}", fBez)).andExpect(status().isUnauthorized());
        mvc.perform(post("/public/nalog/fajlovi/{id}/mejl", fBez).header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pregledNosiFajloveKadPraviloVazi() throws Exception {
        // slajd prikazan grupi: pregled prezentacije je dostupan; fajlovi se vide samo kad pravilo naloga važi
        Long iz = pokreni(prez, l1);
        jdbc.update("insert into prikazani_slajdovi (izvodjenje_id, slajd_id, grupa_id, prvi_put) values (?, ?, ?, ?)",
                iz, slajd1, g1, LocalDateTime.now());
        p.zavrsiIzvodjenje(iz);
        mvc.perform(get("/public/nalog/prezentacije/{id}", prez).cookie(kolacic(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slajdovi.length()").value(1))     // pravilo slajdova nepromenjeno
                .andExpect(jsonPath("$.fajlovi.length()").value(3))
                .andExpect(jsonPath("$.fajlovi[0].naziv").value("Vežba č.docx"));
    }

    // ---------------------------------------------------------------- preuzimanje

    @Test
    void preuzimanjeSaZaglavljimaIFajlBezSlajdaIPrikazanogSlajda() throws Exception {
        odrzi(prez);
        MvcResult r = mvc.perform(get("/public/nalog/fajlovi/{id}", fBez).cookie(kolacic(ana)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().longValue("Content-Length", FajloviPodaci.PK.length))
                .andReturn();
        String cd = r.getResponse().getHeader("Content-Disposition");
        assertTrue(cd.startsWith("attachment;"), cd);
        assertTrue(cd.contains("filename*=UTF-8''Ve%C5%BEba%20%C4%8D.docx"), cd);
        assertArrayEquals(FajloviPodaci.PK, r.getResponse().getContentAsByteArray());
        mvc.perform(get("/public/nalog/fajlovi/{id}", f2).cookie(kolacic(ana))).andExpect(status().isOk());
    }

    @Test
    void preuzimanje404KadNijedanoPravilo() throws Exception {
        mvc.perform(get("/public/nalog/fajlovi/{id}", fBez).cookie(kolacic(ana)))      // izvođenje još nije održano
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Nije pronađeno."));
        odrzi(prez);
        mvc.perform(get("/public/nalog/fajlovi/{id}", fBez).cookie(kolacic(bora))).andExpect(status().isNotFound());
        mvc.perform(get("/public/nalog/fajlovi/{id}", fDrugo).cookie(kolacic(ana))).andExpect(status().isNotFound());
        mvc.perform(get("/public/nalog/fajlovi/{id}", 999_999_999L).cookie(kolacic(ana))).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- mejl

    @Test
    void mejlPoNalogu202SaMaskomIJednimPrilogem() throws Exception {
        odrzi(prez);
        mejlPost(kolacic(ana), fBez).andExpect(status().isAccepted()).andExpect(jsonPath("$.poslatoNa").value("a***@example.test"));
        List<MejlPoruka> poslate = mejl.cekaj(1);
        assertEquals(1, poslate.size());
        MejlPoruka m = poslate.get(0);
        assertEquals("ana.anic@example.test", m.za());
        assertEquals("Fajl sa časa", m.naslov());
        assertEquals(1, m.prilozi().size());
        assertEquals("Vežba č.docx", m.prilozi().get(0).naziv());
        assertArrayEquals(FajloviPodaci.PK, m.prilozi().get(0).sadrzaj());
        assertTrue(m.telo().contains("Vežba č.docx") && m.telo().contains("Vežbe A"));
        Thread.sleep(200);
        assertEquals(1, mejl.poslate().size());
    }

    @Test
    void mejlUzivoNaVazecemKoduDozvoljenIakoIzvodjenjeNijeZavrseno() throws Exception {
        Long iz = pokreni(prez, l1);
        kodovi.obezbedi(izvodjenjeRepository.findSaVezama(iz).orElseThrow()).orElseThrow();
        mejlPost(kolacic(ana), fBez).andExpect(status().isAccepted());   // fajl bez slajda je na kodu od početka
        mejl.cekaj(1);
        mejlPost(kolacic(ana), f1).andExpect(status().isNotFound());       // slajd još nije prikazan, nalog ne važi
    }

    @Test
    void mejl404KadNijedanoPravilo() throws Exception {
        mejlPost(kolacic(ana), fBez).andExpect(status().isNotFound()).andExpect(jsonPath("$.reason").value("Nije pronađeno."));
        odrzi(prez);
        mejlPost(kolacic(bora), fBez).andExpect(status().isNotFound());
        mejlPost(kolacic(ana), 999_999_999L).andExpect(status().isNotFound());
        assertEquals(0, mejl.poslate().size());
    }

    @Test
    void mejl409BezMejlaNeTrosiLimit() throws Exception {
        odrzi(prez);
        mejlPost(kolacic(bezMejla), fBez).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("U GFS-u nemaš upisan mejl. Javi se nastavniku."));
        assertEquals(0, jdbc.queryForObject("select count(*) from pokusaji where vrsta = 'MEJL_FAJLA'", Integer.class));
    }

    @Test
    void mejl503KadSlanjeNijePodeseno() throws Exception {
        odrzi(prez);
        mejl.dostupan(false);
        mejlPost(kolacic(ana), fBez).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("Slanje mejla trenutno ne radi."));
        assertEquals(0, jdbc.queryForObject("select count(*) from pokusaji where vrsta = 'MEJL_FAJLA'", Integer.class));
    }

    @Test
    void jedanaestiNaSatJe429() throws Exception {
        odrzi(prez);
        Cookie c = kolacic(ana);
        for (int i = 0; i < 10; i++) mejlPost(c, fBez).andExpect(status().isAccepted());
        mejlPost(c, fBez).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("Već si poslao dosta fajlova. Pokušaj ponovo za sat."));
        mejlPost(kolacic(ponavlja), fBez).andExpect(status().isAccepted());   // limit je po studentu
        mejl.cekaj(11);
    }

    @Test
    void dvadeseti_dnevniLimitPoStudentu() throws Exception {
        odrzi(prez);
        ubaciPokusaje("s" + ana, 20, Duration.ofHours(5));   // 20 pre više od sat vremena: dnevni limit, ne satni
        mejlPost(kolacic(ana), fBez).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("Već si poslao dosta fajlova. Pokušaj ponovo za sat."));
    }

    @Test
    void stoPedesetPrviDnevnoUkupnoJe429() throws Exception {
        odrzi(prez);
        ubaciPokusaje("*", 150, Duration.ofHours(2));
        mejlPost(kolacic(ana), fBez).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("Danas je poslato previše fajlova mejlom. Preuzmi fajl na računaru."));
        assertEquals(0, mejl.poslate().size());
    }

    void ubaciPokusaje(String kljuc, int n, Duration pre) {
        List<Object[]> redovi = new ArrayList<>();
        LocalDateTime vreme = LocalDateTime.now().minus(pre);
        for (int i = 0; i < n; i++) redovi.add(new Object[]{"MEJL_FAJLA", kljuc, vreme});
        jdbc.batchUpdate("insert into pokusaji (vrsta, kljuc, vreme) values (?, ?, ?)", redovi);
    }
}
