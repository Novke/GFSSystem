package tri.novica.gfssystem.service.anketa;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.anketa.Anketa;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.anketa.AnketaPravila.Stanje;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Čista pravila brze ankete (spec brze ankete 3 i 4): unos, glas, izračunato stanje, prozori za studenta i zbirovi. */
class AnketaPravilaTest {

    static final LocalDateTime T = LocalDateTime.of(2033, 2, 10, 10, 0);

    static String poruka(Runnable r) {
        SystemException e = assertThrows(SystemException.class, r::run);
        assertEquals(400, e.getCode());
        return e.getMessage();
    }

    static Anketa anketa(PublikaAnkete publika, LocalDateTime rok, LocalDateTime zatvoreno) {
        Anketa a = new Anketa();
        a.setPublika(publika);
        a.setOtvoreno(T);
        a.setRok(rok);
        a.setZatvoreno(zatvoreno);
        return a;
    }

    // ---------------------------------------------------------------- unos

    @Test
    void pitanjeSeSkracujeIBrojiUZnakovima() {
        assertEquals("Pauza?", AnketaPravila.pitanje("  Pauza?\n"));
        assertEquals(AnketaPravila.PITANJE, poruka(() -> AnketaPravila.pitanje("   ")));
        assertEquals(AnketaPravila.PITANJE, poruka(() -> AnketaPravila.pitanje(null)));
        String emoji = "☕".repeat(300);
        assertEquals(emoji, AnketaPravila.pitanje(emoji), "300 znakova, i kad su van BMP-a nije previše");
        String dugo = "😀".repeat(300);
        assertEquals(dugo, AnketaPravila.pitanje(dugo), "300 code point-a (600 UTF-16 jedinica)");
        assertEquals(AnketaPravila.PITANJE, poruka(() -> AnketaPravila.pitanje("a".repeat(301))));
    }

    @Test
    void odgovoriOdDvaDoDesetRazlicitiBezVelikihSlova() {
        assertEquals(List.of("Da", "Ne"), AnketaPravila.odgovori(List.of(" Da ", "Ne")));
        assertEquals(AnketaPravila.BROJ_ODGOVORA, poruka(() -> AnketaPravila.odgovori(List.of("Da"))));
        assertEquals(AnketaPravila.BROJ_ODGOVORA, poruka(() -> AnketaPravila.odgovori(null)));
        assertEquals(AnketaPravila.BROJ_ODGOVORA, poruka(() -> AnketaPravila.odgovori(
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"))));
        assertEquals(10, AnketaPravila.odgovori(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")).size());
        assertEquals(AnketaPravila.ISTI_ODGOVORI, poruka(() -> AnketaPravila.odgovori(List.of("Da", " da"))));
        assertEquals("Odgovor 2 mora imati od 1 do 120 znakova.", poruka(() -> AnketaPravila.odgovori(List.of("Da", " "))));
        assertEquals("Odgovor 1 mora imati od 1 do 120 znakova.",
                poruka(() -> AnketaPravila.odgovori(Arrays.asList(null, "Ne"))));
        assertEquals("Odgovor 2 mora imati od 1 do 120 znakova.",
                poruka(() -> AnketaPravila.odgovori(List.of("Da", "x".repeat(121)))));
    }

    @Test
    void trajanjeRokSatiKratkiTekstoviIFolder() {
        assertNull(AnketaPravila.trajanje(null));
        assertEquals(10, AnketaPravila.trajanje(10));
        assertEquals(1800, AnketaPravila.trajanje(1800));
        assertEquals(AnketaPravila.TRAJANJE, poruka(() -> AnketaPravila.trajanje(9)));
        assertEquals(AnketaPravila.TRAJANJE, poruka(() -> AnketaPravila.trajanje(1801)));
        assertEquals(336, AnketaPravila.rokSati(336));
        assertEquals(AnketaPravila.ROK_SATI, poruka(() -> AnketaPravila.rokSati(0)));
        assertEquals(AnketaPravila.ROK_SATI, poruka(() -> AnketaPravila.rokSati(337)));
        assertNull(AnketaPravila.opcioniTekst("  ", 16, AnketaPravila.IKONICA));
        assertEquals("☕", AnketaPravila.opcioniTekst(" ☕ ", 16, AnketaPravila.IKONICA));
        assertEquals(AnketaPravila.IKONICA, poruka(() -> AnketaPravila.opcioniTekst("a".repeat(17), 16, AnketaPravila.IKONICA)));
        assertEquals(AnketaPravila.KRATAK_NAZIV,
                poruka(() -> AnketaPravila.opcioniTekst("Pa\nuza", 30, AnketaPravila.KRATAK_NAZIV)));
        assertEquals("Tok časa", AnketaPravila.nazivFoldera(" Tok časa "));
        assertEquals(AnketaPravila.NAZIV_FOLDERA, poruka(() -> AnketaPravila.nazivFoldera("")));
        assertEquals(AnketaPravila.NAZIV_FOLDERA, poruka(() -> AnketaPravila.nazivFoldera("f".repeat(61))));
    }

    @Test
    void glasJedanIliViseRazlicitihUOpsegu() {
        assertEquals(List.of(1), AnketaPravila.glas(List.of(1), 3, false));
        assertEquals(List.of(0, 2), AnketaPravila.glas(List.of(2, 0), 3, true), "rastuće");
        assertEquals(List.of(1), AnketaPravila.glas(List.of(1), 3, true));
        assertEquals(AnketaPravila.JEDAN_ODGOVOR, poruka(() -> AnketaPravila.glas(List.of(0, 1), 3, false)));
        assertEquals(AnketaPravila.JEDAN_ODGOVOR, poruka(() -> AnketaPravila.glas(List.of(), 3, false)));
        assertEquals(AnketaPravila.BAR_JEDAN_ODGOVOR, poruka(() -> AnketaPravila.glas(null, 3, true)));
        assertEquals(AnketaPravila.NEISPRAVAN_ODGOVOR, poruka(() -> AnketaPravila.glas(List.of(3), 3, false)));
        assertEquals(AnketaPravila.NEISPRAVAN_ODGOVOR, poruka(() -> AnketaPravila.glas(List.of(-1), 3, false)));
        assertEquals(AnketaPravila.NEISPRAVAN_ODGOVOR, poruka(() -> AnketaPravila.glas(List.of(1, 1), 3, true)));
        assertEquals(AnketaPravila.NEISPRAVAN_ODGOVOR, poruka(() -> AnketaPravila.glas(Arrays.asList(0, null), 3, true)));
        assertEquals("0,2", AnketaPravila.uKolonu(List.of(0, 2)));
        assertEquals(List.of(0, 2), AnketaPravila.izKolone("0,2"));
        assertEquals(List.of(), AnketaPravila.izKolone(""));
        assertEquals(List.of(1), AnketaPravila.izKolone("x,1"));
    }

    // ---------------------------------------------------------------- stanje

    @Test
    void naCasuJeOtvorenaDokPredavanjeTrajeIRokNijeProsao() {
        Anketa a = anketa(PublikaAnkete.NA_CASU, null, null);
        assertEquals(new Stanje(true, null), AnketaPravila.stanje(a, false, T.plusMinutes(10)));
        assertEquals(new Stanje(false, null), AnketaPravila.stanje(a, true, T.plusMinutes(10)),
                "kraj predavanja bez upisanog zatvaranja: zatvorena, vreme nepoznato");
        assertEquals(new Stanje(false, null), AnketaPravila.stanje(a, null, T.plusMinutes(10)), "predavanje obrisano");
        assertEquals(new Stanje(false, T.plusHours(5)), AnketaPravila.stanje(a, false, T.plusHours(5)),
                "zaboravljeno predavanje: najkasnije 5 h");

        Anketa sRokom = anketa(PublikaAnkete.NA_CASU, T.plusSeconds(60), null);
        assertTrue(AnketaPravila.stanje(sRokom, false, T.plusSeconds(59)).otvorena());
        assertEquals(new Stanje(false, T.plusSeconds(60)), AnketaPravila.stanje(sRokom, false, T.plusSeconds(60)));

        Anketa zatvorena = anketa(PublikaAnkete.NA_CASU, T.plusSeconds(60), T.plusSeconds(30));
        assertEquals(new Stanje(false, T.plusSeconds(30)), AnketaPravila.stanje(zatvorena, false, T.plusSeconds(90)),
                "efektivno: ranije od upisanog i roka");
    }

    @Test
    void grupaJeOtvorenaDoRokaBezObziraNaPredavanje() {
        Anketa a = anketa(PublikaAnkete.GRUPA, T.plusDays(1), null);
        assertTrue(AnketaPravila.stanje(a, true, T.plusHours(6)).otvorena(), "kraj predavanja ne zatvara anketu za grupu");
        assertTrue(AnketaPravila.stanje(a, null, T.plusHours(23)).otvorena());
        assertEquals(new Stanje(false, T.plusDays(1)), AnketaPravila.stanje(a, null, T.plusDays(2)));
        a.setZatvoreno(T.plusHours(2));
        assertEquals(new Stanje(false, T.plusHours(2)), AnketaPravila.stanje(a, null, T.plusHours(3)));
    }

    @Test
    void studentVidiPrikazanRezultatSamoUProzoru() {
        Anketa naCasu = anketa(PublikaAnkete.NA_CASU, null, T.plusMinutes(1));
        Stanje s = AnketaPravila.stanje(naCasu, false, T.plusMinutes(2));
        assertFalse(AnketaPravila.vidiStudent(naCasu, s, false, T.plusMinutes(2)), "zatvorena bez prikazanog rezultata");
        naCasu.setRezultatPrikazan(true);
        assertTrue(AnketaPravila.vidiStudent(naCasu, s, false, T.plusMinutes(2)));
        assertTrue(AnketaPravila.vidiStudent(naCasu, s, false, T.plusMinutes(1).plusHours(2)));
        assertFalse(AnketaPravila.vidiStudent(naCasu, s, false, T.plusMinutes(2).plusHours(2)), "posle 2 h");
        assertFalse(AnketaPravila.vidiStudent(naCasu, s, true, T.plusMinutes(2)), "predavanje završeno");

        Anketa grupa = anketa(PublikaAnkete.GRUPA, T.plusDays(1), null);
        assertTrue(AnketaPravila.vidiStudent(grupa, AnketaPravila.stanje(grupa, null, T), null, T), "otvorena uvek");
        grupa.setRezultatPrikazan(true);
        LocalDateTime posle = T.plusDays(1).plusHours(23);
        assertTrue(AnketaPravila.vidiStudent(grupa, AnketaPravila.stanje(grupa, true, posle), true, posle));
        LocalDateTime kasno = T.plusDays(2).plusMinutes(1);
        assertFalse(AnketaPravila.vidiStudent(grupa, AnketaPravila.stanje(grupa, true, kasno), true, kasno), "posle 24 h");
    }

    // ---------------------------------------------------------------- zbirovi

    @Test
    void zbiroviPoOdgovoruIPobednik() {
        assertEquals(List.of(2, 1, 1), AnketaPravila.poOdgovoru(3, List.of("0", "0,2", "1")));
        assertEquals(List.of(0, 0), AnketaPravila.poOdgovoru(2, List.of("5")), "indeks van opsega se preskače");
        List<String> o = List.of("Da", "Ne", "Svejedno");
        assertEquals("Da", AnketaPravila.pobednik(o, List.of(3, 1, 0)));
        assertEquals("Svejedno", AnketaPravila.pobednik(o, List.of(0, 1, 2)));
        assertNull(AnketaPravila.pobednik(o, List.of(2, 2, 0)), "nerešeno");
        assertNull(AnketaPravila.pobednik(o, List.of(0, 0, 0)), "bez glasova");
        assertEquals("Ne", AnketaPravila.pobednik(o, List.of(1, 2, 1)));
        assertNull(AnketaPravila.ms(null, ZoneId.of("Europe/Belgrade")));
        assertEquals(T.atZone(ZoneId.of("Europe/Belgrade")).toInstant().toEpochMilli(),
                AnketaPravila.ms(T, ZoneId.of("Europe/Belgrade")));
    }
}
