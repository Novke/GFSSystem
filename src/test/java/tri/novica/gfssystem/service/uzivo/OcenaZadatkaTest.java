package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Provera i ocena odgovora na zadatak po šemi polja iz snimka runde. */
class OcenaZadatkaTest {

    static final List<PoljeOpcija> DVE = List.of(new PoljeOpcija(1L, "jedan"), new PoljeOpcija(2L, "dva"));
    static final List<PoljeOpcija> TRI = List.of(new PoljeOpcija(1L, "x"), new PoljeOpcija(2L, "y"), new PoljeOpcija(3L, "z"));

    static final VarijantaSnimak V = new VarijantaSnimak(12L, "V1", 3L, "svg", null, List.of(
            new Polje("a", "a)", TipPolja.BROJ, 0.5, OdstupanjeTip.APSOLUTNO, "m", null, null, null),
            new Polje("b", "b)", TipPolja.TEKST, null, null, null, null, true, null),
            new Polje("c", "c)", TipPolja.JEDAN_TACAN, null, null, null, null, null, DVE),
            new Polje("d", "d)", TipPolja.VISE_TACNIH, null, null, null, null, null, TRI)),
            Map.of("a", new OdgovorPolja(45.0, null, null, null),
                    "b", new OdgovorPolja(null, List.of("1011 0110"), null, null),
                    "c", new OdgovorPolja(null, null, 2L, null),
                    "d", new OdgovorPolja(null, null, null, List.of(1L, 3L))));

    static Map<String, Object> tacan() {
        Map<String, Object> m = new HashMap<>();
        m.put("a", "45,4");
        m.put("b", "10110110");
        m.put("c", List.of(2));
        m.put("d", List.of(3, 1));
        return m;
    }

    static void greska(String poruka, Map<String, Object> dato) {
        SystemException ex = assertThrows(SystemException.class, () -> OcenaZadatka.oceni(V, dato));
        assertEquals(400, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    @Test
    void tacanOdgovor() {
        OcenaZadatka.Ishod i = OcenaZadatka.oceni(V, tacan());
        assertTrue(i.tacno());
        assertEquals(List.of("a", "b", "c", "d"), new ArrayList<>(i.polja().keySet()), "redom šeme");
        i.polja().values().forEach(p -> assertTrue(p.tacno()));
        assertEquals("45,4", i.polja().get("a").vrednost());
        assertEquals(List.of(1L, 3L), i.polja().get("d").vrednost(), "sortirano");
        assertEquals(List.of(2L), i.polja().get("c").vrednost());
    }

    @Test
    void netacnoPolje() {
        Map<String, Object> m = tacan();
        m.put("a", "46");
        OcenaZadatka.Ishod i = OcenaZadatka.oceni(V, m);
        assertFalse(i.tacno());
        assertFalse(i.polja().get("a").tacno());
        assertTrue(i.polja().get("b").tacno());
    }

    @Test
    void greske() {
        Map<String, Object> m = tacan();
        m.remove("d");
        greska("Popuni sva polja.", m);
        m = tacan();
        m.put("a", "  ");
        greska("Popuni sva polja.", m);
        m = tacan();
        m.put("a", null);
        greska("Popuni sva polja.", m);
        m = tacan();
        m.put("d", List.of());
        greska("Polje „d)“: izaberi bar jedan odgovor.", m);
        m = tacan();
        m.put("d", List.of(1, 1));
        greska("Polje „d)“: izaberi bar jedan odgovor.", m);
        m = tacan();
        m.put("c", List.of(1, 2));
        greska("Polje „c)“: izaberi jedan odgovor.", m);
        m = tacan();
        m.put("c", List.of(9));
        greska("Polje „c)“: izaberi jedan odgovor.", m);
        m = tacan();
        m.put("c", 2);
        greska("Polje „c)“: izaberi jedan odgovor.", m);
        m = tacan();
        m.put("a", "abc");
        greska("Polje „a)“: unesi broj.", m);
        m = tacan();
        m.put("a", "9".repeat(400));
        greska("Polje „a)“: unesi broj.", m);
        m = tacan();
        m.put("b", "x".repeat(201));
        greska("Polje „b)“: odgovor mora imati od 1 do 200 znakova.", m);
        m = tacan();
        m.put("x", "1");
        greska("Nepoznato polje odgovora.", m);
        greska("Popuni sva polja.", null);
    }

    @Test
    void papir() {
        VarijantaSnimak papir = new VarijantaSnimak(13L, "P", 4L, "svg", null, List.of(), Map.of());
        SystemException ex = assertThrows(SystemException.class, () -> OcenaZadatka.oceni(papir, Map.of()));
        assertEquals("Ovaj zadatak se radi na papiru.", ex.getMessage());
    }
}
