package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.service.uzivo.ZadatakRezultatBuilder.OdgovorZadatka;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Rezultat runde zadatka po varijanti i polju, sa tri nezavisna prekidača vidljivosti. */
class ZadatakRezultatBuilderTest {

    static final Polje BROJ = new Polje("a", "a)", TipPolja.BROJ, 0.0, OdstupanjeTip.APSOLUTNO, null, null, null, null);
    static final Polje TEKST = new Polje("b", "b)", TipPolja.TEKST, null, null, null, null, true, null);
    static final Polje JEDAN = new Polje("c", "c)", TipPolja.JEDAN_TACAN, null, null, null, null, null,
            List.of(new PoljeOpcija(1L, "jedan"), new PoljeOpcija(2L, "dva")));

    static final VarijantaSnimak A = new VarijantaSnimak(1L, "A", 3L, "svg-A", "res-A", List.of(BROJ, TEKST, JEDAN),
            Map.of("a", new OdgovorPolja(45.0, null, null, null),
                    "b", new OdgovorPolja(null, List.of("1011 0110"), null, null),
                    "c", new OdgovorPolja(null, null, 2L, null)));
    static final VarijantaSnimak B = new VarijantaSnimak(2L, "B", 3L, "svg-B", "res-B", List.of(BROJ),
            Map.of("a", new OdgovorPolja(13.0, null, null, null)));
    static final ZadatakSnimak S = new ZadatakSnimak(7L, 3L, "K", List.of(), null, List.of(A, B));

    static final Map<Long, Long> DODELE = Map.of(10L, 1L, 11L, 1L, 12L, 1L, 20L, 2L, 21L, 2L);

    static OdgovorZadatka a(long ucesnik, String broj, String tekst, long opcija, boolean tacno) {
        Map<String, PoljeOdgovora> m = new LinkedHashMap<>();
        m.put("a", new PoljeOdgovora(broj, true));
        m.put("b", new PoljeOdgovora(tekst, true));
        m.put("c", new PoljeOdgovora(List.of(opcija), opcija == 2));
        return new OdgovorZadatka(ucesnik, 1L, m, tacno);
    }

    static final List<OdgovorZadatka> ODGOVORI = List.of(
            a(10, "45", "1011 0110", 2, true), a(11, "45,0", "10110110", 2, true), a(12, "44", "1111", 1, false),
            new OdgovorZadatka(20L, 2L, Map.of("a", new PoljeOdgovora("13", true)), true));

    @Test
    void vidljivoSve() {
        ZadatakRezultat r = ZadatakRezultatBuilder.izgradi(S, DODELE, ODGOVORI, true, true, true);
        assertTrue(r.odgovoriPrikazani());
        assertEquals(4, r.ukupno());
        RezultatVarijante va = r.varijante().getFirst();
        assertEquals(3, va.dodeljeno());
        assertEquals(3, va.odgovorilo());
        assertEquals(2, va.tacnih());
        assertEquals("res-A", va.resenjeSvgId());
        List<RezultatTekst> brojevi = va.polja().getFirst().vrednosti();
        assertEquals(2, brojevi.size());
        assertEquals("45", brojevi.getFirst().tekst());
        assertEquals(2, brojevi.getFirst().broj());
        assertEquals(Boolean.TRUE, brojevi.getFirst().tacan());
        assertEquals("44", brojevi.get(1).tekst());
        assertEquals(Boolean.FALSE, brojevi.get(1).tacan());
        List<RezultatTekst> tekstovi = va.polja().get(1).vrednosti();
        assertEquals(2, tekstovi.getFirst().broj(), "1011 0110 i 10110110 su ista grupa");
        List<RezultatOpcija> opcije = va.polja().get(2).opcije();
        assertEquals(List.of(1, 2), opcije.stream().map(RezultatOpcija::broj).toList());
        assertEquals(List.of(false, true), opcije.stream().map(RezultatOpcija::tacna).toList());
        RezultatVarijante vb = r.varijante().get(1);
        assertEquals(2, vb.dodeljeno());
        assertEquals(1, vb.odgovorilo());
        assertEquals(1, vb.tacnih());
    }

    @Test
    void nevidljivo() {
        ZadatakRezultat r = ZadatakRezultatBuilder.izgradi(S, DODELE, ODGOVORI, true, false, false);
        RezultatVarijante va = r.varijante().getFirst();
        assertNull(va.tacnih());
        assertNull(va.resenjeSvgId());
        assertNull(va.polja().getFirst().tacno());
        assertNull(va.polja().getFirst().vrednosti().getFirst().tacan());
        assertNull(va.polja().get(2).opcije().getFirst().tacna());

        r = ZadatakRezultatBuilder.izgradi(S, DODELE, ODGOVORI, false, false, true);
        assertFalse(r.odgovoriPrikazani());
        assertEquals(0, r.ukupno());
        va = r.varijante().getFirst();
        assertEquals(0, va.dodeljeno());
        assertEquals(0, va.odgovorilo());
        assertNull(va.tacnih());
        assertNull(va.polja().getFirst().vrednosti());
        assertEquals(List.of(0, 0), va.polja().get(2).opcije().stream().map(RezultatOpcija::broj).toList());
        assertEquals("jedan", va.polja().get(2).opcije().getFirst().tekst(), "tekst opcija ostaje");
        assertEquals("res-A", va.resenjeSvgId());
    }

    @Test
    void rezultatPoSnimkuPosleIzmeneSeme() {
        // polje b postoji samo u snimku (grupa ga je u međuvremenu izgubila): rezultat ide po snimku
        ZadatakRezultat r = ZadatakRezultatBuilder.izgradi(S, DODELE, ODGOVORI, true, true, false);
        assertEquals(List.of("a", "b", "c"), r.varijante().getFirst().polja().stream().map(RezultatPolja::kljuc).toList());
    }
}
