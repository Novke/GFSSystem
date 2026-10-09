package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PovezanostRegistarTest {

    final PovezanostRegistar r = new PovezanostRegistar();

    @Test
    void viseVezaIstogUcesnika() {
        r.povezan(1L, "a");
        r.povezan(1L, "b");
        r.povezan(2L, "c");
        r.povezan(1L, "a");   // isti događaj dva puta se ne broji dvaput
        assertEquals(2, r.brojPovezanih(List.of(1L, 2L, 3L)));
        r.prekinut("a");
        assertTrue(r.jePovezan(1L));
        r.prekinut("b");
        assertFalse(r.jePovezan(1L));
        r.prekinut("b");
        r.prekinut("nepoznata");
        assertEquals(1, r.brojPovezanih(List.of(1L, 2L)));
    }
}
