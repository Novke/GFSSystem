package tri.novica.gfssystem.service;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.pregled.GrupaPregledInfo;
import tri.novica.gfssystem.dto.pregled.GrupaStudentStatInfo;
import tri.novica.gfssystem.dto.pregled.PrisustvoMatricaInfo;
import tri.novica.gfssystem.dto.pregled.StudentPredmetKarticaInfo;
import tri.novica.gfssystem.entity.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pregled grupe (G1/G2), matrica prisustva (G3) i kartice studenta po predmetu (S2) nad pravom MySQL bazom; svaki test
 * se vraća unazad. Podaci su u školskoj godini 2088/89 (i 2087/88 za stariju grupu), tvrdnje su samo o sopstvenim id-jevima.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PregledGrupeIT {

    static final double D = 1e-9;

    @Autowired PregledGrupeService service;
    @Autowired OcenjivanjeService ocenjivanje;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    // scenario: grupa N (2088) ima predmete A (sa koeficijentima), B (bez sačuvanih koeficijenata) i C (poslednji
    // rezultat); stariji student o1 iz grupe O (2087) dolazi na predavanje i test grupe N
    Predmet a, b, c;
    Grupa n, o, prazna;
    Student n1, n2, n3, o1, o2, e1;
    Predavanje pA0, pA1, pA2, pA3, pB1, pC1, pO1;
    Domaci dA1, dA2, dO1;
    TipTesta k1, k2, tb, tc;
    tri.novica.gfssystem.entity.Test tN1, tN1b, tN2, tO1, tB, tC1, tC2;
    OnboardingSesija otvorena;

    @BeforeEach
    void pripremi() {
        a = predmet("Statika PG");
        b = predmet("Beton PG");
        c = predmet("Celik PG");
        n = grupa("PG-2088", 2088);
        o = grupa("PG-2087", 2087);
        prazna = grupa("PG-prazna", 2088);

        n1 = student("PG1", n);
        n2 = student("PG2", n);
        n3 = student("PG3", n);
        o1 = student("PG7", o);
        o2 = student("PG8", o);
        e1 = student("PG9", prazna);

        // predavanja A grupe N: namerno upisana van redosleda datuma; pA0 je u prošloj školskoj godini (2087/88)
        pA3 = predavanje(a, n, 3, LocalDate.of(2088, 10, 19), "Grede");
        pA1 = predavanje(a, n, 1, LocalDate.of(2088, 10, 5), "Uvod");
        pA2 = predavanje(a, n, 2, LocalDate.of(2088, 10, 12), null);
        pA0 = predavanje(a, n, 0, LocalDate.of(2088, 9, 30), "Pripremno");
        pB1 = predavanje(b, n, 4, LocalDate.of(2088, 10, 6), null);
        pC1 = predavanje(c, n, 5, LocalDate.of(2088, 10, 7), null);
        pO1 = predavanje(a, o, 1, LocalDate.of(2087, 10, 6), null);

        aktivnost(pA1, n1, TipAktivnosti.PRISUSTVO);
        aktivnost(pA2, n1, TipAktivnosti.ZADATAK);
        aktivnost(pA3, n1, TipAktivnosti.SA_ZVEZDICOM);
        aktivnost(pB1, n1, TipAktivnosti.PRISUSTVO);
        aktivnost(pC1, n1, TipAktivnosti.ZADATAK);
        aktivnost(pA1, n2, TipAktivnosti.PRISUSTVO);
        aktivnost(pO1, o1, TipAktivnosti.PRISUSTVO);
        aktivnost(pA2, o1, TipAktivnosti.ZADATAK);      // ponovac na predavanju mlađe grupe

        dA1 = domaci(a, n, LocalDate.of(2088, 10, 6));
        dA2 = domaci(a, n, LocalDate.of(2088, 10, 13));
        dO1 = domaci(a, o, LocalDate.of(2087, 10, 7));
        uradjen(dA1, n1, 7, false);
        uradjen(dA2, n1, 10, true);                     // oslobođen: puni bodovi, broji se kao urađen
        uradjen(dA1, n2, 3, false);
        uradjen(dO1, o1, 5, false);

        k1 = tip("Kolokvijum 1 PG", a);
        k2 = tip("Kolokvijum 2 PG", a);
        tb = tip("Test B PG", b);
        tc = tip("Test C PG", c);
        tO1 = test(k1, a, o, LocalDate.of(2087, 11, 10), 50);
        tN1 = test(k1, a, n, LocalDate.of(2088, 11, 10), 50);
        tN1b = test(k1, a, n, LocalDate.of(2088, 12, 1), 50);
        tN2 = test(k2, a, n, LocalDate.of(2089, 1, 15), 40);
        tB = test(tb, b, n, LocalDate.of(2088, 11, 3), 60);
        tC1 = test(tc, c, n, LocalDate.of(2088, 11, 20), 20);
        tC2 = test(tc, c, n, LocalDate.of(2088, 11, 20), 20);   // isti datum, veći id: "poslednji"
        polaganje(tN1, n1, 30.0);
        polaganje(tN1b, n1, 42.0);
        polaganje(tN2, n1, 35.0);
        polaganje(tC1, n1, 18.0);
        polaganje(tC2, n1, 11.0);
        polaganje(tN1, n2, 20.0);
        polaganje(tB, n2, 45.5);
        polaganje(tO1, o1, 25.0);
        polaganje(tN1, o1, 33.0);                        // ponovac na testu mlađe grupe

        // A: sopstveni koeficijenti sa normalizacijom; C: poslednji rezultat umesto najboljeg; B: bez reda
        KoeficijentiOcenjivanja ka = new KoeficijentiOcenjivanja(a);
        ka.setKoefPrisustvo(1.5);
        ka.setKoefZadatak(2.5);
        ka.setKoefZvezdica(3.0);
        ka.setMaxAktivnost(10.0);
        ka.setMaxDomaci(10.0);
        KoeficijentTipTesta kt = new KoeficijentTipTesta(ka, k1);
        kt.setMaxPoena(30.0);
        ka.getKoeficijentiTipova().add(kt);
        em.persist(ka);
        KoeficijentiOcenjivanja kc = new KoeficijentiOcenjivanja(c);
        kc.setKoristiMaxRezultat(false);
        em.persist(kc);

        sesija(n, "PGtokenIsteklo00000000000000000a", true, LocalDateTime.of(2001, 1, 1, 0, 0), LocalDateTime.of(2000, 1, 1, 0, 0));
        otvorena = sesija(n, "PGtokenOtvoreno0000000000000000b", true, LocalDateTime.of(2002, 1, 1, 0, 0), LocalDateTime.of(2099, 1, 1, 0, 0));
        sesija(n, "PGtokenUgaseno00000000000000000c", false, LocalDateTime.of(2003, 1, 1, 0, 0), LocalDateTime.of(2099, 1, 1, 0, 0));

        flushClear();
    }

    // ================================================================== G1 + G2

    @Test
    void pregledGrupeBezPredmetaBrojiSvePredmete() {
        GrupaPregledInfo p = service.pregled(n.getId(), null);

        assertEquals(n.getId(), p.grupa().getId());
        assertEquals(3L, p.grupa().getBrojStudenata());
        assertEquals(3, p.brojStudenata());
        assertEquals(6, p.brojPredavanja());              // pA0-pA3, pB1, pC1
        assertEquals(otvorena.getId(), p.otvorenOnboarding().getId());
        assertEquals(List.of(n1.getId(), n2.getId(), n3.getId()),
                p.studenti().stream().map(s -> s.student().getId()).toList());

        GrupaStudentStatInfo s1 = p.studenti().get(0);
        assertEquals(5, s1.prisutan());
        assertEquals(6, s1.predavanja());
        assertEquals(2, s1.domaciUradjeno());
        assertEquals(2, s1.domaciUkupno());
        assertEquals(tN2.getId(), s1.poslednjiTest().testId());
        assertEquals("Kolokvijum 2 PG", s1.poslednjiTest().tip());
        assertEquals(LocalDate.of(2089, 1, 15), s1.poslednjiTest().datum());
        assertEquals(35.0, s1.poslednjiTest().poeni());
        assertEquals(40, s1.poslednjiTest().maxPoena());

        GrupaStudentStatInfo s2 = p.studenti().get(1);
        assertEquals(1, s2.prisutan());
        assertEquals(6, s2.predavanja());
        assertEquals(1, s2.domaciUradjeno());
        assertEquals(tN1.getId(), s2.poslednjiTest().testId());   // tN1 (10. 11.) posle tB (3. 11.)

        GrupaStudentStatInfo s3 = p.studenti().get(2);
        assertEquals(0, s3.prisutan());
        assertEquals(6, s3.predavanja());
        assertEquals(0, s3.domaciUradjeno());
        assertEquals(2, s3.domaciUkupno());
        assertNull(s3.poslednjiTest());

        assertEquals((5 + 1 + 0) / 18.0, p.prosecnaPrisutnost(), D);
    }

    @Test
    void pregledGrupeZaPredmet() {
        GrupaPregledInfo p = service.pregled(n.getId(), c.getId());
        assertEquals(1, p.brojPredavanja());
        GrupaStudentStatInfo s1 = p.studenti().get(0);
        assertEquals(1, s1.prisutan());
        assertEquals(1, s1.predavanja());
        assertEquals(0, s1.domaciUkupno());
        assertEquals(tC2.getId(), s1.poslednjiTest().testId());   // isti datum kao tC1, veći id
        assertEquals(11.0, s1.poslednjiTest().poeni());
        assertNull(p.studenti().get(1).poslednjiTest());
        assertEquals(1 / 3.0, p.prosecnaPrisutnost(), D);

        GrupaPregledInfo pa = service.pregled(n.getId(), a.getId());
        assertEquals(4, pa.brojPredavanja());
        assertEquals(3, pa.studenti().get(0).prisutan());
        assertEquals(tN2.getId(), pa.studenti().get(0).poslednjiTest().testId());
        assertEquals(tN1.getId(), pa.studenti().get(1).poslednjiTest().testId());
    }

    @Test
    void ponovacImaPredavanjaSvojeGrupeIOnaNaKojimaJeBio() {
        GrupaPregledInfo p = service.pregled(o.getId(), null);
        assertEquals(1, p.brojPredavanja());              // samo pO1 je predavanje grupe O
        assertNull(p.otvorenOnboarding());
        GrupaStudentStatInfo s1 = p.studenti().get(0);    // o1: pO1 + pA2 grupe N
        assertEquals(2, s1.prisutan());
        assertEquals(2, s1.predavanja());
        assertEquals(1, s1.domaciUradjeno());             // dO1; domaći grupe N se ne broje
        assertEquals(1, s1.domaciUkupno());
        assertEquals(tN1.getId(), s1.poslednjiTest().testId());
        GrupaStudentStatInfo s2 = p.studenti().get(1);
        assertEquals(0, s2.prisutan());
        assertEquals(1, s2.predavanja());
        assertEquals(2 / 3.0, p.prosecnaPrisutnost(), D);
    }

    @Test
    void grupaBezPredavanjaNemaProsecnuPrisutnost() throws Exception {
        GrupaPregledInfo p = service.pregled(prazna.getId(), null);
        assertEquals(0, p.brojPredavanja());
        assertNull(p.prosecnaPrisutnost());
        assertNull(p.otvorenOnboarding());
        assertEquals(1, p.studenti().size());
        GrupaStudentStatInfo s = p.studenti().get(0);
        assertEquals(0, s.prisutan());
        assertEquals(0, s.predavanja());
        assertEquals(0, s.domaciUradjeno());
        assertEquals(0, s.domaciUkupno());
        assertNull(s.poslednjiTest());

        mvc.perform(get("/grupe/{id}/pregled", prazna.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.grupa.id").value(prazna.getId()))
           .andExpect(jsonPath("$.grupa.brojStudenata").value(1))
           .andExpect(jsonPath("$.brojStudenata").value(1))
           .andExpect(jsonPath("$.brojPredavanja").value(0))
           .andExpect(jsonPath("$.prosecnaPrisutnost").value(nullValue()))
           .andExpect(jsonPath("$.otvorenOnboarding").value(nullValue()))
           .andExpect(jsonPath("$.studenti[0].student.id").value(e1.getId()))
           .andExpect(jsonPath("$.studenti[0].prisutan").value(0))
           .andExpect(jsonPath("$.studenti[0].predavanja").value(0))
           .andExpect(jsonPath("$.studenti[0].domaciUradjeno").value(0))
           .andExpect(jsonPath("$.studenti[0].domaciUkupno").value(0))
           .andExpect(jsonPath("$.studenti[0].poslednjiTest").value(nullValue()));
    }

    @Test
    void grupaBezStudenata() {
        Grupa bez = grupa("PG-bez", 2088);
        predavanje(a, bez, 1, LocalDate.of(2088, 10, 5), null);
        flushClear();
        GrupaPregledInfo p = service.pregled(bez.getId(), a.getId());
        assertEquals(0, p.brojStudenata());
        assertEquals(1, p.brojPredavanja());
        assertNull(p.prosecnaPrisutnost());
        assertTrue(p.studenti().isEmpty());
    }

    @Test
    void pregledGrupeHttp() throws Exception {
        mvc.perform(get("/grupe/{id}/pregled", n.getId()).param("predmetId", a.getId().toString()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.brojPredavanja").value(4))
           .andExpect(jsonPath("$.otvorenOnboarding.id").value(otvorena.getId()))
           .andExpect(jsonPath("$.studenti[0].poslednjiTest.testId").value(tN2.getId()))
           .andExpect(jsonPath("$.studenti[0].poslednjiTest.tip").value("Kolokvijum 2 PG"))
           .andExpect(jsonPath("$.studenti[0].poslednjiTest.datum").value("2089-01-15"))
           .andExpect(jsonPath("$.studenti[0].poslednjiTest.poeni").value(35.0))
           .andExpect(jsonPath("$.studenti[0].poslednjiTest.maxPoena").value(40));
        mvc.perform(get("/grupe/{id}/pregled", 99_999_999L))
           .andExpect(status().isNotFound());
        mvc.perform(get("/grupe/{id}/pregled", n.getId()).param("predmetId", "99999999"))
           .andExpect(status().isNotFound());
        mvc.perform(get("/grupe/{id}/pregled", n.getId()).param("predmetId", "abc"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: predmetId."));
    }

    // ================================================================== G3

    @Test
    void matricaSaPraznimCelijama() {
        PrisustvoMatricaInfo m = service.prisustvo(n.getId(), a.getId(), null);

        assertEquals(List.of(pA0.getId(), pA1.getId(), pA2.getId(), pA3.getId()),
                m.predavanja().stream().map(PrisustvoMatricaInfo.PredavanjeRef::id).toList());
        PrisustvoMatricaInfo.PredavanjeRef prvo = m.predavanja().get(1);
        assertEquals(1, prvo.rb());
        assertEquals(LocalDate.of(2088, 10, 5), prvo.datum());
        assertEquals("Uvod", prvo.tema());
        assertNull(m.predavanja().get(2).tema());

        assertEquals(List.of(n1.getId(), n2.getId(), n3.getId()),
                m.studenti().stream().map(r -> r.student().getId()).toList());
        assertEquals(Map.of(pA1.getId(), "PRISUSTVO", pA2.getId(), "ZADATAK", pA3.getId(), "SA_ZVEZDICOM"),
                m.studenti().get(0).tip());
        assertEquals(Map.of(pA1.getId(), "PRISUSTVO"), m.studenti().get(1).tip());
        assertEquals(Map.of(), m.studenti().get(2).tip());

        // školska godina 2088/89: bez pA0 (30. 9. 2088)
        PrisustvoMatricaInfo m2088 = service.prisustvo(n.getId(), a.getId(), 2088);
        assertEquals(List.of(pA1.getId(), pA2.getId(), pA3.getId()),
                m2088.predavanja().stream().map(PrisustvoMatricaInfo.PredavanjeRef::id).toList());
        assertTrue(service.prisustvo(n.getId(), a.getId(), 2090).predavanja().isEmpty());
        assertEquals(3, service.prisustvo(n.getId(), a.getId(), 2090).studenti().size());
    }

    @Test
    void matricaBezPredavanja() {
        PrisustvoMatricaInfo m = service.prisustvo(prazna.getId(), a.getId(), null);
        assertTrue(m.predavanja().isEmpty());
        assertEquals(1, m.studenti().size());
        assertTrue(m.studenti().get(0).tip().isEmpty());
    }

    @Test
    void matricaHttp() throws Exception {
        mvc.perform(get("/grupe/{id}/prisustvo", n.getId()).param("predmetId", a.getId().toString())
                        .param("godina", "2088"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.predavanja.length()").value(3))
           .andExpect(jsonPath("$.predavanja[0].id").value(pA1.getId()))
           .andExpect(jsonPath("$.predavanja[0].rb").value(1))
           .andExpect(jsonPath("$.predavanja[0].datum").value("2088-10-05"))
           .andExpect(jsonPath("$.predavanja[0].tema").value("Uvod"))
           .andExpect(jsonPath("$.studenti.length()").value(3))
           .andExpect(jsonPath("$.studenti[0].student.indeks").value("PG1"))
           .andExpect(jsonPath("$.studenti[0].tip['" + pA3.getId() + "']").value("SA_ZVEZDICOM"))
           .andExpect(jsonPath("$.studenti[2].tip.length()").value(0));
        mvc.perform(get("/grupe/{id}/prisustvo", n.getId()))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: predmetId."));
        mvc.perform(get("/grupe/{id}/prisustvo", n.getId()).param("predmetId", ""))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: predmetId."));
        mvc.perform(get("/grupe/{id}/prisustvo", n.getId()).param("predmetId", a.getId().toString())
                        .param("godina", "1"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: godina."));
        mvc.perform(get("/grupe/{id}/prisustvo", 99_999_999L).param("predmetId", a.getId().toString()))
           .andExpect(status().isNotFound());
        mvc.perform(get("/grupe/{id}/prisustvo", n.getId()).param("predmetId", "99999999"))
           .andExpect(status().isNotFound());
    }

    // ================================================================== S2

    @Test
    void karticeStudenta() {
        List<StudentPredmetKarticaInfo> k = ocenjivanje.karticeStudenta(n1.getId());
        assertEquals(List.of(b.getId(), c.getId(), a.getId()), k.stream().map(x -> x.predmet().getId()).toList());

        StudentPredmetKarticaInfo ka = k.get(2);
        assertEquals("Statika PG", ka.predmet().getNaziv());
        assertEquals(3, ka.prisutan());
        assertEquals(4, ka.predavanja());                 // pA0-pA3
        assertEquals(1, ka.zadaci());
        assertEquals(1, ka.zvezdice());
        assertEquals(2, ka.domaciUradjeno());
        assertEquals(2, ka.domaciUkupno());
        assertEquals(8.5, ka.domaciProsek(), D);
        assertEquals(List.of(k1.getId(), k2.getId()), ka.testovi().stream().map(t -> t.getTipTesta().getId()).toList());
        // aktivnost 7 od 3 * 7 -> 10; domaći 18.2 od 2 * 10 -> 10; K1 najbolji 42 od 50 -> 30; K2 35 bez normalizacije
        double ocekivano = 7.0 / 21 * 10 + 18.2 / 20 * 10 + 42.0 / 50 * 30 + 35;
        assertEquals(ocekivano, ka.ukupno(), D);
        assertEquals(8, ka.predlogOcene());
        assertEquals(81 - ocekivano, ka.doSledeceOcene(), D);

        StudentPredmetKarticaInfo kc = k.get(1);
        assertEquals(1, kc.zadaci());
        assertEquals(0, kc.domaciUkupno());
        assertNull(kc.domaciProsek());
    }

    @Test
    void studentBezIcega() throws Exception {
        assertTrue(ocenjivanje.karticeStudenta(e1.getId()).isEmpty());
        mvc.perform(get("/studenti/{id}/predmeti", e1.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(0));

        // n3 nema ništa svoje, ali grupa ima nastavu na sva tri predmeta
        List<StudentPredmetKarticaInfo> k = ocenjivanje.karticeStudenta(n3.getId());
        assertEquals(3, k.size());
        StudentPredmetKarticaInfo ka = k.get(2);
        assertEquals(0, ka.prisutan());
        assertEquals(4, ka.predavanja());
        assertEquals(0, ka.domaciUradjeno());
        assertEquals(2, ka.domaciUkupno());
        assertNull(ka.domaciProsek());
        assertEquals(0.0, ka.ukupno());
        assertNull(ka.predlogOcene());
        assertEquals(51.0, ka.doSledeceOcene());
        assertTrue(ka.testovi().stream().allMatch(t -> t.getOstvarenoPoena() == 0.0));

        mvc.perform(get("/studenti/{id}/predmeti", n3.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[2].predmet.naziv").value("Statika PG"))
           .andExpect(jsonPath("$[2].domaciProsek").value(nullValue()))
           .andExpect(jsonPath("$[2].predlogOcene").value(nullValue()))
           .andExpect(jsonPath("$[2].doSledeceOcene").value(51.0))
           .andExpect(jsonPath("$[2].ukupno").value(0.0));
        mvc.perform(get("/studenti/{id}/predmeti", 99_999_999L))
           .andExpect(status().isNotFound());
    }

    @Test
    void ponovacImaKarticuSamoZaPredmetSaTragom() {
        List<StudentPredmetKarticaInfo> k = ocenjivanje.karticeStudenta(o1.getId());
        assertEquals(List.of(a.getId()), k.stream().map(x -> x.predmet().getId()).toList());
        StudentPredmetKarticaInfo ka = k.get(0);
        assertEquals(2, ka.prisutan());                   // pO1 i pA2 grupe N
        assertEquals(2, ka.predavanja());
        assertEquals(1, ka.domaciUradjeno());
        assertEquals(1, ka.domaciUkupno());
        assertEquals(5.0, ka.domaciProsek(), D);
    }

    @Test
    void karticaNeUpisujeKoeficijente() {
        ocenjivanje.karticeStudenta(n2.getId());
        em.flush();
        assertEquals(0L, em.createQuery("select count(k) from KoeficijentiOcenjivanja k where k.predmet.id = :p", Long.class)
                .setParameter("p", b.getId()).getSingleResult());
    }

    /** Kartice preko HTTP-a daju isto ukupno, predlog i poene po tipu kao POST rezultati za grupu, za svakog studenta. */
    @Test
    void karticePoklapajuRezultateOcenjivanja() throws Exception {
        int poredjeno = 0;
        for (Grupa g : List.of(n, o)) {
            for (Predmet p : List.of(a, b, c)) {
                String rezultati = mvc.perform(post("/ocenjivanje/predmet/{id}/rezultati", p.getId())
                                .contentType(MediaType.APPLICATION_JSON).content("{\"grupaId\":" + g.getId() + "}"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                List<Map<String, Object>> redovi = JsonPath.read(rezultati, "$");
                for (Map<String, Object> red : redovi) {
                    Number studentId = JsonPath.read(red, "$.studentInfo.id");
                    String kartice = mvc.perform(get("/studenti/{id}/predmeti", studentId.longValue()))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    List<Map<String, Object>> kartica = JsonPath.read(kartice, "$[?(@.predmet.id == " + p.getId() + ")]");
                    if (kartica.isEmpty()) {
                        // nema kartice samo kad ni grupa ni student nemaju ništa na predmetu: tada je i rezultat 0
                        assertEquals(0.0, broj(red.get("ukupno")), "student " + studentId + ", predmet " + p.getNaziv());
                        continue;
                    }
                    Map<String, Object> kar = kartica.get(0);
                    String opis = "student " + studentId + ", predmet " + p.getNaziv();
                    assertEquals(broj(red.get("ukupno")), broj(kar.get("ukupno")), opis);
                    assertEquals(red.get("predlogOcene"), kar.get("predlogOcene"), opis);
                    List<Number> poeniRez = JsonPath.read(red, "$.rezultati[*].ostvarenoPoena");
                    List<Number> poeniKar = JsonPath.read(kar, "$.testovi[*].ostvarenoPoena");
                    assertEquals(poeniRez.stream().map(Number::doubleValue).toList(),
                            poeniKar.stream().map(Number::doubleValue).toList(), opis);
                    poredjeno++;
                }
            }
        }
        // N: 3 studenta × 3 predmeta; O: 2 studenta × A
        assertEquals(11, poredjeno);
    }

    // ================================================================== pomoćno

    private static double broj(Object o) {
        return ((Number) o).doubleValue();
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }

    private Predmet predmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv);
        em.persist(p);
        return p;
    }

    private Grupa grupa(String naziv, int godinaUpisa) {
        Grupa g = new Grupa();
        g.setNaziv(naziv);
        g.setGodinaUpisa(godinaUpisa);
        em.persist(g);
        return g;
    }

    private Student student(String indeks, Grupa g) {
        Student s = new Student();
        s.setIme("Ime" + indeks);
        s.setPrezime("Prezime" + indeks);
        s.setIndeks(indeks);
        s.setGodina(g.getGodinaUpisa());
        s.setGrupa(g);
        em.persist(s);
        return s;
    }

    private Predavanje predavanje(Predmet p, Grupa g, int rb, LocalDate datum, String tema) {
        Predavanje pr = new Predavanje();
        pr.setPredmet(p);
        pr.setGrupa(g);
        pr.setRb(rb);
        pr.setDatum(datum);
        pr.setTema(tema);
        pr.setZavrseno(true);
        em.persist(pr);
        return pr;
    }

    private void aktivnost(Predavanje p, Student s, TipAktivnosti tip) {
        em.persist(new Aktivnost(p, s, tip));
    }

    private Domaci domaci(Predmet p, Grupa g, LocalDate datum) {
        Domaci d = new Domaci();
        d.setPredmet(p);
        d.setGrupa(g);
        d.setDatum(datum);
        d.setNaslov("Domaći " + datum);
        d.setPregledan(true);
        em.persist(d);
        return d;
    }

    private void uradjen(Domaci d, Student s, int bodovi, boolean oslobodjen) {
        UradjenDomaci u = new UradjenDomaci();
        u.setDomaci(d);
        u.setStudent(s);
        u.setBodovi(bodovi);
        u.setOslobodjen(oslobodjen);
        em.persist(u);
    }

    private TipTesta tip(String naziv, Predmet p) {
        TipTesta t = new TipTesta(naziv, p);
        em.persist(t);
        return t;
    }

    private tri.novica.gfssystem.entity.Test test(TipTesta tip, Predmet p, Grupa g, LocalDate datum, int maxPoena) {
        tri.novica.gfssystem.entity.Test t = new tri.novica.gfssystem.entity.Test();
        t.setTipTesta(tip);
        t.setPredmet(p);
        t.setGrupa(g);
        t.setDatum(datum);
        t.setMaxPoena(maxPoena);
        t.setPregledan(true);
        em.persist(t);
        return t;
    }

    private void polaganje(tri.novica.gfssystem.entity.Test t, Student s, Double poeni) {
        Polaganje p = Polaganje.defaultPolaganje(t, s);
        p.setOstvareniPoeni(poeni);
        p.setPolozio(true);
        em.persist(p);
    }

    private OnboardingSesija sesija(Grupa g, String token, boolean aktivna, LocalDateTime kreirano, LocalDateTime istice) {
        OnboardingSesija s = new OnboardingSesija();
        s.setGrupa(g);
        s.setToken(token);
        s.setAktivna(aktivna);
        s.setKreirano(kreirano);
        s.setIstice(istice);
        s.setMaxPrijava(200);
        em.persist(s);
        return s;
    }
}
