package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class IndeksUtilTest {
    @Test
    void normalizujUklanjaRazmakeIPodizeSlova() {
        assertEquals("GD12", IndeksUtil.normalizuj("  gd 12 "));
        assertEquals("GD12/2024", IndeksUtil.normalizuj("gd\t12 / 2024"));
        assertEquals("", IndeksUtil.normalizuj("   "));
        assertNull(IndeksUtil.normalizuj(null));
    }

    @Test
    void normalizujUklanjaNeprekidiveRazmake() {
        assertEquals("GD12", IndeksUtil.normalizuj("GD\u00A012"));       // NBSP (telefonske tastature, copy-paste)
        assertEquals("GD12", IndeksUtil.normalizuj("\u202FGD\u00A0 12")); // uski NBSP
    }

    @Test
    void normalizujNeZavisiOdPodrazumevanogLokala() {
        Locale stari = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));   // turski: "i".toUpperCase() bi bio "İ"
            assertEquals("ID1", IndeksUtil.normalizuj("id1"));
        } finally {
            Locale.setDefault(stari);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "GD5|2026|GD5",
            "gd 05|2026|GD5",
            "GD005|NULL|GD5",
            "GD0|2026|GD0",
            "GD100|2026|GD100",
            "GD99/2026|2026|GD99",
            "GD99 / 2026|2026|GD99",
            "GD99/2025|2026|GD99/2025",
            "GD99/2026|NULL|GD99/2026",
            "123/2026|2026|123",
            "AB-12|2026|AB-12",
            "GD12A|2026|GD12A",
    })
    void kanonski(String unos, Integer godina, String ocekivano) {
        assertEquals(ocekivano, IndeksUtil.kanonski(unos, godina));
    }

    @Test
    void kanonskiNullJeNull() {
        assertNull(IndeksUtil.kanonski(null, 2026));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GD1", "GD9", "GD99", "GD999"})
    void javniOblik(String indeks) {
        assertTrue(IndeksUtil.jeJavniOblik(indeks));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"GD", "GD0", "GD05", "GD1000", "AR5", "GD5/2026", "gd5"})
    void nijeJavniOblik(String indeks) {
        assertFalse(IndeksUtil.jeJavniOblik(indeks));
    }

    @Test
    void razdvojiStariOblik() {
        assertEquals(new IndeksUtil.Razdvojen("GD5", 2026), IndeksUtil.razdvoji(" gd5/2026"));
        assertEquals(new IndeksUtil.Razdvojen("GD5", null), IndeksUtil.razdvoji("gd 5"));
        assertEquals(new IndeksUtil.Razdvojen(null, null), IndeksUtil.razdvoji(null));
    }
}
