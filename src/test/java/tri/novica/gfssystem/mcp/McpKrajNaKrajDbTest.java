package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.VarijantaService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * MCP s kraja na kraj kroz pravi {@code /mcp} (spec MCP 9, "MCP end-to-end"): {@code initialize} sa UTF-8 uputstvima,
 * {@code tools/list} tačno po tabeli {@link McpAlatiTest#OCEKIVANO}, pa Klodov tok jedne serije: {@code kontekst} ->
 * {@code napravi_grupu} -> {@code napravi_varijantu} (dve, Novica jednu odobri) -> {@code izmeni_varijantu} nad Novicinom
 * varijantom (kopija, original netaknut) -> {@code ponisti_seriju} (prvo spisak, pa potvrda) -> {@code pretraga_banke} po
 * seriji ima samo preskočenu (odobrenu) varijantu.
 */
class McpKrajNaKrajDbTest extends McpDbTestOsnova {

    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    static final Map<String, Object> ODGOVORI = Map.of("a", Map.of("broj", 11));
    static final String TEKST = "Pretvori $1011_{(2)}$ u dekadni.";
    static final String RESENJE = "Rešenje: $11$.";
    static final String LINK = "http://localhost:4200";

    @Autowired GrupaZadatakaService grupaService;
    @Autowired VarijantaService varijantaService;

    Predmet predmet;
    String serija;
    /** Novicin list PREDAVANJE sa jednom odobrenom (AKTIVNOM) Novicinom varijantom {@link #rucna}. */
    GrupaDetails rucniList;
    Long rucna;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(anyList(), anyString(), anyList()))
                .thenAnswer(inv -> VarijanteAlatiDbTest.odgovor(inv.getArgument(0), inv.getArgument(1)));
        when(latexRender.renderuj(anyString(), anyString(), anyList())).thenReturn(
                VarijanteAlatiDbTest.odgovor(List.of("svg"), ""));
        predmet = noviPredmet("Kraj na kraj MCP test");
        serija = "kraj-" + UUID.randomUUID().toString().substring(0, 8);
        GrupaDetails koren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "Brojni sistemi",
                null, null, null, null));
        rucniList = grupaService.kreiraj(new GrupaCmd(null, koren.id(), null, "2 → 10", null, 2, null, List.of(BROJ_A)));
        rucna = varijantaService.kreiraj(rucniList.id(), new VarijantaCmd("V1", TEKST, RESENJE, ODGOVOR_A, List.of(), null))
                .id();
        varijantaService.odobri(rucna);
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** JSON iz prvog tekstualnog bloka uspešnog poziva. */
    JsonNode uspeh(String alat, Map<String, Object> argumenti) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertFalse(McpTestKlijent.greska(r), alat + ": " + r);
        return McpTestKlijent.json(r);
    }

    Map<String, Object> red(Long varijantaId) {
        return jdbc.queryForMap("select tekst, status, izvor, serija, zamenjuje_id from varijante where id = ?", varijantaId);
    }

    boolean postoji(String tabela, Long id) {
        Integer n = jdbc.queryForObject("select count(*) from " + tabela + " where id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /** {@code vrsta:id} stavki poništavanja, redom. */
    static List<String> stavke(JsonNode niz) {
        return niz.valueStream().map(s -> s.path("vrsta").asString() + ":" + s.path("id").asLong()).toList();
    }

    @Test
    void initializeIAlatiPoTabeli() {
        JsonNode init = mcp.initialize();
        assertEquals("gfs", init.path("serverInfo").path("name").asString());
        assertTrue(init.path("capabilities").has("tools"), init.toString());
        String uputstva = init.path("instructions").asString();
        assertTrue(uputstva.contains("č"), uputstva);
        assertTrue(uputstva.length() <= 4_000, "uputstva: " + uputstva.length());

        List<JsonNode> alati = mcp.alati();
        Set<String> imena = new TreeSet<>();
        alati.forEach(a -> imena.add(a.path("name").asString()));
        assertEquals(28, alati.size());
        assertEquals(new TreeSet<>(McpAlatiTest.OCEKIVANO.keySet()), imena);
    }

    @Test
    void tokSerijeOdKontekstaDoPonistavanja() {
        // kontekst: okruženje i predmet sa Novicinim korenom
        JsonNode kontekst = uspeh("kontekst", Map.of());
        assertEquals("TEST", kontekst.path("okruzenje").asString());
        JsonNode p = kontekst.path("predmeti").valueStream().filter(x -> x.path("id").asLong() == predmet.getId())
                .findFirst().orElseThrow(() -> new AssertionError("predmet nije u kontekstu: " + kontekst));
        assertEquals(1, p.path("koreni").size());
        assertEquals(1, p.path("koreni").get(0).path("brojAktivnih").asInt());

        // napravi_grupu: Klodov list iz serije
        JsonNode upisGrupe = uspeh("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(),
                "namena", "PREDAVANJE", "naziv", "Heksadekadni", "tezina", 2,
                "polja", List.of(mapa("kljuc", "a", "oznaka", "a) heksadekadni u dekadni", "tip", "BROJ"))));
        JsonNode grupa = upisGrupe.path("grupa");
        Long grupaId = grupa.path("id").asLong();
        assertEquals("CLAUDE", grupa.path("izvor").asString());
        assertEquals(serija, grupa.path("serija").asString());
        assertEquals(LINK + "/banka/grupe/" + grupaId, upisGrupe.path("link").asString());

        // napravi_varijantu: dva Klodova nacrta; Novica jedan odobri u UI-ju
        JsonNode prva = uspeh("napravi_varijantu", mapa("serija", serija, "grupaId", grupaId, "naziv", "Zbirka 1.1",
                "tekst", "Pretvori $1A_{(16)}$ u dekadni.", "resenje", "Rešenje: $26$.", "odgovori",
                Map.of("a", Map.of("broj", 26))));
        assertEquals("nova", prva.path("radnja").asString());
        assertEquals(LINK + "/banka/pregled?serija=" + serija, prva.path("linkPregleda").asString());
        Long nacrt = prva.path("varijanta").path("id").asLong();
        assertEquals("NACRT", prva.path("varijanta").path("status").asString());
        assertEquals("CLAUDE", prva.path("varijanta").path("izvor").asString());
        Long odobrena = uspeh("napravi_varijantu", mapa("serija", serija, "grupaId", grupaId, "naziv", "Zbirka 1.2",
                "tekst", "Pretvori $FF_{(16)}$ u dekadni.", "odgovori", Map.of("a", Map.of("broj", 255))))
                .path("varijanta").path("id").asLong();
        varijantaService.odobri(odobrena);

        // izmeni_varijantu nad Novicinom odobrenom varijantom: kopija, original netaknut i aktivan
        JsonNode izmena = uspeh("izmeni_varijantu", mapa("serija", serija, "varijantaId", rucna,
                "tekst", "Pretvori $1100_{(2)}$ u dekadni.", "odgovori", Map.of("a", Map.of("broj", 12))));
        assertEquals("kopija", izmena.path("radnja").asString());
        Long kopija = izmena.path("varijanta").path("id").asLong();
        assertNotEquals(rucna, kopija);
        assertEquals(rucna, izmena.path("varijanta").path("zamenjuje").asLong());
        assertEquals(LINK + "/banka/varijante/" + rucna, izmena.path("linkOriginala").asString());
        assertEquals(serija, red(kopija).get("serija"));
        assertEquals("AKTIVNA", red(rucna).get("status"));
        assertEquals(TEKST, red(rucna).get("tekst"));

        // pretraga po seriji pre poništavanja: oba nacrta, odobrena i kopija
        JsonNode pre = uspeh("pretraga_banke", mapa("predmetId", predmet.getId(), "serija", serija));
        assertEquals(3, pre.path("ukupno").asLong(), pre.toString());

        // ponisti_seriju: prvo spisak bez brisanja, pa potvrda
        JsonNode spisak = uspeh("ponisti_seriju", mapa("serija", serija, "potvrdi", false));
        assertFalse(spisak.path("potvrdjeno").asBoolean(true));
        Set<String> obrisati = new TreeSet<>(stavke(spisak.path("obrisano")));
        assertEquals(new TreeSet<>(List.of("VARIJANTA:" + nacrt, "VARIJANTA:" + kopija)), obrisati);
        assertEquals(List.of("VARIJANTA:" + odobrena, "GRUPA:" + grupaId), stavke(spisak.path("preskoceno")));
        assertTrue(postoji("varijante", nacrt));
        assertTrue(postoji("varijante", kopija));

        JsonNode ishod = uspeh("ponisti_seriju", mapa("serija", serija, "potvrdi", true));
        assertTrue(ishod.path("potvrdjeno").asBoolean());
        assertEquals(obrisati, new TreeSet<>(stavke(ishod.path("obrisano"))));
        assertEquals(stavke(spisak.path("preskoceno")), stavke(ishod.path("preskoceno")));
        assertEquals(LINK + "/banka/pregled?serija=" + serija, ishod.path("linkPregleda").asString());
        assertFalse(postoji("varijante", nacrt));
        assertFalse(postoji("varijante", kopija));
        assertTrue(postoji("varijante", odobrena));
        assertTrue(postoji("grupe_zadataka", grupaId), "grupa sa odobrenom varijantom ostaje");
        assertEquals("AKTIVNA", red(rucna).get("status"), "Novicina varijanta je netaknuta");
        assertEquals(TEKST, red(rucna).get("tekst"));

        // pretraga po seriji posle: samo preskočena (odobrena) varijanta
        JsonNode posle = uspeh("pretraga_banke", mapa("predmetId", predmet.getId(), "serija", serija));
        assertEquals(1, posle.path("ukupno").asLong(), posle.toString());
        JsonNode stavka = posle.path("stavke").get(0);
        assertEquals(odobrena, stavka.path("varijantaId").asLong());
        assertEquals("AKTIVNA", stavka.path("status").asString());
        assertEquals("CLAUDE", stavka.path("izvor").asString());
        assertEquals(serija, stavka.path("serija").asString());
    }
}
