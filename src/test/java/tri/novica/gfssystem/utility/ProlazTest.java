package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.Polaganje;

import static org.junit.jupiter.api.Assertions.*;

class ProlazTest {

    @Test
    void bezPragaNikoNijePolozio() {
        assertFalse(Prolaz.polozeno(100.0, null, false));
    }

    @Test
    void tacnoNaPraguProlazi() {
        assertTrue(Prolaz.polozeno(25.0, 25, false));
        assertTrue(Prolaz.polozeno(25.0, 25, null));
    }

    @Test
    void ispodPragaPada() {
        assertFalse(Prolaz.polozeno(24.99, 25, false));
    }

    @Test
    void pragNulaProlaziSvaStoImaPoene() {
        assertTrue(Prolaz.polozeno(0.0, 0, false));
    }

    @Test
    void prepisivaoPada() {
        assertFalse(Prolaz.polozeno(50.0, 25, true));
    }

    @Test
    void bezPoenaNijePolozeno() {
        assertFalse(Prolaz.polozeno(null, 25, false));
    }

    @Test
    void sacuvanoPoljePolozioSeIgnorise() {
        tri.novica.gfssystem.entity.Test t = new tri.novica.gfssystem.entity.Test();
        t.setPragProlaza(25);
        Polaganje p = Polaganje.defaultPolaganje(t, null);
        p.setOstvareniPoeni(10.0);
        p.setPolozio(true);
        assertFalse(Prolaz.polozeno(p));
        p.setOstvareniPoeni(25.0);
        p.setPolozio(false);
        assertTrue(Prolaz.polozeno(p));
        t.setPragProlaza(null);
        assertFalse(Prolaz.polozeno(p));
    }
}
