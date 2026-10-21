package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.entity.uzivo.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PitanjeSnimakTest {

    static PitanjeOpcija opcija(long id, int rb, String tekst, boolean tacna) {
        PitanjeOpcija o = new PitanjeOpcija();
        o.setId(id);
        o.setRb(rb);
        o.setTekst(tekst);
        o.setTacna(tacna);
        return o;
    }

    static Slajd slajd() {
        Medij m = new Medij();
        m.setId("uuid-1");
        Pitanje p = new Pitanje();
        p.setId(5L);
        p.setTip(TipPitanja.JEDAN_TACAN);
        p.setTekst("Koliko?");
        p.setSlika(m);
        p.setVremeSekunde(20);
        p.setOpcije(new ArrayList<>(List.of(opcija(2, 2, "B", false), opcija(1, 1, "A", true))));
        Slajd s = new Slajd();
        s.setId(9L);
        s.setPitanje(p);
        return s;
    }

    @Test void odKopiraPitanjeSaOpcijamaPoRb() {
        PitanjeSnimak s = PitanjeSnimak.od(slajd());
        assertEquals(5L, s.pitanjeId());
        assertEquals(9L, s.slajdId());
        assertEquals("uuid-1", s.slikaId());
        assertEquals(20, s.vremeSekunde());
        assertEquals(List.of(1L, 2L), s.opcije().stream().map(OpcijaSnimak::id).toList());
        assertTrue(s.imaTacanOdgovor());
    }

    @Test void odBezPitanjaBacaIzuzetak() {
        assertThrows(IllegalArgumentException.class, () -> PitanjeSnimak.od(new Slajd()));
    }

    @Test void jsonKrugUJednomSmeru() {
        JsonMapper mapper = JsonMapper.builder().build();
        PitanjeSnimak s = PitanjeSnimak.od(slajd());
        String json = mapper.writeValueAsString(s);
        assertFalse(json.contains("imaTacanOdgovor"));
        assertEquals(s, mapper.readValue(json, PitanjeSnimak.class));
    }

    /** Spec 16.1, odluka 6 faze 5: server ne tumači formule, snimak runde čuva {@code $...$} i {@code \$} doslovno. */
    @Test void snimakCuvaFormuleDoslovno() {
        String tekst = "Koliko je $1011_2$ dekadno? \\$5";
        Slajd s = slajd();
        s.getPitanje().setTekst(tekst);
        s.getPitanje().setOpcije(new ArrayList<>(List.of(opcija(1, 1, "$11$", true), opcija(2, 2, "$13$", false))));

        PitanjeSnimak snimak = PitanjeSnimak.od(s);
        assertEquals(tekst, snimak.tekst());
        assertEquals(List.of("$11$", "$13$"), snimak.opcije().stream().map(OpcijaSnimak::tekst).toList());

        JsonMapper mapper = JsonMapper.builder().build();
        PitanjeSnimak procitan = mapper.readValue(mapper.writeValueAsString(snimak), PitanjeSnimak.class);
        assertEquals(tekst, procitan.tekst());
        assertEquals(List.of("$11$", "$13$"), procitan.opcije().stream().map(OpcijaSnimak::tekst).toList());
        assertEquals(snimak, procitan);
    }
}
