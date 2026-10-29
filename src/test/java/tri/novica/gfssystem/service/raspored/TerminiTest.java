package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Čiste funkcije termina (spec najave odsustva, odeljak 2): datum u periodu sa danom stavke, početak, kraj, naredni. */
class TerminiTest {

    // 2026-11-04 je sreda (ISO 3)
    static final LocalDate OD = LocalDate.of(2026, 10, 1);
    static final LocalDate DO = LocalDate.of(2027, 1, 31);
    static final LocalTime DESET = LocalTime.of(10, 0);

    @Test
    void terminJeDatumUPerioduSaDanomStavke() {
        assertTrue(Termini.jeTermin(OD, DO, 3, LocalDate.of(2026, 11, 4)));
        assertFalse(Termini.jeTermin(OD, DO, 3, LocalDate.of(2026, 11, 5)), "četvrtak nije sreda");
        assertFalse(Termini.jeTermin(OD, DO, 3, LocalDate.of(2026, 9, 30)), "sreda pre perioda");
        assertFalse(Termini.jeTermin(OD, DO, 3, LocalDate.of(2027, 2, 3)), "sreda posle perioda");
        assertTrue(Termini.jeTermin(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4), 3, LocalDate.of(2026, 11, 4)),
                "granice perioda su uključene");
        assertFalse(Termini.jeTermin(OD, DO, 3, null));
    }

    @Test
    void poceoIZavrsen() {
        LocalDate d = LocalDate.of(2026, 11, 4);
        assertFalse(Termini.poceo(LocalDateTime.of(2026, 11, 4, 9, 59, 59), d, DESET));
        assertTrue(Termini.poceo(LocalDateTime.of(2026, 11, 4, 10, 0), d, DESET), "u minut početka je počeo");
        assertTrue(Termini.poceo(LocalDateTime.of(2026, 11, 5, 8, 0), d, DESET));
        assertFalse(Termini.zavrsen(LocalDateTime.of(2026, 11, 4, 12, 59), d, LocalTime.of(13, 0)));
        assertTrue(Termini.zavrsen(LocalDateTime.of(2026, 11, 4, 13, 0), d, LocalTime.of(13, 0)));
    }

    @Test
    void uProzoruSeceSaPeriodom() {
        List<LocalDate> d = Termini.uProzoru(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 10), 3,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30));
        assertEquals(List.of(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 4)), d);
        assertEquals(List.of(), Termini.uProzoru(OD, DO, 3, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1)));
        assertEquals(List.of(), Termini.uProzoru(OD, DO, 3, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4)), "obrnut prozor");
    }

    @Test
    void narednaCetiriOdSadaDanasnjiDokNePocne() {
        LocalDateTime pre = LocalDateTime.of(2026, 11, 4, 9, 30);
        assertEquals(List.of(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 25)), Termini.naredni(OD, DO, 3, DESET, pre, 4));
        LocalDateTime posle = LocalDateTime.of(2026, 11, 4, 10, 0);
        assertEquals(LocalDate.of(2026, 11, 11), Termini.naredni(OD, DO, 3, DESET, posle, 4).getFirst(),
                "počeo današnji se ne nudi");
        assertEquals(List.of(LocalDate.of(2027, 1, 20), LocalDate.of(2027, 1, 27)),
                Termini.naredni(OD, DO, 3, DESET, LocalDateTime.of(2027, 1, 14, 8, 0), 4), "kraj perioda");
        assertEquals(List.of(LocalDate.of(2026, 10, 7)),
                Termini.naredni(OD, DO, 3, DESET, LocalDateTime.of(2026, 9, 1, 8, 0), 1), "period još nije počeo");
    }

    @Test
    void opisTerminaNaSrpskom() {
        TerminOpis t = new TerminOpis(7L, LocalDate.of(2026, 11, 4), DESET, LocalTime.of(13, 0), "Uvod", "GD-2026");
        assertEquals("sreda", t.dan());
        assertEquals("u sredu", t.uDan());
        assertEquals("4. 11.", t.datumKratko());
        assertEquals("4. 11. 2026.", t.datumPun());
        assertEquals("10:00–13:00", t.blok());
        assertEquals("MNOGO_NAJAVA:7:2026-11-04", t.kljucObavestenja());
        assertEquals("u ponedeljak", new TerminOpis(1L, LocalDate.of(2026, 11, 2), DESET, DESET, "", "").uDan());
        assertEquals("u subotu", new TerminOpis(1L, LocalDate.of(2026, 11, 7), DESET, DESET, "", "").uDan());
        assertEquals("u nedelju", new TerminOpis(1L, LocalDate.of(2026, 11, 8), DESET, DESET, "", "").uDan());
    }

    @Test
    void mnozinaStudenata() {
        assertEquals("1 student", TerminOpis.studenata(1));
        assertEquals("2 studenta", TerminOpis.studenata(2));
        assertEquals("5 studenata", TerminOpis.studenata(5));
        assertEquals("11 studenata", TerminOpis.studenata(11));
        assertEquals("12 studenata", TerminOpis.studenata(12));
        assertEquals("21 student", TerminOpis.studenata(21));
        assertEquals("23 studenta", TerminOpis.studenata(23));
    }

    @Test
    void tekstSeCisti() {
        assertNull(Termini.ocisti(null, 200));
        assertNull(Termini.ocisti("  ​ \t ", 200));
        assertEquals("Bolestan sam", Termini.ocisti(" Bolestan\u0000 \n sam‮ ", 200));
        assertEquals("a".repeat(200), Termini.ocisti("a".repeat(200), 200));
        assertThrows(IllegalArgumentException.class, () -> Termini.ocisti("a".repeat(201), 200));
        // ćirilica i dijakritika su znakovi, ne bajtovi
        assertEquals("ć".repeat(200), Termini.ocisti("ć".repeat(200), 200));
    }
}
