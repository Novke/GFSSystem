package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;

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
}
