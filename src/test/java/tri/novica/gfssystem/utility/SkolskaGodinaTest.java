package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class SkolskaGodinaTest {

    @Test
    void oktobarPripadaNovojGodini() {
        assertEquals(2025, SkolskaGodina.za(LocalDate.of(2025, 10, 1)));
    }

    @Test
    void septembarPripadaPrethodnoj() {
        assertEquals(2024, SkolskaGodina.za(LocalDate.of(2025, 9, 30)));
    }

    @Test
    void granice() {
        assertEquals(LocalDate.of(2025, 10, 1), SkolskaGodina.pocetak(2025));
        assertEquals(LocalDate.of(2026, 9, 30), SkolskaGodina.kraj(2025));
    }

    @Test
    void tekucaIdeKrozClock() {
        ZoneId zona = ZoneId.of("Europe/Belgrade");
        // 30. 9. 2026. 23:30 u Beogradu je još školska 2025, iako je u UTC-u isti dan
        Clock septembar = Clock.fixed(Instant.parse("2026-09-30T21:30:00Z"), zona);
        Clock oktobar = Clock.fixed(Instant.parse("2026-09-30T22:30:00Z"), zona);
        assertEquals(2025, SkolskaGodina.tekuca(septembar));
        assertEquals(2026, SkolskaGodina.tekuca(oktobar));
    }

    @Test
    void proveriPropustaNullIRazumneGodine() {
        assertDoesNotThrow(() -> SkolskaGodina.proveri(null));
        assertDoesNotThrow(() -> SkolskaGodina.proveri(2025));
        assertDoesNotThrow(() -> SkolskaGodina.proveri(SkolskaGodina.MIN));
        assertDoesNotThrow(() -> SkolskaGodina.proveri(SkolskaGodina.MAX));
        assertDoesNotThrow(() -> SkolskaGodina.kraj(SkolskaGodina.MAX));   // 30. 9. 9999 je u opsegu MySQL DATE
    }

    @Test
    void proveriOdbijaGodinuVanOpsega() {
        for (int g : new int[]{0, -1, 1899, 9999, 10000, Integer.MAX_VALUE}) {
            SystemException ex = assertThrows(SystemException.class, () -> SkolskaGodina.proveri(g));
            assertEquals(400, ex.getCode());
            assertEquals("Neispravan parametar: godina.", ex.getMessage());
        }
    }
}
