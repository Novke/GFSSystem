package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.exceptions.SystemException;

import static org.junit.jupiter.api.Assertions.*;

class VezbaTest {

    @Test
    void proveriPusta0Do99INull() {
        assertNull(Vezba.proveri(null));
        assertEquals(0, Vezba.proveri(0));
        assertEquals(99, Vezba.proveri(99));
        for (int los : new int[]{-1, 100}) {
            SystemException e = assertThrows(SystemException.class, () -> Vezba.proveri(los));
            assertEquals(Vezba.GRANICE, e.getMessage());
        }
    }

    @Test
    void brojIzNazivaKaoPopunaV21() {
        assertEquals(1, Vezba.izNaziva("Vežbe 1 – zadaci"));
        assertEquals(1, Vezba.izNaziva("Vezbe 1 - Brojevni sistemi, prevodjenje"));
        assertEquals(3, Vezba.izNaziva("Vežbe 3 i 4 – zadaci"));
        assertEquals(12, Vezba.izNaziva("  VEŽBE 12: ponavljanje"));
        assertEquals(4, Vezba.izNaziva("vezbe 4"));
        assertNull(Vezba.izNaziva("Upoznavanje"));
        assertNull(Vezba.izNaziva("Vežbe"));
        assertNull(Vezba.izNaziva("Vežbe 123"));
        assertNull(Vezba.izNaziva("Ponavljanje za vežbe 3"));
        assertNull(Vezba.izNaziva("Vežbe3"));
        assertNull(Vezba.izNaziva(null));
    }
}
