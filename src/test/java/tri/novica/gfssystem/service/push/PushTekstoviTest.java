package tri.novica.gfssystem.service.push;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.service.raspored.PromenaTermina;
import tri.novica.gfssystem.service.raspored.TerminOpis;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tekstovi push poruka, prozor podsetnika i VAPID kontakt (bez baze). */
class PushTekstoviTest {

    static final LocalDate CET = LocalDate.of(2026, 10, 29);
    static final TerminOpis TERMIN = new TerminOpis(7L, CET.plusDays(1), LocalTime.of(10, 15), LocalTime.of(12, 0),
            "Osnove računarstva", "GD-2026");

    @Test
    void danIDatum() {
        assertEquals("čet 29.10.", PushTekstovi.danIDatum(CET));
        assertEquals("ned 1.11.", PushTekstovi.danIDatum(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void otkazanCasINapomena() {
        PushPoruka p = PushTekstovi.promena(new PromenaTermina(PromenaTermina.Vrsta.OTKAZAN, TERMIN, "x".repeat(200)));
        assertEquals("Čas je otkazan", p.naslov());
        assertEquals("Osnove računarstva, pet 30.10. u 10:15. " + "x".repeat(119) + "…", p.tekst());
        assertEquals("nalog", p.putanja());
        assertEquals("cas-7-2026-10-30", p.oznaka());
        PushPoruka v = PushTekstovi.promena(new PromenaTermina(PromenaTermina.Vrsta.VRACEN, TERMIN, null));
        assertEquals("Čas se ipak održava", v.naslov());
        assertEquals("Osnove računarstva, pet 30.10. u 10:15.", v.tekst());
        assertEquals(p.oznaka(), v.oznaka(), "vraćanje zamenjuje otkazivanje na telefonu");
    }

    @Test
    void podsetnikIOcene() {
        PushPoruka p = PushTekstovi.podsetnik(CET, List.of(new PushTekstovi.SutrasnjiTermin(LocalTime.of(8, 0), "A")));
        assertEquals("Sutra imaš čas", p.naslov());
        assertEquals("08:00 A. Vidimo se!", p.tekst());
        assertEquals("podsetnik-2026-10-29", p.oznaka());
        assertEquals("Ocene su ažurirane", PushTekstovi.ocene(3L, "B", true).naslov());
        assertEquals("B: pogledaj predlog ocene.", PushTekstovi.ocene(3L, "B", false).tekst());
    }

    @Test
    void posleCasaMnozina() {
        assertEquals("Prezentacije su dostupne za ponavljanje i 2 nova tipa za vežbanje.",
                PushTekstovi.posleCasa(CET, 2, 2, 1L).tekst());
        assertEquals("Dostupan je 1 nov tip za vežbanje.", PushTekstovi.posleCasa(CET, 0, 1, null).tekst());
        assertEquals("Dostupna su 3 nova tipa za vežbanje.", PushTekstovi.posleCasa(CET, 0, 3, null).tekst());
        assertEquals("Dostupno je 5 novih tipova za vežbanje.", PushTekstovi.posleCasa(CET, 0, 5, null).tekst());
        assertEquals("Dostupno je 12 novih tipova za vežbanje.", PushTekstovi.posleCasa(CET, 0, 12, null).tekst());
        assertEquals("21 nov tip", PushTekstovi.tipova(21));
        assertEquals("nalog", PushTekstovi.posleCasa(CET, 1, 0, null).putanja());
        assertEquals("nalog/predmet/4", PushTekstovi.posleCasa(CET, 1, 0, 4L).putanja());
    }

    @Test
    void nastavnikuBezImenaIRazloga() {
        PushPoruka p = PushTekstovi.nastavniku(VrstaObavestenja.MNOGO_NAJAVA, TERMIN, 6, 5);
        assertEquals("6 studenata neće doći", p.naslov());
        assertEquals("Osnove računarstva, GD-2026, pet 30.10. u 10:15 (prag 5).", p.tekst());
        assertEquals("raspored/termini?od=2026-10-30", p.putanja());
        for (VrstaObavestenja v : VrstaObavestenja.values()) {
            assertNotNull(PushTekstovi.nastavniku(v, TERMIN, 6, 5).naslov(), "svaka vrsta ima tekst");
        }
    }

    @Test
    void prozorPodsetnika() {
        LocalDate d = CET;
        LocalTime sest = LocalTime.of(18, 0);
        assertFalse(PodsetnikPosao.dospeva(d.atTime(17, 59), sest));
        assertTrue(PodsetnikPosao.dospeva(d.atTime(18, 0), sest));
        assertTrue(PodsetnikPosao.dospeva(d.atTime(21, 0), sest), "nadoknada do 3 h");
        assertFalse(PodsetnikPosao.dospeva(d.atTime(21, 1), sest));
        LocalTime osam = LocalTime.of(20, 0);
        assertTrue(PodsetnikPosao.dospeva(d.atTime(22, 0), osam));
        assertFalse(PodsetnikPosao.dospeva(LocalDateTime.of(d, LocalTime.of(22, 1)), osam), "nikad posle 22:00");
    }

    @Test
    void vapidKontakt() {
        assertEquals("mailto:nastavnik@gfs.rs", PushPosiljalac.kontakt(" nastavnik@gfs.rs ", "https://a"));
        assertEquals("https://a", PushPosiljalac.kontakt("nastavnik@example.test", "https://a"));
        assertEquals("https://gfs.example.test", PushPosiljalac.kontakt(null, "https://gfs.example.test/, https://b"));
        assertEquals("https://gfs.example.test", PushPosiljalac.kontakt("nije adresa", "https://gfs.example.test"));
    }
}
