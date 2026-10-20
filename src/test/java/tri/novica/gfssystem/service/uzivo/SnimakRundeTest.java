package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Snimak runde kao unija: {@code vrsta} bira PITANJE ili ZADATAK, a stari snimak bez {@code vrsta} je PITANJE. */
class SnimakRundeTest {

    final JsonMapper mapper = JsonMapper.builder().build();

    /** Snimak PITANJE runde kako ga je upisivala verzija pre unije (bez {@code vrsta}), za {@link PitanjeSnimakTest#slajd()}. */
    static final String STARI_PITANJE_JSON = """
            {"pitanjeId":5,"slajdId":9,"tip":"JEDAN_TACAN","tekst":"Koliko?","slikaId":"uuid-1","vremeSekunde":20,\
            "opcije":[{"id":1,"rb":1,"tekst":"A","tacna":true},{"id":2,"rb":2,"tekst":"B","tacna":false}],\
            "brojTacno":null,"brojOdstupanje":null,"odstupanjeTip":null,"jedinica":null,"tekstPrikaz":null,\
            "prihvatljiviOdgovori":null,"skalaMinOznaka":null,"skalaMaxOznaka":null}""";

    static VarijantaSnimak varijanta(long id, String naziv, String resenjeSvgId, List<Polje> polja,
                                     Map<String, OdgovorPolja> odgovori) {
        return new VarijantaSnimak(id, naziv, 3L, "svg-" + id, resenjeSvgId, polja, odgovori);
    }

    static ZadatakSnimak zadatak(List<VarijantaSnimak> varijante) {
        return new ZadatakSnimak(7L, 3L, "2 → 10", List.of(new GrupaPutanja(1L, "Konverzije"), new GrupaPutanja(3L, "2 → 10")),
                120, varijante);
    }

    static ZadatakSnimak dveVarijante() {
        Polje broj = new Polje("a", "a) dekadno", TipPolja.BROJ, 0.0, OdstupanjeTip.APSOLUTNO, null, null, null, null);
        Polje izbor = new Polje("c", "c) koliko cifara", TipPolja.JEDAN_TACAN, null, null, null, null, null,
                List.of(new PoljeOpcija(1L, "$3$"), new PoljeOpcija(2L, "$4$")));
        return zadatak(List.of(
                varijanta(12, "V1", "resenje-12", List.of(broj, izbor),
                        Map.of("a", new OdgovorPolja(45.0, null, null, null), "c", new OdgovorPolja(null, null, 2L, null))),
                varijanta(13, "V2", null, List.of(broj),
                        Map.of("a", new OdgovorPolja(11.0, null, null, null)))));
    }

    @Test void stariSnimakBezVrsteJePitanje() {
        SnimakRunde s = mapper.readValue(STARI_PITANJE_JSON, SnimakRunde.class);
        PitanjeSnimak p = assertInstanceOf(PitanjeSnimak.class, s);
        assertEquals(PitanjeSnimak.od(PitanjeSnimakTest.slajd()), p);
        assertEquals(9L, s.slajdId());
        assertTrue(s.imaTacanOdgovor());
    }

    @Test void upisanoPitanjeNosiVrstu() {
        PitanjeSnimak p = PitanjeSnimak.od(PitanjeSnimakTest.slajd());
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("\"vrsta\":\"PITANJE\""), json);
        assertFalse(json.contains("imaTacanOdgovor"));
        assertEquals(p, mapper.readValue(json, SnimakRunde.class));
        // čitaoci koji traže baš PitanjeSnimak (stari testovi) i dalje rade
        assertEquals(p, mapper.readValue(json, PitanjeSnimak.class));
    }

    @Test void zadatakSaDveVarijanteKrug() {
        ZadatakSnimak z = dveVarijante();
        String json = mapper.writeValueAsString(z);
        assertTrue(json.contains("\"vrsta\":\"ZADATAK\""), json);
        assertFalse(json.contains("imaTacanOdgovor"));
        assertFalse(json.contains("imaResenje"));
        SnimakRunde procitan = mapper.readValue(json, SnimakRunde.class);
        assertEquals(z, assertInstanceOf(ZadatakSnimak.class, procitan));
        assertEquals(7L, procitan.slajdId());
        assertTrue(procitan.imaTacanOdgovor());
        // formule u opcijama polja ostaju doslovno
        assertTrue(json.contains("$4$"));
    }

    @Test void zadatakPomocneMetode() {
        ZadatakSnimak z = dveVarijante();
        assertTrue(z.imaResenje());
        assertEquals("V2", z.varijanta(13L).orElseThrow().naziv());
        assertTrue(z.varijanta(99L).isEmpty());
        assertTrue(z.varijanta(null).isEmpty());

        ZadatakSnimak bezResenja = zadatak(List.of(varijanta(13, "V2", null, List.of(), Map.of())));
        assertFalse(bezResenja.imaResenje());
    }

    @Test void radNaPapiruNemaTacanOdgovor() {
        ZadatakSnimak papir = zadatak(List.of(varijanta(12, "V1", "resenje-12", List.of(), Map.of()),
                varijanta(13, "V2", null, null, null)));
        assertFalse(papir.imaTacanOdgovor());
        assertFalse(zadatak(List.of()).imaTacanOdgovor());
        assertFalse(zadatak(null).imaTacanOdgovor());
    }
}
