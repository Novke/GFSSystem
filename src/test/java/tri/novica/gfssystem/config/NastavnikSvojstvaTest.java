package tri.novica.gfssystem.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NastavnikSvojstvaTest {

    // Javni heš poznate test lozinke (cost 12, $2y$ oblik); ne štiti ništa.
    private static final String HES_2Y = "$2y$12$" + "a".repeat(53);

    @Test
    void praznoNijePodeseno() {
        assertFalse(new NastavnikSvojstva("", "", "").podeseno());
        assertFalse(new NastavnikSvojstva(null, null, null).podeseno());
    }

    @Test
    void samoKorisnikIliSamoHesNijePodeseno() {
        assertFalse(new NastavnikSvojstva("nastavnik", "", "").podeseno());
        assertFalse(new NastavnikSvojstva("", HES_2Y, "").podeseno());
        assertFalse(new NastavnikSvojstva("  ", HES_2Y, "").podeseno());
    }

    @Test
    void sirovaLozinkaRusiStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new NastavnikSvojstva("nastavnik", "tajna-lozinka", ""));
        assertEquals("gfs.nastavnik.lozinka-bcrypt nije bcrypt heš", e.getMessage());
    }

    @Test
    void hesSaCostVanOpsegaRusiStart() {
        assertThrows(IllegalStateException.class, () -> new NastavnikSvojstva("n", "$2y$09$" + "a".repeat(53), ""));
        assertThrows(IllegalStateException.class, () -> new NastavnikSvojstva("n", "$2y$15$" + "a".repeat(53), ""));
        assertThrows(IllegalStateException.class, () -> new NastavnikSvojstva("n", "$2y$12$" + "a".repeat(52), ""));
        assertThrows(IllegalStateException.class, () -> new NastavnikSvojstva("n", "$2x$12$" + "a".repeat(53), ""));
    }

    @Test
    void ispravanHesJePodesen() {
        for (String verzija : new String[]{"2a", "2b", "2y"}) {
            assertTrue(new NastavnikSvojstva("nastavnik", "$" + verzija + "$12$" + "a".repeat(53), "").podeseno());
        }
        assertTrue(new NastavnikSvojstva("nastavnik", HES_2Y, "nastavnik@example.test").podeseno());
    }

    @Test
    void zaPraznoNePrijavljujeGresku() {
        assertDoesNotThrow(() -> new NastavnikSvojstva("", "", ""));
    }
}
