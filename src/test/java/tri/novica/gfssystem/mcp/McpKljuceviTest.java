package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Parsiranje {@code gfs.mcp.kljucevi} (oznaka:sha256hex, zarezom) i poređenje ključa sa hešom. */
class McpKljuceviTest {

    // sintetički test ključevi iz plana (Ugovori), nikad pravi
    static final String TEST_KLJUC = "gfs_TEST000000000000000000000000000000000000000";
    static final String TEST_HES = "fba7afe9974287572c8bf87caa175a78ddd3e98e337edad300dce78199168589";
    static final String DRUGI_KLJUC = "gfs_DRUGI11111111111111111111111111111111111111";
    static final String DRUGI_HES = "9a61f2d52530e514d1bbfbadf992d12616d92448625c314ad3c21ed4ab3090e4";

    @Test
    void praznoZnaciIskljucen() {
        assertTrue(McpKljucevi.parsiraj("").prazno());
        assertTrue(McpKljucevi.parsiraj("  ").prazno());
        assertTrue(McpKljucevi.parsiraj(null).prazno());
        assertEquals(List.of(), McpKljucevi.parsiraj("").oznake());
        assertEquals(Optional.empty(), McpKljucevi.parsiraj("").oznaka(TEST_KLJUC));
    }

    @Test
    void oznakeRedomKaoUSvojstvu() {
        McpKljucevi k = McpKljucevi.parsiraj("novica-dev:" + TEST_HES + ",staro:" + DRUGI_HES);
        assertFalse(k.prazno());
        assertEquals(List.of("novica-dev", "staro"), k.oznake());
    }

    static Stream<String> neispravneStavke() {
        return Stream.of(
                TEST_KLJUC,                                     // sirov ključ umesto heša
                "a:" + TEST_HES.toUpperCase(),                  // velika slova u hešu
                "bezdvotacke",
                "a:abc",                                        // kratak heš
                "A-B:" + TEST_HES,                              // oznaka sa velikim slovima
                ":" + TEST_HES,                                 // prazna oznaka
                "a".repeat(41) + ":" + TEST_HES,                // oznaka duža od 40
                "a:" + TEST_HES + "0",                          // heš od 65 znakova
                "a :" + TEST_HES,                               // razmak u stavci
                "a:" + TEST_KLJUC);                             // ključ na mestu heša
    }

    @ParameterizedTest
    @MethodSource("neispravneStavke")
    void neispravnaStavkaRusiStartBezSadrzajaUPoruci(String stavka) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> McpKljucevi.parsiraj(stavka));
        assertEquals("Neispravna stavka gfs.mcp.kljucevi: #1", e.getMessage());
        assertFalse(e.getMessage().contains(stavka));
    }

    @Test
    void porukaNosiRedniBrojStavke() {
        IllegalStateException druga = assertThrows(IllegalStateException.class,
                () -> McpKljucevi.parsiraj("test:" + TEST_HES + "," + DRUGI_KLJUC));
        assertEquals("Neispravna stavka gfs.mcp.kljucevi: #2", druga.getMessage());
        // prazna stavka posle zareza nije tiho ignorisana
        IllegalStateException prazna = assertThrows(IllegalStateException.class,
                () -> McpKljucevi.parsiraj("test:" + TEST_HES + ","));
        assertEquals("Neispravna stavka gfs.mcp.kljucevi: #2", prazna.getMessage());
        // razmak posle zareza se ne skida (format je strog), samo krajevi cele vrednosti
        IllegalStateException razmak = assertThrows(IllegalStateException.class,
                () -> McpKljucevi.parsiraj("test:" + TEST_HES + ", drugi:" + DRUGI_HES));
        assertEquals("Neispravna stavka gfs.mcp.kljucevi: #2", razmak.getMessage());
        assertEquals(List.of("test"), McpKljucevi.parsiraj(" test:" + TEST_HES + "\n").oznake());
    }

    @Test
    void oznakaZaIspravanKljuc() {
        McpKljucevi k = McpKljucevi.parsiraj("test:" + TEST_HES);
        assertEquals(Optional.of("test"), k.oznaka(TEST_KLJUC));
        assertEquals(Optional.empty(), k.oznaka("gfs_x"));
        assertEquals(Optional.empty(), k.oznaka(DRUGI_KLJUC));
        assertEquals(Optional.empty(), k.oznaka(TEST_HES));            // heš nije ključ
        assertEquals(Optional.empty(), k.oznaka(null));
        assertEquals(Optional.empty(), k.oznaka(""));
    }

    @Test
    void rotacijaObaKljucaVaze() {
        McpKljucevi k = McpKljucevi.parsiraj("novica-dev:" + DRUGI_HES + ",novica-dev-prethodni:" + TEST_HES);
        assertEquals(Optional.of("novica-dev"), k.oznaka(DRUGI_KLJUC));
        assertEquals(Optional.of("novica-dev-prethodni"), k.oznaka(TEST_KLJUC));
    }
}
