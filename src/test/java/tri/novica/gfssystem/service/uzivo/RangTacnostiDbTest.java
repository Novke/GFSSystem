package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.utility.SkolskaGodina;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rang tačnosti nad pravom MySQL bazom (kao CI), kroz prave servise: dva izvođenja jednog predavanja (dva pitanja,
 * jedno), prijavljen student, gost sa istim imenom u oba izvođenja, propušteno pitanje, rang predavanja, kartica
 * studenta i rang grupe; projektor ({@code L}, {@code Shift+L}) i telefoni van takmičenja.
 */
@SpringBootTest
class RangTacnostiDbTest {

    @Autowired RangTacnostiService rangService;
    @Autowired UcesnikService ucesnikService;
    @Autowired OdgovorService odgovorService;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired StanjeService stanjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;

    final String oznaka = UUID.randomUUID().toString().substring(0, 8);
    final List<Long> prezentacije = new ArrayList<>();
    final List<Long> studenti = new ArrayList<>();
    Predmet predmet;
    Long grupaId;
    Long predavanjeId;
    Long ana;
    Long bojan;
    Long ceca;

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Rang tačnosti DB " + oznaka);
        predmet = predmetRepository.save(predmet);
        jdbc.update("insert into grupe (naziv, godina_upisa) values (?, 2025)", "RT-" + oznaka);
        grupaId = jdbc.queryForObject("select id from grupe where naziv = ?", Long.class, "RT-" + oznaka);
        ana = student("Ana", "Anić", "1");
        bojan = student("Bojan", "Bojić", "2");
        ceca = student("Ceca", "Cecić", "3");
        jdbc.update("insert into predavanja (predmet_id, grupa_id, rb, datum, tema, posecenost, zavrseno) "
                + "values (?, ?, 4, curdate(), 'Sile', 0, 0)", predmet.getId(), grupaId);
        predavanjeId = jdbc.queryForObject("select max(id) from predavanja where predmet_id = ?", Long.class, predmet.getId());
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", id);
            try {
                prezentacijaService.obrisi(id);
            } catch (SystemException e) {
                // već obrisana
            }
        }
        jdbc.update("delete from predavanja where id = ?", predavanjeId);
        studenti.forEach(s -> jdbc.update("delete from studenti where id = ?", s));
        jdbc.update("delete from grupe where id = ?", grupaId);
        predmetRepository.deleteById(predmet.getId());
    }

    Long student(String ime, String prezime, String indeks) {
        String pun = "RT-" + oznaka + "-" + indeks;
        jdbc.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values (?, ?, 2025, ?, ?)",
                ime, prezime, pun, grupaId);
        Long id = jdbc.queryForObject("select id from studenti where indeks = ?", Long.class, pun);
        studenti.add(id);
        return id;
    }

    /** Prezentacija sa {@code n} JEDAN_TACAN pitanja (prva opcija tačna) i jednom anketom na kraju. */
    PrezentacijaDetails prezentacija(String naziv, int n) {
        PrezentacijaDetails p = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), naziv, null, null));
        prezentacije.add(p.id());
        for (int i = 0; i < n; i++) {
            prezentacijaService.dodajSlajd(p.id(), new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false,
                    new PitanjeCmd(TipPitanja.JEDAN_TACAN, "Pitanje " + i, null, null,
                            List.of(new OpcijaCmd("Tačno", true), new OpcijaCmd("Netačno", false)),
                            null, null, null, null, null, null, null, null), null, null), null);
        }
        prezentacijaService.dodajSlajd(p.id(), new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false,
                new PitanjeCmd(TipPitanja.ANKETA, "Anketa", null, null,
                        List.of(new OpcijaCmd("Da", false), new OpcijaCmd("Ne", false)),
                        null, null, null, null, null, null, null, null), null, null), null);
        return prezentacijaService.detalji(p.id());
    }

    void k(Long id, TipKomande tip) {
        izvodjenjeService.komanda(id, new KomandaCmd(tip, null));
    }

    /** Otvara sledeći slajd-pitanje (SLEDECI do pitanja, pa otvaranje) i vraća id runde. */
    Long otvoriSledece(Long id) {
        k(id, TipKomande.SLEDECI);
        k(id, TipKomande.OTVORI_ZATVORI);
        return stanjeService.nastavnicko(id).runda().id();
    }

    void odgovori(Long iz, Long ucesnik, Long runda, Long opcija) {
        odgovorService.odgovori(iz, ucesnik, new OdgovorCmd(runda, List.of(opcija), null, null, null));
    }

    Long opcija(PrezentacijaDetails p, int slajd, int i) {
        return p.slajdovi().get(slajd).pitanje().opcije().get(i).id();
    }

    @Test
    void izvodjenjePredavanjeStudentIGrupa() {
        // izvođenje 1: dva pitanja; Ana (student) 2/2, Bojan (student) 1/2, gost "Mile" propušta drugo
        PrezentacijaDetails p1 = prezentacija("Prva", 2);
        IzvodjenjeInfo i1 = izvodjenjeService.pokreni(p1.id(), new PokreniCmd(true, null, predavanjeId));
        Long u1Ana = ucesnikService.prijavi(i1.kod(), "x", Optional.of(ana)).info().ucesnikId();
        Long u1Bojan = ucesnikService.prijavi(i1.kod(), "x", Optional.of(bojan)).info().ucesnikId();
        Long u1Mile = ucesnikService.prijavi(i1.kod(), "Mile").info().ucesnikId();

        Long r1 = otvoriSledece(i1.id());
        odgovori(i1.id(), u1Ana, r1, opcija(p1, 0, 0));
        odgovori(i1.id(), u1Bojan, r1, opcija(p1, 0, 1));
        odgovori(i1.id(), u1Mile, r1, opcija(p1, 0, 0));
        k(i1.id(), TipKomande.OTVORI_ZATVORI);
        Long r2 = otvoriSledece(i1.id());
        odgovori(i1.id(), u1Ana, r2, opcija(p1, 1, 0));
        odgovori(i1.id(), u1Bojan, r2, opcija(p1, 1, 0));

        // konzola: otvorena runda 2 se Miletu ne računa dok se ne zatvori
        Map<String, RangTacnostiStavka> rang = poImenu(rangService.zaIzvodjenje(i1.id()));
        assertEquals(1, rang.get("Mile").dugovanih());
        k(i1.id(), TipKomande.OTVORI_ZATVORI);
        rang = poImenu(rangService.zaIzvodjenje(i1.id()));
        assertEquals(List.of(2, 1, 2), List.of(rang.get("Ana Anić").tacnih(), rang.get("Bojan Bojić").tacnih(),
                rang.get("Mile").dugovanih()));
        assertEquals(1, rang.get("Ana Anić").mesto());
        assertEquals(100, rang.get("Ana Anić").procenat());
        assertEquals(2, rang.get("Ana Anić").zvezdice());
        assertEquals(ana, rang.get("Ana Anić").studentId());
        assertTrue(rang.get("Mile").gost());
        assertNull(rang.get("Ana Anić").poeni()); // nije takmičenje

        // projektor van takmičenja: L daje rang tačnosti, telefoni ne dobijaju rang-listu
        k(i1.id(), TipKomande.RANG_LISTA);
        NastavnickoStanje st = stanjeService.nastavnicko(i1.id());
        assertEquals(RangPrikaz.IZVODJENJE, st.rangPrikaz());
        assertNull(st.javnaRangLista());
        assertEquals("Ana A.", st.javniRangTacnosti().getFirst().ime(), "projektor: kratko ime");
        assertNull(st.javniRangTacnosti().getFirst().studentId());
        assertNull(stanjeService.javno(i1.id()).rangLista());
        assertFalse(st.imaRangPredavanja()); // još nema drugog izvođenja
        SystemException e = assertThrows(SystemException.class, () -> k(i1.id(), TipKomande.RANG_PREDAVANJA));
        assertEquals(409, e.getCode());
        k(i1.id(), TipKomande.ZAVRSI);

        // izvođenje 2: jedno pitanje; Ana netačno, Ceca tačno, gost " mile " (isto ime) netačno
        PrezentacijaDetails p2 = prezentacija("Druga", 1);
        IzvodjenjeInfo i2 = izvodjenjeService.pokreni(p2.id(), new PokreniCmd(true, null, predavanjeId));
        Long u2Ana = ucesnikService.prijavi(i2.kod(), "x", Optional.of(ana)).info().ucesnikId();
        Long u2Ceca = ucesnikService.prijavi(i2.kod(), "x", Optional.of(ceca)).info().ucesnikId();
        Long u2Mile = ucesnikService.prijavi(i2.kod(), " mile ").info().ucesnikId();
        Long r3 = otvoriSledece(i2.id());
        odgovori(i2.id(), u2Ana, r3, opcija(p2, 0, 1));
        odgovori(i2.id(), u2Ceca, r3, opcija(p2, 0, 0));
        odgovori(i2.id(), u2Mile, r3, opcija(p2, 0, 1));

        // projektor: Shift+L pre TACAN ne računa trenutnu rundu
        assertTrue(stanjeService.nastavnicko(i2.id()).imaRangPredavanja());
        k(i2.id(), TipKomande.OTVORI_ZATVORI);
        k(i2.id(), TipKomande.RANG_PREDAVANJA);
        st = stanjeService.nastavnicko(i2.id());
        assertEquals(RangPrikaz.PREDAVANJE, st.rangPrikaz());
        Map<String, RangTacnostiStavka> projektor = poImenu(st.javniRangTacnosti());
        assertEquals(2, projektor.get("Ana A.").dugovanih());
        assertNull(projektor.get("Ceca C.")); // jedino pitanje joj je trenutno
        k(i2.id(), TipKomande.TACAN);
        projektor = poImenu(stanjeService.nastavnicko(i2.id()).javniRangTacnosti());
        assertEquals(3, projektor.get("Ana A.").dugovanih());
        assertEquals(1, projektor.get("Ceca C.").tacnih());
        izvodjenjeService.komanda(i2.id(), new KomandaCmd(TipKomande.RANG_BROJ, 1));
        st = stanjeService.nastavnicko(i2.id());
        assertEquals(1, st.rangBroj());
        assertEquals(1, st.javniRangTacnosti().size(), "na tabli samo prvih N");
        assertNull(st.javniRangTacnosti().getFirst().indeks());
        k(i2.id(), TipKomande.ZAVRSI);

        // vremena odgovora (prosek posle ★ odlučuje): Ceca 2 s, Bojan 5 s, gost 8 s
        jdbc.update("update odgovori set vreme_ms = 2000 where ucesnik_id = ?", u2Ceca);
        jdbc.update("update odgovori set vreme_ms = 5000 where ucesnik_id = ?", u1Bojan);
        jdbc.update("update odgovori set vreme_ms = 8000 where ucesnik_id in (?, ?)", u1Mile, u2Mile);
        jdbc.update("update odgovori set vreme_ms = 9000 where ucesnik_id in (?, ?)", u1Ana, u2Ana);

        // rang predavanja: zbir oba izvođenja, gost spojen po imenu (Ana ★2, pa ★1 po proseku: Ceca, Bojan, gost)
        RangPredavanja pred = rangService.zaPredavanje(predavanjeId);
        assertEquals(2, pred.izvodjenja().size());
        Map<String, RangTacnostiStavka> zbir = poImenu(pred.rang());
        assertEquals(List.of(1, 2, 3, 4), pred.rang().stream().map(RangTacnostiStavka::mesto).toList());
        assertEquals(List.of(ana, ceca, bojan), pred.rang().subList(0, 3).stream().map(RangTacnostiStavka::studentId).toList());
        assertEquals(2, zbir.get("Ana Anić").tacnih());
        assertEquals(3, zbir.get("Ana Anić").dugovanih());
        assertEquals(2, zbir.get("Ana Anić").izvodjenja());
        RangTacnostiStavka gost = pred.rang().get(3);
        assertTrue(gost.gost());
        assertEquals(2, gost.izvodjenja());
        assertEquals(3, gost.dugovanih());
        assertEquals(1, gost.tacnih());

        // kartica studenta: jedan čas, mesto 1 od 4 (najviše ★)
        UzivoStudenta s = rangService.zaStudenta(ana, null, null);
        assertEquals(1, s.casovi().size());
        UzivoStudentaRed red = s.casovi().getFirst();
        assertEquals(predavanjeId, red.predavanjeId());
        assertEquals("4. Sile", red.naslov());
        assertEquals(1, red.stavka().mesto());
        assertEquals(4, red.ukupnoURangu());
        assertEquals(2, s.ukupno().tacnih());
        assertEquals(1, s.ukupno().casova());
        assertTrue(rangService.zaStudenta(ana, null, SkolskaGodina.za(LocalDate.now()) - 1).casovi().isEmpty());
        assertEquals(new UzivoStudenta(null, List.of()), rangService.zaStudenta(ana, predmet.getId() + 100_000, null));

        // grupa: samo studenti grupe, bez gostiju; svi su učestvovali
        UzivoGrupe g = rangService.zaGrupu(grupaId, predmet.getId(), SkolskaGodina.za(LocalDate.now()));
        assertEquals(1, g.brojCasova());
        assertEquals(List.of("Ana Anić", "Ceca Cecić", "Bojan Bojić"),
                g.rang().stream().map(RangTacnostiStavka::ime).toList());
        assertEquals(1, g.rang().getFirst().casova());
    }

    @Test
    void grupaBezUcescaINepoznatId() {
        UzivoGrupe g = rangService.zaGrupu(grupaId, null, null);
        assertEquals(0, g.brojCasova());
        assertEquals(3, g.rang().size());
        assertTrue(g.rang().stream().allMatch(r -> r.mesto() == null && r.dugovanih() == 0));
        assertEquals(404, assertThrows(SystemException.class, () -> rangService.zaGrupu(-1L, null, null)).getCode());
        assertEquals(404, assertThrows(SystemException.class, () -> rangService.zaPredavanje(-1L)).getCode());
        assertEquals(404, assertThrows(SystemException.class, () -> rangService.zaStudenta(-1L, null, null)).getCode());
        assertEquals(404, assertThrows(SystemException.class, () -> rangService.zaIzvodjenje(-1L)).getCode());
        assertEquals(400, assertThrows(SystemException.class, () -> rangService.zaGrupu(grupaId, null, 99999)).getCode());
    }

    @Test
    void takmicenjeLOstajeRangPoena() {
        PrezentacijaDetails p = prezentacija("Takmičenje", 1);
        jdbc.update("update prezentacije set takmicenje = 1 where id = ?", p.id());
        IzvodjenjeInfo i = izvodjenjeService.pokreni(p.id(), new PokreniCmd(true, null, null));
        Long u = ucesnikService.prijavi(i.kod(), "Zoki").info().ucesnikId();
        Long r = otvoriSledece(i.id());
        odgovori(i.id(), u, r, opcija(p, 0, 0));
        k(i.id(), TipKomande.OTVORI_ZATVORI);
        k(i.id(), TipKomande.RANG_LISTA);
        NastavnickoStanje st = stanjeService.nastavnicko(i.id());
        assertNotNull(st.javnaRangLista());
        assertNull(st.javniRangTacnosti());
        assertNotNull(stanjeService.javno(i.id()).rangLista());
        assertTrue(rangService.zaIzvodjenje(i.id()).getFirst().poeni() > 0);
    }

    static Map<String, RangTacnostiStavka> poImenu(List<RangTacnostiStavka> rang) {
        Map<String, RangTacnostiStavka> m = new HashMap<>();
        rang.forEach(r -> m.put(r.ime(), r));
        return m;
    }
}
