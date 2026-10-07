package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndeksUtilTest {
    @Test
    void normalizujUklanjaRazmakeIPodizeSlova() {
        assertEquals("GD12", IndeksUtil.normalizuj("  gd 12 "));
        assertEquals("GD12/2024", IndeksUtil.normalizuj("gd\t12 / 2024"));
        assertEquals("", IndeksUtil.normalizuj("   "));
        assertNull(IndeksUtil.normalizuj(null));
    }
}
