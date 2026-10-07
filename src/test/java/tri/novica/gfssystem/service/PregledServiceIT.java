package tri.novica.gfssystem.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.domaci.DomaciListItem;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.pregled.*;
import tri.novica.gfssystem.dto.predavanje.PredavanjeListItem;
import tri.novica.gfssystem.dto.student.StudentListItem;
import tri.novica.gfssystem.dto.test.TestListItem;
import tri.novica.gfssystem.entity.*;

import java.time.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Kontrolna tabla i globalna pretraga nad pravom MySQL bazom; svaki test se vraća unazad. Sat je zamenjen
 * podesivim ({@link PodesivSat}), a podaci su u 2087. godini, daleko od podataka drugih testova: "danas" je
 * sreda 5. 3. 2087, tekuća nedelja je ponedeljak 3. 3. - nedelja 9. 3. 2087.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PregledServiceIT {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDate PONEDELJAK = LocalDate.of(2087, 3, 3);
    static final LocalDate SREDA = PONEDELJAK.plusDays(2);
    static final LocalDate NEDELJA = PONEDELJAK.plusDays(6);

    /** Clock čije se vreme menja iz testa; {@code @Primary} pa ga dobijaju svi servisi umesto sistemskog. */
    static class PodesivSat extends Clock {
        private Instant sada = Instant.EPOCH;

        void postavi(LocalDateTime vreme) {
            sada = vreme.atZone(ZONA).toInstant();
        }

        @Override public ZoneId getZone() { return ZONA; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(sada, zone); }
        @Override public Instant instant() { return sada; }
    }

    @TestConfiguration
    static class SatKonfiguracija {
        @Bean
        @Primary
        PodesivSat podesivSat() {
            return new PodesivSat();
        }
    }

    @Autowired PodesivSat sat;
    @Autowired PregledService service;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    Predmet mat, fiz;
    Grupa nova, stara, najstarija;
    int rb = 87000;

    @BeforeEach
    void pripremi() {
        sat.postavi(SREDA.atTime(10, 0));
        mat = predmet("Matematika PSIT");
        fiz = predmet("Fizika PSIT");
        nova = grupa("PS-2087", 2087);
        stara = grupa("PS-2086", 2086);
        najstarija = grupa("PS-2085", 2085);
    }

    // ================================================================== prazna baza

    @Test
    void praznaBazaDajePraznaPoljaBezIzuzetka() throws Exception {
        obrisiSveStoTablaCita();

        KontrolnaTablaInfo t = service.kontrolnaTabla();
        assertNull(t.sledece());
        assertTrue(t.uToku().isEmpty());
        assertTrue(t.ceka().testovi().isEmpty());
        assertTrue(t.ceka().domaci().isEmpty());
        assertTrue(t.ceka().prijave().isEmpty());
        assertTrue(t.ceka().nezavrsena().isEmpty());
        assertTrue(t.nedelja().isEmpty());

        mvc.perform(get("/pregled/kontrolna-tabla"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sledece").value(nullValue()))
           .andExpect(jsonPath("$.uToku.length()").value(0))
           .andExpect(jsonPath("$.ceka.testovi.length()").value(0))
           .andExpect(jsonPath("$.ceka.domaci.length()").value(0))
           .andExpect(jsonPath("$.ceka.prijave.length()").value(0))
           .andExpect(jsonPath("$.ceka.nezavrsena.length()").value(0))
           .andExpect(jsonPath("$.nedelja.length()").value(0));
    }

    // ================================================================== sledeće predavanje

    @Test
    void sledeceJePoslednjePredavanjeSaRbPlusJedan() {
        predavanje(fiz, nova, 12, SREDA.minusDays(7), true);
        predavanje(mat, nova, 13, SREDA.minusDays(1), true);
        predavanje(fiz, stara, 40, SREDA.minusDays(1), true);   // isti dan kao "poslednje", ali svejedno starije od danas
        Predavanje pobednik = predavanje(mat, nova, 14, SREDA, true);

        // studenti nove grupe
        Student n1 = student("N1", nova);
        student("N2", nova);
        // stariji koji su se pojavili na matematici: aktivnost, polaganje, oba (broji se jednom)
        Student a = student("S1", stara);
        Student b = student("S2", najstarija);
        Student c = student("S3", stara);
        // stariji bez traga na matematici: samo fizika, ili ništa
        Student d = student("S4", stara);
        student("S5", stara);
        Student bezGrupe = student("S6", null);
        // ponovac sa aktivnošću i polaganjem samo u prošloj školskoj godini (2085/86) se ne broji
        Student prosleGodine = student("S7", stara);

        // tekuća školska godina je 2086/87 (1. 10. 2086 - 30. 9. 2087)
        Predavanje staroMat = predavanje(mat, stara, 5, LocalDate.of(2086, 10, 1), true);
        Predavanje staroFiz = predavanje(fiz, stara, 6, LocalDate.of(2086, 11, 2), true);
        Predavanje proslaGodinaMat = predavanje(mat, stara, 7, LocalDate.of(2086, 9, 30), true);
        aktivnost(staroMat, a);
        aktivnost(staroMat, c);
        aktivnost(staroMat, bezGrupe);
        aktivnost(pobednik, n1);    // ista grupa: nije "stariji"
        aktivnost(staroFiz, d);
        TipTesta kol = tip("Kolokvijum PSIT", mat);
        tri.novica.gfssystem.entity.Test testMat = test(kol, mat, stara, LocalDate.of(2087, 9, 30), true);
        polaganje(testMat, b);
        polaganje(testMat, c);
        aktivnost(proslaGodinaMat, prosleGodine);
        polaganje(test(kol, mat, stara, LocalDate.of(2086, 9, 30), true), prosleGodine);
        flushClear();

        SledecePredavanjeInfo s = service.kontrolnaTabla().sledece();
        assertNotNull(s);
        assertEquals(mat.getId(), s.predmet().getId());
        assertEquals("Matematika PSIT", s.predmet().getNaziv());
        assertEquals(nova.getId(), s.grupa().getId());
        assertEquals("PS-2087", s.grupa().getNaziv());
        assertEquals(2087, s.grupa().getGodinaUpisa());
        assertEquals(15, s.rb());
        assertEquals(2, s.brojStudenata());
        assertEquals(2, s.grupa().getBrojStudenata());
        assertEquals(3, s.brojStarijih());   // a (aktivnost), b (polaganje), c (oba); ne S7 (prošla godina)
    }

    @Test
    void sledeceIstiDatumOdlucujeVeciId() {
        predavanje(mat, nova, 3, SREDA, true);
        predavanje(fiz, stara, 8, SREDA, true);
        flushClear();
        SledecePredavanjeInfo s = service.kontrolnaTabla().sledece();
        assertEquals(fiz.getId(), s.predmet().getId());
        assertEquals(stara.getId(), s.grupa().getId());
        assertEquals(9, s.rb());
    }

    @Test
    void sledeceZaPredavanjeBezGrupe() {
        predavanje(mat, null, 7, SREDA, true);
        flushClear();
        SledecePredavanjeInfo s = service.kontrolnaTabla().sledece();
        assertEquals(mat.getId(), s.predmet().getId());
        assertNull(s.grupa());
        assertEquals(8, s.rb());
        assertEquals(0, s.brojStudenata());
        assertEquals(0, s.brojStarijih());
    }

    // ================================================================== u toku / nezavršena

    @Test
    void uTokuSuDanasnjaNezavrsenaANezavrsenaStarijaOdDanas() {
        Predavanje danasOtvoreno = predavanje(mat, nova, 1, SREDA, false);
        Predavanje danasZavrseno = predavanje(mat, stara, 2, SREDA, true);
        Predavanje juceOtvoreno = predavanje(fiz, nova, 3, SREDA.minusDays(1), false);
        Predavanje staroNull = predavanje(fiz, stara, 4, SREDA.minusDays(20), null);   // stari red: zavrseno = null
        Predavanje juceZavrseno = predavanje(fiz, najstarija, 5, SREDA.minusDays(1), true);
        Predavanje sutraOtvoreno = predavanje(mat, najstarija, 6, SREDA.plusDays(1), false);
        Set<Long> nasa = Set.of(danasOtvoreno.getId(), danasZavrseno.getId(), juceOtvoreno.getId(), staroNull.getId(),
                juceZavrseno.getId(), sutraOtvoreno.getId());
        flushClear();

        KontrolnaTablaInfo t = service.kontrolnaTabla();
        assertEquals(List.of(danasOtvoreno.getId()), idPredavanja(t.uToku(), nasa));
        assertEquals(List.of(juceOtvoreno.getId(), staroNull.getId()), idPredavanja(t.ceka().nezavrsena(), nasa));
        PredavanjeListItem u = t.uToku().getFirst();
        assertEquals("PS-2087", u.getGrupa().getNaziv());
        assertEquals(mat.getId(), u.getPredmet().getId());
    }

    @Test
    void nezavrsenaNajvise10NajnovijaPrva() {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            ids.add(predavanje(mat, nova, i, SREDA.minusDays(i), false).getId());
        }
        flushClear();
        List<PredavanjeListItem> r = service.kontrolnaTabla().ceka().nezavrsena();
        assertEquals(10, r.size());
        assertEquals(ids.subList(0, 10), r.stream().map(PredavanjeListItem::getId).toList());
    }

    // ================================================================== čeka na tebe

    @Test
    void cekaTestoviIDomaciNepregledani() {
        TipTesta kol = tip("Kolokvijum PSIT", mat);
        tri.novica.gfssystem.entity.Test tFalse = test(kol, mat, nova, SREDA.minusDays(2), false);
        tri.novica.gfssystem.entity.Test tNull = test(kol, mat, nova, SREDA.minusDays(3), null);
        tri.novica.gfssystem.entity.Test tTrue = test(kol, mat, nova, SREDA.minusDays(1), true);
        Domaci dFalse = domaci(mat, nova, SREDA.minusDays(2), "DZ1", false);
        Domaci dNull = domaci(mat, null, SREDA.minusDays(3), "DZ2", null);
        Domaci dTrue = domaci(mat, nova, SREDA.minusDays(1), "DZ3", true);
        Set<Long> nasiT = Set.of(tFalse.getId(), tNull.getId(), tTrue.getId());
        Set<Long> nasiD = Set.of(dFalse.getId(), dNull.getId(), dTrue.getId());
        flushClear();

        KontrolnaTablaInfo.Ceka c = service.kontrolnaTabla().ceka();
        assertEquals(List.of(tFalse.getId(), tNull.getId()),
                c.testovi().stream().map(TestListItem::getId).filter(nasiT::contains).toList());
        assertEquals(List.of(dFalse.getId(), dNull.getId()),
                c.domaci().stream().map(DomaciListItem::getId).filter(nasiD::contains).toList());
        DomaciListItem bezGrupe = c.domaci().stream().filter(i -> i.getId().equals(dNull.getId())).findFirst().orElseThrow();
        assertNull(bezGrupe.getGrupa());
    }

    @Test
    void cekaListeNajvise10() {
        TipTesta kol = tip("Kolokvijum PSIT", mat);
        for (int i = 1; i <= 12; i++) {
            test(kol, mat, nova, SREDA.minusDays(i), false);
            domaci(mat, nova, SREDA.minusDays(i), "DZ" + i, false);
        }
        flushClear();
        KontrolnaTablaInfo.Ceka c = service.kontrolnaTabla().ceka();
        assertEquals(10, c.testovi().size());
        assertEquals(10, c.domaci().size());
        assertEquals(SREDA.minusDays(1), c.testovi().getFirst().getDatum());
    }

    @Test
    void cekaPrijaveSamoSesijeSaPrijavamaNaCekanju() {
        OnboardingSesija saCekanjem = sesija(nova, "psit000000000000000000000000001", SREDA.plusDays(5).atTime(10, 0));
        OnboardingSesija istekla = sesija(stara, "psit000000000000000000000000002", SREDA.minusDays(1).atTime(9, 0));
        OnboardingSesija obradjena = sesija(najstarija, "psit000000000000000000000000003", SREDA.plusDays(1).atTime(9, 0));
        prijava(saCekanjem, "PS1", StatusPrijave.NA_CEKANJU);
        prijava(saCekanjem, "PS2", StatusPrijave.NA_CEKANJU);
        prijava(saCekanjem, "PS3", StatusPrijave.ODBIJENA);
        prijava(istekla, "PS4", StatusPrijave.NA_CEKANJU);   // istekla sesija i dalje ima neobrađenu prijavu
        prijava(obradjena, "PS5", StatusPrijave.ODBIJENA);
        Set<Long> nase = Set.of(saCekanjem.getId(), istekla.getId(), obradjena.getId());
        flushClear();

        List<CekaStavkaInfo> p = service.kontrolnaTabla().ceka().prijave().stream()
                .filter(s -> nase.contains(s.sesijaId())).toList();
        assertEquals(List.of(istekla.getId(), saCekanjem.getId()), p.stream().map(CekaStavkaInfo::sesijaId).toList());
        CekaStavkaInfo s = p.get(1);
        assertEquals(2, s.brojNaCekanju());
        assertEquals(SREDA.plusDays(5).atTime(10, 0), s.istice());
        assertEquals(nova.getId(), s.grupa().getId());
        assertEquals("PS-2087", s.grupa().getNaziv());
        assertEquals(1, p.get(0).brojNaCekanju());
    }

    @Test
    void cekaSamoTekucaSkolskaGodinaIVecOdrzano() {
        TipTesta kol = tip("Kolokvijum PSIT", mat);
        LocalDate proslaGodina = LocalDate.of(2086, 9, 30);
        LocalDate pocetakGodine = LocalDate.of(2086, 10, 1);
        tri.novica.gfssystem.entity.Test tProsla = test(kol, mat, nova, proslaGodina, false);
        tri.novica.gfssystem.entity.Test tPocetak = test(kol, mat, nova, pocetakGodine, false);
        tri.novica.gfssystem.entity.Test tDanas = test(kol, mat, nova, SREDA, false);
        tri.novica.gfssystem.entity.Test tSutra = test(kol, mat, nova, SREDA.plusDays(1), false);
        Domaci dProsla = domaci(mat, nova, proslaGodina, "Prošla", false);
        Domaci dPocetak = domaci(mat, nova, pocetakGodine, "Početak", false);
        Domaci dDanas = domaci(mat, nova, SREDA, "Danas", false);
        Domaci dSutra = domaci(mat, nova, SREDA.plusDays(1), "Sutra", null);
        Predavanje pProsla = predavanje(mat, stara, 1, proslaGodina, false);
        Predavanje pPocetak = predavanje(mat, stara, 2, pocetakGodine, false);
        Set<Long> nasiT = Set.of(tProsla.getId(), tPocetak.getId(), tDanas.getId(), tSutra.getId());
        Set<Long> nasiD = Set.of(dProsla.getId(), dPocetak.getId(), dDanas.getId(), dSutra.getId());
        Set<Long> nasaP = Set.of(pProsla.getId(), pPocetak.getId());
        flushClear();

        KontrolnaTablaInfo t = service.kontrolnaTabla();
        assertEquals(List.of(tDanas.getId(), tPocetak.getId()),
                t.ceka().testovi().stream().map(TestListItem::getId).filter(nasiT::contains).toList());
        assertEquals(List.of(dDanas.getId(), dPocetak.getId()),
                t.ceka().domaci().stream().map(DomaciListItem::getId).filter(nasiD::contains).toList());
        assertEquals(List.of(pPocetak.getId()), idPredavanja(t.ceka().nezavrsena(), nasaP));
        // budući su i dalje u "ova nedelja"
        assertTrue(t.nedelja().stream().anyMatch(s -> s.tip() == AgendaStavkaInfo.Tip.TEST && s.id().equals(tSutra.getId())));
        assertTrue(t.nedelja().stream().anyMatch(s -> s.tip() == AgendaStavkaInfo.Tip.DOMACI && s.id().equals(dSutra.getId())));
    }

    @Test
    void cekaGranicaSkolskeGodine30Septembar1Oktobar() {
        TipTesta kol = tip("Kolokvijum PSIT", mat);
        LocalDate kraj = LocalDate.of(2087, 9, 30);
        LocalDate pocetak = LocalDate.of(2087, 10, 1);
        tri.novica.gfssystem.entity.Test tKraj = test(kol, mat, nova, kraj, false);
        tri.novica.gfssystem.entity.Test tPocetak = test(kol, mat, nova, pocetak, false);
        Domaci dKraj = domaci(mat, nova, kraj, "Kraj", false);
        Domaci dPocetak = domaci(mat, nova, pocetak, "Početak", false);
        Predavanje pKraj = predavanje(mat, nova, 1, kraj, false);
        Predavanje pPrethodni = predavanje(mat, nova, 2, kraj.minusDays(1), false);
        Set<Long> nasiT = Set.of(tKraj.getId(), tPocetak.getId());
        Set<Long> nasiD = Set.of(dKraj.getId(), dPocetak.getId());
        Set<Long> nasaP = Set.of(pKraj.getId(), pPrethodni.getId());
        flushClear();

        // 30. 9. 23:59: još 2086/87; 1. 10. je budućnost
        sat.postavi(kraj.atTime(23, 59, 59));
        KontrolnaTablaInfo t = service.kontrolnaTabla();
        assertEquals(List.of(tKraj.getId()), t.ceka().testovi().stream().map(TestListItem::getId).filter(nasiT::contains).toList());
        assertEquals(List.of(dKraj.getId()), t.ceka().domaci().stream().map(DomaciListItem::getId).filter(nasiD::contains).toList());
        assertEquals(List.of(pPrethodni.getId()), idPredavanja(t.ceka().nezavrsena(), nasaP));
        assertEquals(List.of(pKraj.getId()), idPredavanja(t.uToku(), nasaP));

        // 1. 10. 00:00: nova školska godina 2087/88; sve od 30. 9. ispada iz "čeka"
        sat.postavi(pocetak.atStartOfDay());
        t = service.kontrolnaTabla();
        assertEquals(List.of(tPocetak.getId()), t.ceka().testovi().stream().map(TestListItem::getId).filter(nasiT::contains).toList());
        assertEquals(List.of(dPocetak.getId()), t.ceka().domaci().stream().map(DomaciListItem::getId).filter(nasiD::contains).toList());
        assertEquals(List.of(), idPredavanja(t.ceka().nezavrsena(), nasaP));
    }

    @Test
    void brojStarijihGranicaSkolskeGodine() {
        Predavanje poslednje = predavanje(mat, nova, 1, LocalDate.of(2087, 10, 1), true);
        Student naKraju = student("K1", stara);
        Student naPocetku = student("K2", stara);
        aktivnost(predavanje(mat, stara, 1, LocalDate.of(2087, 9, 30), true), naKraju);
        aktivnost(poslednje, naPocetku);   // stariji na predavanju nove grupe
        flushClear();

        sat.postavi(LocalDate.of(2087, 9, 30).atTime(23, 59, 59));
        assertEquals(1, service.kontrolnaTabla().sledece().brojStarijih());   // 2086/87: samo K1
        sat.postavi(LocalDate.of(2087, 10, 1).atStartOfDay());
        assertEquals(1, service.kontrolnaTabla().sledece().brojStarijih());   // 2087/88: samo K2
    }

    // ================================================================== ova nedelja

    @Test
    void nedeljaOdPonedeljka0000DoNedelje2359() {
        Predavanje prethodnaNedelja = predavanje(mat, nova, 1, PONEDELJAK.minusDays(1), true);
        Predavanje ponedeljak = predavanje(mat, nova, 2, PONEDELJAK, true);
        Predavanje nedelja = predavanje(fiz, nova, 3, NEDELJA, false);
        Predavanje sledecaNedelja = predavanje(fiz, nova, 4, NEDELJA.plusDays(1), false);
        Domaci dz = domaci(mat, nova, PONEDELJAK, "Integrali", false);
        Domaci dzBezGrupe = domaci(fiz, null, SREDA, null, false);
        Domaci dzVan = domaci(mat, nova, NEDELJA.plusDays(1), "Van", false);
        TipTesta kol = tip("Kolokvijum PSIT", mat);
        tri.novica.gfssystem.entity.Test test = test(kol, mat, nova, PONEDELJAK, false);
        tri.novica.gfssystem.entity.Test testVan = test(kol, mat, nova, PONEDELJAK.minusDays(1), false);
        flushClear();

        for (LocalDateTime trenutak : List.of(PONEDELJAK.atStartOfDay(), NEDELJA.atTime(23, 59, 59))) {
            sat.postavi(trenutak);
            List<AgendaStavkaInfo> n = service.kontrolnaTabla().nedelja();
            Set<String> kljucevi = new HashSet<>();
            n.forEach(s -> kljucevi.add(s.tip() + ":" + s.id()));
            assertEquals(Set.of("PREDAVANJE:" + ponedeljak.getId(), "PREDAVANJE:" + nedelja.getId(),
                    "DOMACI:" + dz.getId(), "DOMACI:" + dzBezGrupe.getId(), "TEST:" + test.getId()), kljucevi,
                    "nedelja za trenutak " + trenutak);
            assertFalse(kljucevi.contains("PREDAVANJE:" + prethodnaNedelja.getId()));
            assertFalse(kljucevi.contains("PREDAVANJE:" + sledecaNedelja.getId()));
            assertFalse(kljucevi.contains("DOMACI:" + dzVan.getId()));
            assertFalse(kljucevi.contains("TEST:" + testVan.getId()));

            // redosled: datum, pa predavanje, domaći, test
            assertEquals(List.of(AgendaStavkaInfo.Tip.PREDAVANJE, AgendaStavkaInfo.Tip.DOMACI, AgendaStavkaInfo.Tip.TEST,
                    AgendaStavkaInfo.Tip.DOMACI, AgendaStavkaInfo.Tip.PREDAVANJE), n.stream().map(AgendaStavkaInfo::tip).toList());
            AgendaStavkaInfo p = n.getFirst();
            assertEquals("Predavanje 2", p.naslov());
            assertEquals(PONEDELJAK, p.datum());
            assertEquals(mat.getId(), p.predmet().getId());
            assertEquals(nova.getId(), p.grupa().getId());
            assertEquals("Integrali", n.get(1).naslov());
            assertEquals("Kolokvijum PSIT", n.get(2).naslov());
            AgendaStavkaInfo bezGrupe = n.get(3);
            assertNull(bezGrupe.grupa());
            assertEquals("Domaći", bezGrupe.naslov());
        }
        sat.postavi(NEDELJA.plusDays(1).atStartOfDay());
        List<AgendaStavkaInfo> sledeca = service.kontrolnaTabla().nedelja();
        assertEquals(Set.of("PREDAVANJE:" + sledecaNedelja.getId(), "DOMACI:" + dzVan.getId()),
                new HashSet<>(sledeca.stream().map(s -> s.tip() + ":" + s.id()).toList()));
    }

    // ================================================================== pretraga

    @Test
    void pretragaKraceOdDvaZnakaJePrazna() throws Exception {
        student("GD12", nova);
        flushClear();
        for (String q : new String[]{"a", " a ", "", "   ", null}) {
            PretragaRezultatInfo r = service.pretraga(q);
            assertTrue(r.studenti().isEmpty(), "q=" + q);
            assertTrue(r.predavanja().isEmpty());
            assertTrue(r.testovi().isEmpty());
            assertTrue(r.grupe().isEmpty());
        }
        mvc.perform(get("/pretraga").param("q", "a"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.studenti.length()").value(0))
           .andExpect(jsonPath("$.predavanja.length()").value(0))
           .andExpect(jsonPath("$.testovi.length()").value(0))
           .andExpect(jsonPath("$.grupe.length()").value(0));
        mvc.perform(get("/pretraga")).andExpect(status().isOk()).andExpect(jsonPath("$.studenti.length()").value(0));
    }

    @Test
    void pretragaGd12NalaziStudenta() throws Exception {
        Student gd12 = student("GD12", nova);
        Student gd12Razmak = student("GD 12", stara);   // star red sa razmakom u indeksu
        student("GD13", nova);
        flushClear();

        List<Long> ids = service.pretraga("gd12").studenti().stream().map(StudentListItem::getId).toList();
        assertTrue(ids.contains(gd12.getId()));
        assertTrue(ids.contains(gd12Razmak.getId()));
        assertEquals(2, ids.stream().filter(id -> id.equals(gd12.getId()) || id.equals(gd12Razmak.getId())).count());

        mvc.perform(get("/pretraga").param("q", "GD12"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.studenti[?(@.indeks == 'GD12')].grupa.naziv").value("PS-2087"));
    }

    @Test
    void pretragaPoSvimGrupamaINajvise5() {
        for (int i = 1; i <= 6; i++) {
            student("PSQ" + i, nova);
            predavanje(mat, nova, i, SREDA.minusDays(i), true).setTema("Tema psq " + i);
            test(tip("Psq kolokvijum " + i, mat), mat, nova, SREDA.minusDays(i), false);
            grupa("PSQ-" + i, 2080 + i);
        }
        Grupa nadjena = grupa("Psq_x%", 2070);   // % i _ su doslovni
        flushClear();

        PretragaRezultatInfo r = service.pretraga("psq");
        assertEquals(5, r.studenti().size());
        assertEquals(5, r.predavanja().size());
        assertEquals(5, r.testovi().size());
        assertEquals(5, r.grupe().size());
        assertEquals("PSQ-6", r.grupe().getFirst().getNaziv());   // najnovija generacija prva
        assertEquals(0L, r.grupe().getFirst().getBrojStudenata());
        assertTrue(r.predavanja().stream().allMatch(p -> p.getTema().startsWith("Tema psq")));
        assertTrue(r.testovi().stream().allMatch(t -> t.getTipTesta().getNaziv().startsWith("Psq kolokvijum")));

        List<GrupaInfo> doslovno = service.pretraga("_x%").grupe();
        assertEquals(List.of(nadjena.getId()), doslovno.stream().map(GrupaInfo::getId).toList());
        assertEquals(List.of(nova.getId()), service.pretraga("ps-2087").grupe().stream().map(GrupaInfo::getId).toList());
        assertEquals(6L, service.pretraga("ps-2087").grupe().getFirst().getBrojStudenata());
    }

    @Test
    void kontrolnaTablaJsonOblik() throws Exception {
        Predavanje danas = predavanje(mat, nova, 13, SREDA, false);
        danas.setTema("Integrali");
        student("GD12", nova);
        TipTesta kol = tip("Kolokvijum PSIT", mat);
        test(kol, mat, nova, PONEDELJAK, false);
        domaci(mat, nova, SREDA.minusDays(1), "Izvodi", false);
        predavanje(fiz, stara, 3, SREDA.minusDays(2), false);
        OnboardingSesija s = sesija(nova, "psit000000000000000000000000009", SREDA.plusDays(7).atTime(10, 0));
        prijava(s, "PS9", StatusPrijave.NA_CEKANJU);
        flushClear();

        String json = mvc.perform(get("/pregled/kontrolna-tabla"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sledece.predmet.naziv").value("Matematika PSIT"))
           .andExpect(jsonPath("$.sledece.grupa.naziv").value("PS-2087"))
           .andExpect(jsonPath("$.sledece.grupa.godinaUpisa").value(2087))
           .andExpect(jsonPath("$.sledece.rb").value(14))
           .andExpect(jsonPath("$.sledece.brojStudenata").value(1))
           .andExpect(jsonPath("$.sledece.brojStarijih").value(0))
           .andExpect(jsonPath("$.uToku[0].tema").value("Integrali"))
           .andExpect(jsonPath("$.utoku").doesNotExist())
           .andExpect(jsonPath("$.ceka.testovi[0].tipTesta.naziv").value("Kolokvijum PSIT"))
           .andExpect(jsonPath("$.ceka.domaci[0].naslov").value("Izvodi"))
           .andExpect(jsonPath("$.ceka.prijave[0].sesijaId").value(s.getId()))
           .andExpect(jsonPath("$.ceka.prijave[0].grupa.naziv").value("PS-2087"))
           .andExpect(jsonPath("$.ceka.prijave[0].brojNaCekanju").value(1))
           .andExpect(jsonPath("$.ceka.prijave[0].istice").value("2087-03-12T10:00:00"))
           .andExpect(jsonPath("$.ceka.nezavrsena[0].rb").value(3))
           .andExpect(jsonPath("$.nedelja[0].tip").value("PREDAVANJE"))
           .andExpect(jsonPath("$.nedelja[0].datum").value("2087-03-03"))
           .andReturn().getResponse().getContentAsString();
        System.out.println("PRIMER kontrolna-tabla: " + json);
    }

    // ================================================================== pomoćno

    /** Tabla čita predavanja, testove, domaće i prijave; brisanje se vraća unazad sa testom. */
    private void obrisiSveStoTablaCita() {
        for (String tabela : List.of("prijave", "aktivnosti", "polaganja", "uradjeni_domaci", "domaci", "testovi",
                "predavanja")) {
            em.createNativeQuery("delete from " + tabela).executeUpdate();
        }
        flushClear();
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }

    private static List<Long> idPredavanja(List<PredavanjeListItem> r, Set<Long> nasa) {
        return r.stream().map(PredavanjeListItem::getId).filter(nasa::contains).toList();
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
        s.setGodina(g == null ? 2080 : g.getGodinaUpisa());
        s.setGrupa(g);
        em.persist(s);
        return s;
    }

    private Predavanje predavanje(Predmet p, Grupa g, int rbUGrupi, LocalDate datum, Boolean zavrseno) {
        Predavanje pr = new Predavanje();
        pr.setPredmet(p);
        pr.setGrupa(g);
        pr.setRb(rbUGrupi);
        pr.setDatum(datum);
        pr.setZavrseno(zavrseno);
        em.persist(pr);
        return pr;
    }

    private void aktivnost(Predavanje p, Student s) {
        em.persist(new Aktivnost(p, s, TipAktivnosti.PRISUSTVO));
    }

    private Domaci domaci(Predmet p, Grupa g, LocalDate datum, String naslov, Boolean pregledan) {
        Domaci d = new Domaci();
        d.setPredmet(p);
        d.setGrupa(g);
        d.setDatum(datum);
        d.setNaslov(naslov);
        d.setPregledan(pregledan);
        em.persist(d);
        return d;
    }

    private TipTesta tip(String naziv, Predmet p) {
        TipTesta t = new TipTesta(naziv, p);
        em.persist(t);
        return t;
    }

    private tri.novica.gfssystem.entity.Test test(TipTesta tip, Predmet p, Grupa g, LocalDate datum, Boolean pregledan) {
        tri.novica.gfssystem.entity.Test t = new tri.novica.gfssystem.entity.Test();
        t.setTipTesta(tip);
        t.setPredmet(p);
        t.setGrupa(g);
        t.setDatum(datum);
        t.setMaxPoena(50);
        t.setPregledan(pregledan);
        em.persist(t);
        return t;
    }

    private void polaganje(tri.novica.gfssystem.entity.Test t, Student s) {
        em.persist(Polaganje.defaultPolaganje(t, s));
    }

    private OnboardingSesija sesija(Grupa g, String token, LocalDateTime istice) {
        OnboardingSesija s = new OnboardingSesija();
        s.setGrupa(g);
        s.setToken(token);
        s.setAktivna(true);
        s.setKreirano(istice.minusDays(7));
        s.setIstice(istice);
        em.persist(s);
        return s;
    }

    private void prijava(OnboardingSesija s, String indeks, StatusPrijave status) {
        Prijava p = new Prijava();
        p.setSesija(s);
        p.setIme("Ime");
        p.setPrezime("Prezime");
        p.setIndeks(indeks);
        p.setGodina(2087);
        p.setEmail("x@y.rs");
        p.setBrojTelefona("060");
        p.setStatus(status);
        p.setPodneto(s.getKreirano().plusHours(1));
        em.persist(p);
    }
}
