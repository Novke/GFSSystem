package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NormalizacijaTest {

    @Test void kvaciceIRazmaci() { assertEquals("cvrstoca betona", Normalizacija.tekst("  Čvrstoća   BETONA ")); }

    @Test void djIDz() { assertEquals("djak dzep", Normalizacija.tekst("Đak džep")); }

    @Test void cirilica() {
        assertEquals("cvrstoca", Normalizacija.tekst("Чврстоћа"));
        assertEquals("ljubav njiva dzak", Normalizacija.tekst("Љубав њива џак"));
    }

    @Test void brojSaZarezom() {
        assertEquals(3.14, Normalizacija.broj("3,14"));
        assertEquals(1000.5, Normalizacija.broj("1 000,5"));
        assertEquals(-2.0, Normalizacija.broj("-2"));
    }

    @Test void nijeBroj() {
        assertNull(Normalizacija.broj("abc"));
        assertNull(Normalizacija.broj("1e3"));
        assertNull(Normalizacija.broj(""));
        assertNull(Normalizacija.broj("1.2.3"));
    }

    @Test void ime() { assertEquals("Ana Marić", Normalizacija.ime(" Ana \t Marić\u0007 ")); }

    @Test void nullVrednosti() {
        assertEquals("", Normalizacija.tekst(null));
        assertEquals("", Normalizacija.ime(null));
        assertNull(Normalizacija.broj(null));
    }
}
