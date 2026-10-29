package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.uzivo.Odgovor;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KratakOdgovorTest {

    static PitanjeSnimak snimak(TipPitanja tip) {
        // opcije namerno van redosleda: slova idu po rb
        List<OpcijaSnimak> opcije = List.of(new OpcijaSnimak(7L, 2, "B", false), new OpcijaSnimak(5L, 1, "A", true),
                new OpcijaSnimak(9L, 3, "C", true));
        return new PitanjeSnimak(1L, 2L, tip, "?", null, null, opcije, 2.5, null, null, null, null, null, null, null);
    }

    @Test
    void opcijeKaoSlovaPoRedu() {
        Odgovor o = new Odgovor();
        o.setOpcije("9,5");
        assertEquals("A, C", KratakOdgovor.pitanje(snimak(TipPitanja.VISE_TACNIH), o));
        o.setOpcije("x");
        assertNull(KratakOdgovor.pitanje(snimak(TipPitanja.JEDAN_TACAN), o));
    }

    @Test
    void brojTekstSkalaIBezOdgovora() {
        Odgovor o = new Odgovor();
        o.setBroj(12.50);
        assertEquals("12,5", KratakOdgovor.pitanje(snimak(TipPitanja.BROJ), o));
        o.setBroj(3.0);
        assertEquals("3", KratakOdgovor.pitanje(snimak(TipPitanja.BROJ), o));
        o.setTekst("  " + "x".repeat(60));
        String t = KratakOdgovor.pitanje(snimak(TipPitanja.KRATAK_TEKST), o);
        assertEquals(KratakOdgovor.NAJVISE, t.length());
        assertTrue(t.endsWith("…"));
        o.setSkala(4);
        assertEquals("4", KratakOdgovor.pitanje(snimak(TipPitanja.SKALA), o));
        assertNull(KratakOdgovor.pitanje(snimak(TipPitanja.SKALA), null));
        assertNull(KratakOdgovor.zadatak(null, null));
    }
}
