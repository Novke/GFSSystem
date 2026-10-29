package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.pregled.KontrolnaTablaInfo;
import tri.novica.gfssystem.dto.pregled.SledecePredavanjeInfo;
import tri.novica.gfssystem.dto.raspored.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.PregledService;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;
import tri.novica.gfssystem.service.mejl.MejlPoruka;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Termini za nastavnika i početna nad pravom MySQL bazom (spec najave odsustva, odeljci 3.2, 6 i 8): pregled sa
 * najavama i predavanjem, otkazivanje i vraćanje sa mejlovima studentima (svoja grupa i starija generacija, samo sa
 * adresom), ponovljeno otkazivanje bez mejlova, provere, i predlog sledećeg predavanja iz rasporeda.
 */
@SpringBootTest
@Import({RasporedSatConfig.class, LazniMejlServis.class})
class TerminServiceDbTest {

    /** Sreda 6. 11. 2030, 8:00; čas sredom 10-13. */
    static final LocalDateTime SADA = LocalDateTime.of(2030, 11, 6, 8, 0);
    static final LocalDate DANAS = SADA.toLocalDate();
    static final LocalDate OD = LocalDate.of(2030, 10, 1);
    static final LocalDate DO = LocalDate.of(2031, 1, 31);

    @Autowired RasporedService rasporedi;
    @Autowired NajavaService najave;
    @Autowired TerminService termini;
    @Autowired PregledService pregled;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired LazniMejlServis mejl;
    @Autowired PodesavanjaStudentaServis podesavanja;
    @Autowired JdbcTemplate jdbc;

    RasporedPodaci d;
    Long grupa;
    Long starija;
    Long stavka;
    Long ana;
    Long bora;
    Long ceda;

    @BeforeEach
    void setUp() {
        sat.postavi(SADA);
        mejl.ocisti();
        d = new RasporedPodaci(jdbc, rasporedi);
        grupa = d.cas.grupa(2030);
        starija = d.cas.grupa(2029);
        stavka = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00")).stavke().get(0).id();
        ana = d.cas.student(grupa, "Ana");
        bora = d.cas.student(grupa, "Bora");
        ceda = d.cas.student(starija, "Ceda");
        d.mejl(ana, "ana@example.test");
        d.mejl(ceda, "ceda@example.test");
        d.cas.aktivnost(d.cas.predavanje(grupa, LocalDate.of(2030, 10, 30)), ceda, "PRISUSTVO");
    }

    @AfterEach
    void tearDown() {
        mejl.ocisti();
        d.obrisi();
    }

    static void ocekuj(int kod, String poruka, Supplier<?> s) {
        SystemException ex = assertThrows(SystemException.class, s::get);
        assertEquals(kod, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    List<TerminInfo> moji(LocalDate od, Integer dana) {
        return termini.termini(od, dana).stream().filter(t -> t.stavkaId().equals(stavka)).toList();
    }

    @Test
    void terminiSaNajavamaIPredavanjem() {
        najave.najavi(ana, new NajavaCmd(stavka, DANAS, "lekar"));
        najave.najavi(ceda, new NajavaCmd(stavka, DANAS, null));
        Long pred = d.cas.predavanje(grupa, DANAS);
        List<TerminInfo> t = moji(null, 28);
        assertEquals(List.of(DANAS, DANAS.plusDays(7), DANAS.plusDays(14), DANAS.plusDays(21)),
                t.stream().map(TerminInfo::datum).toList());
        TerminInfo danas = t.get(0);
        assertEquals(2, danas.brojNajava());
        assertEquals(5, danas.prag());
        assertEquals(pred, danas.predavanjeId());
        assertFalse(danas.poceo());
        assertEquals(Set.of("Ana", "Ceda"), danas.najave().stream().map(NajavaInfo::ime).collect(Collectors.toSet()));
        assertEquals("lekar", danas.najave().stream().filter(n -> n.ime().equals("Ana")).findFirst().orElseThrow().razlog());
        assertNull(t.get(1).predavanjeId());
        assertEquals(0, t.get(1).brojNajava());

        assertEquals(1, moji(DANAS, 1).size());
        assertEquals(List.of(DANAS.plusDays(7)), moji(DANAS.plusDays(1), 7).stream().map(TerminInfo::datum).toList());
        ocekuj(400, "Neispravan parametar: dana.", () -> termini.termini(null, 0));
        ocekuj(400, "Neispravan parametar: dana.", () -> termini.termini(null, 121));
        sat.postavi(SADA.withHour(10));
        assertTrue(moji(null, 1).get(0).poceo());
    }

    @Test
    void otkazivanjeIVracanjeSaMejlovima() {
        PromenaTerminaOdgovor o = termini.otkazi(tc(stavka, DANAS.plusDays(7), "  sednica veća  "));
        assertEquals(2, o.obavesteno(), "Ana i Ceda (starija generacija) imaju adresu, Bora nema");
        assertEquals(0, o.neposlato());
        assertTrue(o.termin().otkazan());
        assertEquals("sednica veća", o.termin().napomena());
        List<MejlPoruka> m = mejl.cekaj(2).stream().sorted(Comparator.comparing(MejlPoruka::za)).toList();
        assertEquals(List.of("ana@example.test", "ceda@example.test"), m.stream().map(MejlPoruka::za).toList());
        assertEquals("Nema časa: Čas " + d.cas.oznaka + ", sreda 13. 11.", m.get(0).naslov());
        assertTrue(m.get(0).telo().contains("Napomena nastavnika: sednica veća"), m.get(0).telo());
        assertTrue(m.get(0).telo().contains("Zdravo, Ana!"), m.get(0).telo());

        // ponovljeno: samo napomena, bez mejlova
        mejl.ocisti();
        PromenaTerminaOdgovor p = termini.otkazi(tc(stavka, DANAS.plusDays(7), null));
        assertEquals(0, p.obavesteno());
        assertNull(p.termin().napomena());
        assertEquals(1, (int) jdbc.queryForObject("select count(*) from otkazani_termini where stavka_id = ?", Integer.class,
                stavka));

        PromenaTerminaOdgovor v = termini.vrati(tc(stavka, DANAS.plusDays(7), null));
        assertFalse(v.termin().otkazan());
        assertEquals(2, v.obavesteno());
        List<MejlPoruka> m2 = mejl.cekaj(2);
        assertTrue(m2.stream().allMatch(x -> x.naslov().startsWith("Čas se ipak održava: ")), m2.toString());
        assertTrue(m2.stream().noneMatch(x -> x.telo().contains("Napomena")));
        mejl.ocisti();
        assertEquals(0, termini.vrati(tc(stavka, DANAS.plusDays(7), null)).obavesteno(), "neotkazan: ništa");
        assertTrue(mejl.poslate().isEmpty());
    }

    /** {@code TerminCmd} bez izbora slanja ({@code posaljiMejl == null}, kao stariji klijent): mejl se šalje. */
    static TerminCmd tc(Long stavkaId, LocalDate datum, String napomena) {
        return new TerminCmd(stavkaId, datum, napomena, null);
    }

    @Test
    void bezMejlaKadNastavnikIskljuciIJednaPorukaPoAdresi() {
        // "Pošalji mejl studentima" isključeno: termin se otkazuje i vraća, poruka nema
        PromenaTerminaOdgovor o = termini.otkazi(new TerminCmd(stavka, DANAS.plusDays(7), "sednica", false));
        assertTrue(o.termin().otkazan());
        assertEquals(0, o.obavesteno());
        assertEquals(0, o.neposlato());
        PromenaTerminaOdgovor v = termini.vrati(new TerminCmd(stavka, DANAS.plusDays(7), null, false));
        assertFalse(v.termin().otkazan());
        assertEquals(0, v.obavesteno());
        assertTrue(mejl.poslate().isEmpty());

        // ista adresa kod dva studenta (velika i mala slova): jedna poruka
        d.mejl(ceda, " ANA@Example.test ");
        PromenaTerminaOdgovor p = termini.otkazi(new TerminCmd(stavka, DANAS.plusDays(14), null, true));
        assertEquals(1, p.obavesteno());
        assertEquals(0, p.neposlato());
        assertEquals(1, mejl.cekaj(1).size());
    }

    @Test
    void studentKojiJeIskljucioMejlNeDobijaNiOtkazNiVracanje() {
        podesavanja.sacuvaj(ana, new PodesavanjaNaloga(false));
        PromenaTerminaOdgovor o = termini.otkazi(tc(stavka, DANAS.plusDays(7), null));
        assertEquals(1, o.obavesteno(), "samo Ceda; Ana je na tabu Ja isključila mejl");
        assertEquals(0, o.neposlato(), "isključen mejl nije neposlat");
        assertEquals(List.of("ceda@example.test"), mejl.cekaj(1).stream().map(MejlPoruka::za).toList());
        mejl.ocisti();
        PromenaTerminaOdgovor v = termini.vrati(tc(stavka, DANAS.plusDays(7), null));
        assertEquals(1, v.obavesteno());
        assertEquals(0, v.neposlato());
        assertEquals(List.of("ceda@example.test"), mejl.cekaj(1).stream().map(MejlPoruka::za).toList());
    }

    @Test
    void iskljucenMejlNijeNeposlatKadSlanjeNijePodeseno() {
        podesavanja.sacuvaj(ana, new PodesavanjaNaloga(false));
        mejl.dostupan(false);
        PromenaTerminaOdgovor o = termini.otkazi(tc(stavka, DANAS, null));
        assertEquals(0, o.obavesteno());
        assertEquals(1, o.neposlato(), "samo Ceda bi dobila mejl; Ana ga je isključila");
    }

    @Test
    void otkazivanjeNeSaljeKadMejlNijePodesen() {
        mejl.dostupan(false);
        PromenaTerminaOdgovor o = termini.otkazi(tc(stavka, DANAS, null));
        assertEquals(0, o.obavesteno());
        assertEquals(2, o.neposlato(), "Ana i Ceda imaju adresu, a slanje nije podešeno");
        assertTrue(termini.termini(DANAS, 1).stream().filter(t -> t.stavkaId().equals(stavka)).findFirst().orElseThrow()
                .otkazan());
    }

    @Test
    void provereOtkazivanja() {
        ocekuj(404, TerminService.NE_POSTOJI, () -> termini.otkazi(tc(stavka, DANAS.plusDays(1), null)));
        ocekuj(404, TerminService.NE_POSTOJI, () -> termini.otkazi(tc(-1L, DANAS, null)));
        ocekuj(404, TerminService.NE_POSTOJI, () -> termini.vrati(tc(stavka, null, null)));
        ocekuj(409, TerminService.POCEO, () -> termini.otkazi(tc(stavka, DANAS.minusDays(7), null)));
        ocekuj(400, "Napomena može imati najviše 200 znakova.",
                () -> termini.otkazi(tc(stavka, DANAS, "x".repeat(201))));
        sat.postavi(SADA.withHour(10));
        ocekuj(409, TerminService.POCEO, () -> termini.otkazi(tc(stavka, DANAS, null)));
        ocekuj(409, TerminService.POCEO, () -> termini.vrati(tc(stavka, DANAS, null)));
    }

    // ---------------------------------------------------------------- početna

    @Test
    void pocetnaPredlazeDanasnjiTermin() {
        najave.najavi(ana, new NajavaCmd(stavka, DANAS, null));
        KontrolnaTablaInfo k = pregled.kontrolnaTabla();
        assertTrue(k.raspored().pokrivaDanas());
        assertTrue(k.raspored().naredni().stream().anyMatch(t -> t.stavkaId().equals(stavka) && t.datum().equals(DANAS)
                && t.brojNajava() == 1));
        SledecePredavanjeInfo s = k.sledece();
        assertEquals(SledecePredavanjeInfo.Izvor.RASPORED_DANAS, s.izvor());
        assertEquals(stavka, s.termin().stavkaId());
        assertEquals(grupa, s.grupa().getId());
        assertEquals(d.cas.predmetId, s.predmet().getId());
        assertEquals(2L, s.brojStudenata());
        assertEquals(LocalTime.of(10, 0), s.termin().pocetak());
        assertEquals(1, s.termin().brojNajava());
    }

    @Test
    void pocetnaPosleDanasnjegPredavanjaNudiSledeci() {
        d.cas.predavanje(grupa, DANAS);
        SledecePredavanjeInfo s = pregled.kontrolnaTabla().sledece();
        assertEquals(SledecePredavanjeInfo.Izvor.RASPORED_SLEDECI, s.izvor());
        assertEquals(DANAS.plusDays(7), s.termin().datum());

        // otkazan sledeći termin se preskače
        termini.otkazi(tc(stavka, DANAS.plusDays(7), null));
        assertEquals(DANAS.plusDays(14), pregled.kontrolnaTabla().sledece().termin().datum());

        // završen današnji bez predavanja se ne nudi (posle 13 h)
        sat.postavi(LocalDateTime.of(2030, 11, 20, 13, 0));
        assertEquals(LocalDate.of(2030, 11, 27), pregled.kontrolnaTabla().sledece().termin().datum());
    }

    @Test
    void pocetnaBezPeriodaZaDanas() {
        sat.postavi(LocalDateTime.of(2031, 3, 5, 8, 0));
        KontrolnaTablaInfo k = pregled.kontrolnaTabla();
        assertFalse(k.raspored().pokrivaDanas());
        assertTrue(k.raspored().naredni().stream().noneMatch(t -> t.stavkaId().equals(stavka)));
        if (k.sledece() != null) {
            assertEquals(SledecePredavanjeInfo.Izvor.POSLEDNJE, k.sledece().izvor());
            assertNull(k.sledece().termin());
        }
    }
}
