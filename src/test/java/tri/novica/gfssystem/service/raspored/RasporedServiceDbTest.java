package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.raspored.*;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Raspored po periodu nad pravom MySQL bazom (spec najave odsustva, odeljak 3): provera unosa, izmena sa pravilom
 * {@code id}-ja (najave ostaju dok se ne promene predmet ili grupa), brisanje sa kaskadom.
 */
@SpringBootTest
@Import(RasporedSatConfig.class)
class RasporedServiceDbTest {

    /** Sreda 6. 11. 2030, 8:00. */
    static final LocalDateTime SADA = LocalDateTime.of(2030, 11, 6, 8, 0);
    static final LocalDate OD = LocalDate.of(2030, 10, 1);
    static final LocalDate DO = LocalDate.of(2031, 1, 31);

    @Autowired RasporedService rasporedi;
    @Autowired NajavaService najave;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    RasporedPodaci d;
    Long grupa;
    Long grupa2;

    @BeforeEach
    void setUp() {
        sat.postavi(SADA);
        d = new RasporedPodaci(jdbc, rasporedi);
        grupa = d.cas.grupa(2030);
        grupa2 = d.cas.grupa(2030);
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    static void ocekuj(int kod, String poruka, Supplier<?> s) {
        SystemException ex = assertThrows(SystemException.class, s::get);
        assertEquals(kod, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    RasporedCmd cmd(String naziv, LocalDate od, LocalDate doD, Integer prag, StavkaCmd... s) {
        return new RasporedCmd(naziv, od, doD, prag, List.of(s));
    }

    @Test
    void kreiranjeDetaljILista() {
        RasporedDetails r = d.period(OD, DO, 5,
                d.stavka(grupa, 3, "10:00", "13:00"), d.stavka(grupa2, 1, "08:15:30", "09:45"));
        assertEquals("Raspored " + d.cas.oznaka, r.naziv());
        assertEquals(2, r.stavke().size());
        // po danu pa početku; vreme na minute
        assertEquals(1, r.stavke().get(0).dan());
        assertEquals(LocalTime.of(8, 15), r.stavke().get(0).pocetak());
        assertEquals(grupa2, r.stavke().get(0).grupa().getId());
        assertEquals(d.cas.predmetId, r.stavke().get(1).predmet().getId());

        RasporedDetails isti = rasporedi.detalj(r.id());
        assertEquals(r, isti);
        RasporedInfo info = rasporedi.lista().stream().filter(x -> x.id().equals(r.id())).findFirst().orElseThrow();
        assertEquals(2, info.brojStavki());
        assertEquals(5, info.pragNajava());
        ocekuj(404, "Raspored ne postoji.", () -> rasporedi.detalj(-1L));
    }

    @Test
    void podrazumevaniPragINazivBezViskaRazmaka() {
        RasporedDetails r = rasporedi.kreiraj(cmd("  Zimski   semestar ", OD, DO, null));
        try {
            assertEquals("Zimski semestar", r.naziv());
            assertEquals(5, r.pragNajava());
            assertTrue(r.stavke().isEmpty());
        } finally {
            rasporedi.obrisi(r.id());
        }
    }

    @Test
    void proveraUnosa() {
        StavkaCmd ok = d.stavka(grupa, 3, "10:00", "13:00");
        ocekuj(400, "Naziv rasporeda je obavezan.", () -> rasporedi.kreiraj(cmd(" ", OD, DO, 5)));
        ocekuj(400, "Naziv rasporeda može imati najviše 100 znakova.", () -> rasporedi.kreiraj(cmd("x".repeat(101), OD, DO, 5)));
        ocekuj(400, "Datumi važenja su obavezni.", () -> rasporedi.kreiraj(cmd("R", null, DO, 5)));
        ocekuj(400, "Raspored mora da počne pre nego što se završi.", () -> rasporedi.kreiraj(cmd("R", DO, OD, 5)));
        ocekuj(400, "Raspored može da traje najviše 400 dana.",
                () -> rasporedi.kreiraj(cmd("R", OD, OD.plusDays(400), 5)));
        ocekuj(400, "Prag najava mora biti od 1 do 100.", () -> rasporedi.kreiraj(cmd("R", OD, DO, 0)));
        ocekuj(400, "Prag najava mora biti od 1 do 100.", () -> rasporedi.kreiraj(cmd("R", OD, DO, 101)));
        ocekuj(400, "Termin 2 se ponavlja (isti predmet, grupa, dan i početak).",
                () -> rasporedi.kreiraj(cmd("R", OD, DO, 5, ok, d.stavka(grupa, 3, "10:00", "11:00"))));
        ocekuj(400, "Termin 1: početak mora biti pre kraja.",
                () -> rasporedi.kreiraj(cmd("R", OD, DO, 5, d.stavka(grupa, 3, "10:00", "10:00"))));
        ocekuj(400, "Termin 1: dan mora biti od ponedeljka do nedelje.",
                () -> rasporedi.kreiraj(cmd("R", OD, DO, 5, d.stavka(grupa, 8, "10:00", "11:00"))));
        ocekuj(400, "Termin 1: izaberi grupu.",
                () -> rasporedi.kreiraj(cmd("R", OD, DO, 5, d.stavka(null, 3, "10:00", "11:00"))));
        ocekuj(400, "Termin 1: grupa ne postoji.",
                () -> rasporedi.kreiraj(cmd("R", OD, DO, 5, d.stavka(-5L, 3, "10:00", "11:00"))));
        ocekuj(400, "Termin 1: predmet ne postoji.", () -> rasporedi.kreiraj(cmd("R", OD, DO, 5,
                new StavkaCmd(null, -5L, grupa, 3, LocalTime.of(10, 0), LocalTime.of(11, 0)))));
        ocekuj(400, "Termin 1: vreme početka i kraja je obavezno.", () -> rasporedi.kreiraj(cmd("R", OD, DO, 5,
                new StavkaCmd(null, d.cas.predmetId, grupa, 3, null, LocalTime.of(11, 0)))));
        List<StavkaCmd> mnogo = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            mnogo.add(d.stavka(grupa, 1 + i % 7, String.format("%02d:%02d", 7 + i / 7, 0), "22:00"));
        }
        ocekuj(400, "Raspored može imati najviše 50 termina.",
                () -> rasporedi.kreiraj(new RasporedCmd("R", OD, DO, 5, mnogo)));
        // 400 dana tačno je dozvoljeno
        RasporedDetails r = d.period(OD, OD.plusDays(399), 5);
        assertEquals(OD.plusDays(399), r.vaziDo());
    }

    @Test
    void izmenaZadrzavaStavkuINajaveDokSeNeMenjaGrupa() {
        Long ana = d.cas.student(grupa, "Ana");
        RasporedDetails r = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00"), d.stavka(grupa2, 4, "10:00", "12:00"));
        StavkaInfo sreda = r.stavke().get(0);
        StavkaInfo cetvrtak = r.stavke().get(1);
        LocalDate datum = LocalDate.of(2030, 11, 13);
        najave.najavi(ana, new NajavaCmd(sreda.id(), datum, null));

        // sreda: drugo vreme, isti predmet i grupa -> isti id, najava ostaje; četvrtak: druga grupa -> nov id
        RasporedDetails izm = rasporedi.izmeni(r.id(), cmd("Novi naziv", OD, DO, 7,
                new StavkaCmd(sreda.id(), d.cas.predmetId, grupa, 3, LocalTime.of(11, 0), LocalTime.of(14, 0)),
                new StavkaCmd(cetvrtak.id(), d.cas.predmetId, grupa, 4, LocalTime.of(10, 0), LocalTime.of(12, 0))));
        d.zapamtiStavke(izm);
        assertEquals("Novi naziv", izm.naziv());
        assertEquals(7, izm.pragNajava());
        assertEquals(sreda.id(), izm.stavke().get(0).id());
        assertEquals(LocalTime.of(11, 0), izm.stavke().get(0).pocetak());
        assertNotEquals(cetvrtak.id(), izm.stavke().get(1).id());
        assertEquals(grupa, izm.stavke().get(1).grupa().getId());
        assertEquals(1, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ?", Integer.class,
                sreda.id()));
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from raspored_stavke where id = ?", Integer.class,
                cetvrtak.id()));

        // grupa sredine stavke se menja -> stara stavka sa najavom odlazi
        RasporedDetails izm2 = rasporedi.izmeni(r.id(), cmd("Novi naziv", OD, DO, 7,
                new StavkaCmd(sreda.id(), d.cas.predmetId, grupa2, 3, LocalTime.of(11, 0), LocalTime.of(14, 0))));
        d.zapamtiStavke(izm2);
        assertEquals(1, izm2.stavke().size());
        assertNotEquals(sreda.id(), izm2.stavke().get(0).id());
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ?", Integer.class,
                sreda.id()));
    }

    @Test
    void izmenaZamenaDvaTerminaIstogKljuca() {
        // dve stavke zamene početke: bez jedinstvenog ključa u bazi, provera je nad konačnim stanjem
        RasporedDetails r = d.period(OD, DO, 5, d.stavka(grupa, 3, "08:00", "10:00"), d.stavka(grupa, 3, "10:00", "12:00"));
        Long a = r.stavke().get(0).id();
        Long b = r.stavke().get(1).id();
        RasporedDetails izm = rasporedi.izmeni(r.id(), cmd("R", OD, DO, 5,
                new StavkaCmd(a, d.cas.predmetId, grupa, 3, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                new StavkaCmd(b, d.cas.predmetId, grupa, 3, LocalTime.of(8, 0), LocalTime.of(10, 0))));
        assertEquals(b, izm.stavke().get(0).id());
        assertEquals(a, izm.stavke().get(1).id());
    }

    @Test
    void izmenaOdbijaTudjIIdPonovljen() {
        RasporedDetails r = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00"));
        RasporedDetails drugi = d.period(OD, DO, 5, d.stavka(grupa, 4, "10:00", "13:00"));
        Long tudja = drugi.stavke().get(0).id();
        Long moja = r.stavke().get(0).id();
        ocekuj(400, "Termin 1 ne pripada ovom rasporedu.", () -> rasporedi.izmeni(r.id(), cmd("R", OD, DO, 5,
                new StavkaCmd(tudja, d.cas.predmetId, grupa, 4, LocalTime.of(10, 0), LocalTime.of(13, 0)))));
        ocekuj(400, "Termin 2 je poslat dvaput.", () -> rasporedi.izmeni(r.id(), cmd("R", OD, DO, 5,
                new StavkaCmd(moja, d.cas.predmetId, grupa, 3, LocalTime.of(10, 0), LocalTime.of(13, 0)),
                new StavkaCmd(moja, d.cas.predmetId, grupa, 5, LocalTime.of(10, 0), LocalTime.of(13, 0)))));
        ocekuj(404, "Raspored ne postoji.", () -> rasporedi.izmeni(-1L, cmd("R", OD, DO, 5)));
        // ništa nije promenjeno
        assertEquals(1, rasporedi.detalj(r.id()).stavke().size());
    }

    @Test
    void brisanjeKaskadno() {
        Long ana = d.cas.student(grupa, "Ana");
        RasporedDetails r = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00"));
        Long s = r.stavke().get(0).id();
        najave.najavi(ana, new NajavaCmd(s, LocalDate.of(2030, 11, 13), "bolest"));
        rasporedi.obrisi(r.id());
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from raspored_stavke where id = ?", Integer.class, s));
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ?", Integer.class, s));
        ocekuj(404, "Raspored ne postoji.", () -> {
            rasporedi.obrisi(r.id());
            return null;
        });
    }

    // ------------------------------------------------------------------ pregled posle PR-a, tačke 3 i 4

    @Test
    void preklapanjeIstogTerminaUDrugomPerioduJe400() {
        RasporedDetails a = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00"));
        // "Kopiraj u novi" sa preklopljenim datumima: isti predmet, grupa, dan i početak
        SystemException ex = assertThrows(SystemException.class, () -> rasporedi.kreiraj(cmd("Kopija",
                LocalDate.of(2031, 1, 1), LocalDate.of(2031, 6, 30), 5, d.stavka(grupa, 3, "10:00", "12:00"))));
        assertEquals(400, ex.getCode());
        assertEquals("Termin 1 već postoji u rasporedu „Raspored " + d.cas.oznaka + "“ (1. 10. 2030. – 31. 1. 2031.): isti "
                + "predmet, grupa, dan i početak u periodima koji se preklapaju. Promeni datume važenja ili ukloni termin.",
                ex.getMessage());

        // preklapanje od dva dana bez srede (čet 30. 1. i pet 31. 1.), drugi početak, druga grupa: dozvoljeno
        d.zapamti(rasporedi.kreiraj(cmd("Bez srede", LocalDate.of(2031, 1, 30), LocalDate.of(2031, 3, 1), 5,
                d.stavka(grupa, 3, "10:00", "13:00"))));
        d.zapamti(rasporedi.kreiraj(cmd("Drugi početak", OD, DO, 5, d.stavka(grupa, 3, "14:00", "15:00"))));
        d.zapamti(rasporedi.kreiraj(cmd("Druga grupa", OD, DO, 5, d.stavka(grupa2, 3, "10:00", "13:00"))));

        // izmena perioda se ne poredi sa samom sobom
        RasporedDetails izm = rasporedi.izmeni(a.id(), cmd("Isti", OD, DO, 5,
                new StavkaCmd(a.stavke().get(0).id(), d.cas.predmetId, grupa, 3, LocalTime.of(10, 0), LocalTime.of(13, 0))));
        assertEquals(a.stavke().get(0).id(), izm.stavke().get(0).id());
    }

    void otkazi(Long stavka, LocalDate datum) {
        jdbc.update("insert into otkazani_termini (stavka_id, datum, napomena, otkazano) values (?, ?, ?, ?)", stavka, datum,
                "n", SADA);
    }

    int brojOtkazanih(Long stavka) {
        return jdbc.queryForObject("select count(*) from otkazani_termini where stavka_id = ?", Integer.class, stavka);
    }

    @Test
    void izmenaKojaUkidaBudutaOtkazivanjaIhBrojiIBrise() {
        RasporedDetails r = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00"), d.stavka(grupa2, 4, "10:00", "12:00"));
        StavkaInfo sreda = r.stavke().get(0);
        StavkaInfo cetvrtak = r.stavke().get(1);
        otkazi(sreda.id(), LocalDate.of(2030, 10, 30));  // prošlo: ne broji se i ne dira
        otkazi(sreda.id(), LocalDate.of(2030, 11, 13));
        otkazi(sreda.id(), LocalDate.of(2031, 1, 29));
        otkazi(cetvrtak.id(), LocalDate.of(2030, 11, 14));

        RasporedDetails det = rasporedi.detalj(r.id());
        assertEquals(List.of(LocalDate.of(2030, 11, 13), LocalDate.of(2031, 1, 29)), det.stavke().get(0).otkazani());
        assertEquals(List.of(LocalDate.of(2030, 11, 14)), det.stavke().get(1).otkazani());
        assertEquals(0, det.izgubljenaOtkazivanja());

        // isti termini: ništa se ne gubi
        RasporedDetails ista = rasporedi.izmeni(r.id(), cmd("Isti", OD, DO, 5,
                new StavkaCmd(sreda.id(), d.cas.predmetId, grupa, 3, LocalTime.of(11, 0), LocalTime.of(13, 0)),
                new StavkaCmd(cetvrtak.id(), d.cas.predmetId, grupa2, 4, LocalTime.of(10, 0), LocalTime.of(12, 0))));
        assertEquals(0, ista.izgubljenaOtkazivanja());

        // kraći period (29. 1. ispada) i četvrtak prelazi u petak: dva izgubljena, redovi obrisani
        RasporedDetails izm = rasporedi.izmeni(r.id(), cmd("Kraći", OD, LocalDate.of(2031, 1, 15), 5,
                new StavkaCmd(sreda.id(), d.cas.predmetId, grupa, 3, LocalTime.of(11, 0), LocalTime.of(13, 0)),
                new StavkaCmd(cetvrtak.id(), d.cas.predmetId, grupa2, 5, LocalTime.of(10, 0), LocalTime.of(12, 0))));
        assertEquals(2, izm.izgubljenaOtkazivanja());
        assertEquals(List.of(LocalDate.of(2030, 11, 13)), izm.stavke().get(0).otkazani());
        assertEquals(List.of(), izm.stavke().get(1).otkazani());
        assertEquals(2, brojOtkazanih(sreda.id()), "prošlo i 13. 11. ostaju");
        assertEquals(0, brojOtkazanih(cetvrtak.id()));

        // druga grupa srede: stavka se pravi iznova, otkazivanje 13. 11. se gubi (FK CASCADE)
        RasporedDetails izm2 = rasporedi.izmeni(r.id(), cmd("Kraći", OD, LocalDate.of(2031, 1, 15), 5,
                new StavkaCmd(sreda.id(), d.cas.predmetId, grupa2, 3, LocalTime.of(11, 0), LocalTime.of(13, 0))));
        d.zapamtiStavke(izm2);
        assertEquals(1, izm2.izgubljenaOtkazivanja());
        assertEquals(0, brojOtkazanih(sreda.id()));
    }
}
