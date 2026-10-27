package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Uneto po polju za pregled izvođenja: tekst kakav nastavnik čita, redom šeme snimka. */
class UnetoZadatkaTest {

    static Polje polje(String kljuc, TipPolja tip, String jedinica, List<PoljeOpcija> opcije) {
        return new Polje(kljuc, "Oznaka " + kljuc, tip, null, null, jedinica, null, null, opcije);
    }

    static final List<PoljeOpcija> OPCIJE = List.of(new PoljeOpcija(1L, "$2$"), new PoljeOpcija(2L, "tri"),
            new PoljeOpcija(3L, "pet"));

    static VarijantaSnimak varijanta(Polje... polja) {
        return new VarijantaSnimak(7L, "V7", 1L, "svg", null, List.of(polja), Map.of());
    }

    @Test
    void sviTipoviRedomSeme() {
        VarijantaSnimak v = varijanta(polje("d", TipPolja.VISE_TACNIH, null, OPCIJE), polje("a", TipPolja.BROJ, "m", null),
                polje("b", TipPolja.TEKST, null, null), polje("c", TipPolja.JEDAN_TACAN, null, OPCIJE));
        Map<String, PoljeOdgovora> dato = new LinkedHashMap<>();
        dato.put("a", new PoljeOdgovora("45.5", true));
        dato.put("b", new PoljeOdgovora("2D", false));
        dato.put("c", new PoljeOdgovora(List.of(2), true));
        dato.put("d", new PoljeOdgovora(List.of(3L, 1L), false));

        Map<String, String> uneto = UnetoZadatka.tekstovi(v, dato);

        assertEquals(List.of("d", "a", "b", "c"), new ArrayList<>(uneto.keySet()), "redom šeme");
        assertEquals("$2$, pet", uneto.get("d"), "VISE_TACNIH: tekstovi opcija redom šeme");
        assertEquals("45.5 m", uneto.get("a"), "BROJ: kako je uneto, sa jedinicom");
        assertEquals("2D", uneto.get("b"));
        assertEquals("tri", uneto.get("c"));
    }

    @Test
    void bezOdgovoraIliVarijanteNemaUnosa() {
        VarijantaSnimak v = varijanta(polje("a", TipPolja.BROJ, null, null), polje("b", TipPolja.TEKST, null, null));
        assertEquals(Map.of("a", "11"), UnetoZadatka.tekstovi(v, Map.of("a", new PoljeOdgovora("11", true))),
                "polje bez odgovora se izostavlja");
        assertEquals(Map.of(), UnetoZadatka.tekstovi(null, Map.of("a", new PoljeOdgovora("11", true))));
        assertEquals(Map.of(), UnetoZadatka.tekstovi(v, null));
    }

    @Test
    void nepoznatOblikIliOpcija() {
        VarijantaSnimak v = varijanta(polje("a", TipPolja.BROJ, " ", null), polje("c", TipPolja.JEDAN_TACAN, null, OPCIJE),
                polje("d", TipPolja.VISE_TACNIH, null, null));
        Map<String, PoljeOdgovora> dato = Map.of("a", new PoljeOdgovora(" 7 ", true),
                "c", new PoljeOdgovora(List.of(9L), false), "d", new PoljeOdgovora("x", false));
        Map<String, String> uneto = UnetoZadatka.tekstovi(v, dato);
        assertEquals("7", uneto.get("a"), "prazna jedinica se ne dodaje");
        assertEquals("#9", uneto.get("c"), "opcija koje više nema u šemi snimka: id");
        assertFalse(uneto.containsKey("d"), "opcija bez liste: ništa");
    }
}
