package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Imena MySQL brava: prefiks šeme (produkcija i staging dele server) i granica od 64 znaka. */
class ImenaBravaTest {

    static final String NAJDUZE_LOGICKO = "gfs-pomoc:" + "a".repeat(32);

    @Test
    void imeImaPrefiksSeme() {
        assertEquals("gf:gfs-mejl-nastavniku", ImenaBrava.ime("gf", "gfs-mejl-nastavniku"));
        assertEquals("gf_staging:gfs-mejl-nastavniku", ImenaBrava.ime("gf_staging", "gfs-mejl-nastavniku"));
        assertNotEquals(ImenaBrava.ime("gf", "gfs-kod:x"), ImenaBrava.ime("gf_staging", "gfs-kod:x"));
    }

    @Test
    void praznaSemaDajeLogickoIme() {
        assertEquals("gfs-kod:x", ImenaBrava.ime("", "gfs-kod:x"));
        assertEquals("gfs-kod:x", ImenaBrava.ime(null, "gfs-kod:x"));
    }

    @Test
    void imeNikadNijeDuzeOdGranice() {
        String duga = "gf_" + "s".repeat(60);
        String ime = ImenaBrava.ime(duga, NAJDUZE_LOGICKO);
        assertTrue(ime.length() <= ImenaBrava.MAX_DUZINA, ime + " " + ime.length());
        assertTrue(ime.endsWith(":" + NAJDUZE_LOGICKO), ime);
        // šema od tačno granice se ne skraćuje
        String granica = "x".repeat(ImenaBrava.MAX_SEMA);
        assertEquals(granica + ":" + NAJDUZE_LOGICKO, ImenaBrava.ime(granica, NAJDUZE_LOGICKO));
        assertEquals(63, ImenaBrava.ime(granica, NAJDUZE_LOGICKO).length());
    }

    @Test
    void razlicitaDugackaSemaDajeRazlicitoIme() {
        String a = "gf_proizvodnja_okruzenje_a";
        String b = "gf_proizvodnja_okruzenje_b";
        assertNotEquals(ImenaBrava.ime(a, "gfs-kod:x"), ImenaBrava.ime(b, "gfs-kod:x"));
        assertEquals(ImenaBrava.ime(a, "gfs-kod:x"), ImenaBrava.ime(a, "gfs-kod:x"));
    }
}
