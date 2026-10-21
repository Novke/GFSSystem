package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.mcp.servis.VodicTema;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.IskljuceneTemeService;
import tri.novica.gfssystem.service.banka.VarijantaService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Alati konteksta i vodiča kroz pravi {@code /mcp} (spec MCP 4.2): {@code kontekst} (okruženje, link UI-ja, predmeti sa
 * korenima banke obe namene i brojačima, broj prezentacija, isključene teme, sopstvena preambula, poslednje serije),
 * {@code vodic} po temi (nepoznata tema je greška), {@code preambula} (samo čitanje) i {@code domaci_tekstovi} (20 po strani,
 * samo id, naslov, tekst i datum).
 */
class KontekstAlatiDbTest extends McpDbTestOsnova {

    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>"
            .getBytes(StandardCharsets.UTF_8);
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));

    @Autowired GrupaZadatakaService grupaService;
    @Autowired VarijantaService varijantaService;
    @Autowired IskljuceneTemeService temeService;
    @Autowired PrezentacijaService prezentacijaService;

    Predmet predmet;
    String serija;
    GrupaDetails predavanjeKoren;
    GrupaDetails list;
    GrupaDetails testKoren;
    IskljucenaTemaInfo tema;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 7L, false));
        predmet = noviPredmet("Kontekst MCP test");
        serija = "kontekst-" + UUID.randomUUID().toString().substring(0, 8);

        // PREDAVANJE: koren -> list sa AKTIVNOM, Klodovim nacrtom i nacrtom sa greškom rendera
        predavanjeKoren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "Brojni sistemi",
                null, null, null, null));
        list = grupaService.kreiraj(new GrupaCmd(null, predavanjeKoren.id(), null, "2 → 10", null, 2, null, List.of(BROJ_A)));
        Long aktivna = varijantaService.kreiraj(list.id(), cmd("V1", null)).id();
        varijantaService.odobri(aktivna);
        varijantaService.kreiraj(list.id(), cmd("V2", IzvorVarijante.CLAUDE));
        Long greska = varijantaService.kreiraj(list.id(), cmd("V3", null)).id();
        jdbc.update("update varijante set render_status = 'GRESKA' where id = ?", greska);

        // TEST: Klodov koren (list) iz serije sa jednim nacrtom
        testKoren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum 1", null, 3, null,
                List.of(BROJ_A)), IzvorVarijante.CLAUDE, serija);
        varijantaService.kreiraj(testKoren.id(), cmd("K1", IzvorVarijante.CLAUDE), serija, false);

        tema = temeService.dodaj(predmet.getId(), new IskljucenaTemaCmd("Razlomljeni brojevi 10 → 2", list.id()),
                IzvorVarijante.RUCNO, null);
        prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Vežbe 1", null, null));
    }

    static VarijantaCmd cmd(String naziv, IzvorVarijante izvor) {
        return new VarijantaCmd(naziv, "Pretvori $1011_{(2)}$ u dekadni.", "$11$", ODGOVOR_A, List.of(), izvor);
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonNode uspeh(String alat, Map<String, Object> argumenti) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertFalse(McpTestKlijent.greska(r), r.toString());
        return r;
    }

    void ocekujGresku(String alat, Map<String, Object> argumenti, String poruka) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertTrue(McpTestKlijent.greska(r), r.toString());
        assertEquals(poruka, McpTestKlijent.tekst(r));
    }

    JsonNode predmetIzKonteksta(JsonNode kontekst, Long id) {
        for (JsonNode p : kontekst.path("predmeti")) {
            if (p.path("id").asLong() == id) {
                return p;
            }
        }
        throw new AssertionError("Nema predmeta " + id + " u kontekstu");
    }

    @Test
    void kontekstVracaOkruzenjeLinkIPredmetSaKorenimaObeNamene() {
        JsonNode k = McpTestKlijent.json(uspeh("kontekst", Map.of()));
        assertEquals("TEST", k.path("okruzenje").asString());
        assertEquals("http://localhost:4200", k.path("linkUi").asString());

        JsonNode p = predmetIzKonteksta(k, predmet.getId());
        assertEquals("Kontekst MCP test", p.path("naziv").asString());
        assertEquals(1, p.path("brojPrezentacija").asLong());
        assertFalse(p.path("imaPreambulu").asBoolean(true));

        JsonNode koreni = p.path("koreni");
        assertEquals(2, koreni.size(), koreni.toString());
        JsonNode predavanje = koreni.get(0);
        assertEquals(predavanjeKoren.id(), predavanje.path("grupaId").asLong());
        assertEquals("Brojni sistemi", predavanje.path("naziv").asString());
        assertEquals("PREDAVANJE", predavanje.path("namena").asString());
        assertEquals(3, predavanje.path("brojVarijanti").asInt());
        assertEquals(1, predavanje.path("brojAktivnih").asInt());
        assertEquals(2, predavanje.path("brojNacrta").asInt());
        assertEquals(1, predavanje.path("brojClaudeNacrta").asInt());
        assertEquals(1, predavanje.path("brojGresaka").asInt());
        JsonNode test = koreni.get(1);
        assertEquals(testKoren.id(), test.path("grupaId").asLong());
        assertEquals("TEST", test.path("namena").asString());
        assertEquals(1, test.path("brojVarijanti").asInt());
        assertEquals(0, test.path("brojAktivnih").asInt());
        assertEquals(1, test.path("brojNacrta").asInt());
        assertEquals(1, test.path("brojClaudeNacrta").asInt());
        assertEquals(0, test.path("brojGresaka").asInt());

        JsonNode teme = p.path("iskljuceneTeme");
        assertEquals(1, teme.size(), teme.toString());
        assertEquals(tema.id(), teme.get(0).path("id").asLong());
        assertEquals("Razlomljeni brojevi 10 → 2", teme.get(0).path("opis").asString());
        assertEquals(list.id(), teme.get(0).path("grupaId").asLong());

        JsonNode serije = k.path("poslednjeSerije");
        assertTrue(serije.size() <= 5, serije.toString());
        JsonNode nasa = null;
        for (JsonNode s : serije) {
            if (serija.equals(s.path("serija").asString())) {
                nasa = s;
            }
        }
        assertNotNull(nasa, "serija iz testa nije među poslednjim: " + serije);
        assertEquals(1, nasa.path("grupa").asLong());
        assertEquals(1, nasa.path("nacrta").asLong());
        assertFalse(McpTestKlijent.tekst(mcp.pozovi("kontekst", Map.of())).contains("svgId"));
    }

    @Test
    void kontekstZnaZaSopstvenuPreambulu() {
        Predmet drugi = noviPredmet("Kontekst MCP test (preambula)");
        jdbc.update("update predmeti set latex_preambula = ? where id = ?", "\\usepackage{amsmath}", drugi.getId());
        JsonNode p = predmetIzKonteksta(McpTestKlijent.json(uspeh("kontekst", Map.of())), drugi.getId());
        assertTrue(p.path("imaPreambulu").asBoolean());
        assertEquals(0, p.path("koreni").size());
        assertEquals(0, p.path("brojPrezentacija").asLong());

        JsonNode preambula = McpTestKlijent.json(uspeh("preambula", mapa("predmetId", drugi.getId())));
        assertFalse(preambula.path("podrazumevana").asBoolean(true));
        assertEquals("\\usepackage{amsmath}", preambula.path("preambula").asString());
    }

    @Test
    void vodicPoTemi() {
        for (VodicTema t : VodicTema.values()) {
            JsonNode r = uspeh("vodic", mapa("tema", t.name().toLowerCase(Locale.ROOT)));
            assertEquals(t.ucitaj(), McpTestKlijent.tekst(r), t.name());
            assertEquals(1, r.path("content").size(), "vodič je jedan tekstualni blok");
        }
        assertTrue(McpTestKlijent.tekst(uspeh("vodic", mapa("tema", "banka"))).contains("standalone"));
        assertTrue(McpTestKlijent.tekst(uspeh("vodic", mapa("tema", "pitanja"))).contains("TACNO_NETACNO"));
        assertTrue(McpTestKlijent.tekst(uspeh("vodic", mapa("tema", "pravila"))).contains("ponisti_seriju"));
    }

    @Test
    void nepoznataTemaVodica() {
        ocekujGresku("vodic", mapa("tema", "platno"), "Nepoznata tema vodiča.");
        ocekujGresku("vodic", mapa("tema", "BANKA"), "Nepoznata tema vodiča.");
        ocekujGresku("vodic", Map.of(), "Nepoznata tema vodiča.");
    }

    @Test
    void preambulaPodrazumevana() {
        JsonNode p = McpTestKlijent.json(uspeh("preambula", mapa("predmetId", predmet.getId())));
        assertTrue(p.path("podrazumevana").asBoolean());
        assertTrue(p.path("preambula").isNull(), p.toString());
        assertTrue(p.path("podrazumevanaPreambula").asString().contains("\\usepackage{tikz}"));
    }

    @Test
    void preambulaNepostojecegIBezPredmeta() {
        ocekujGresku("preambula", mapa("predmetId", Long.MAX_VALUE), "Predmet nije pronađen.");
        ocekujGresku("preambula", Map.of(), "Predmet je obavezan.");
    }

    @Test
    void domaciTekstoviPoStranama() {
        for (int i = 1; i <= 21; i++) {
            jdbc.update("insert into domaci (datum, text, predmet_id, naslov, pregledan) values (?, ?, ?, ?, ?)",
                    LocalDate.of(2026, 9, 1).plusDays(i), "Zadatak " + i + ": pretvori " + i + " u binarni.",
                    predmet.getId(), "Domaći " + i, false);
        }
        JsonNode prva = McpTestKlijent.json(uspeh("domaci_tekstovi", mapa("predmetId", predmet.getId(), "strana", 1)));
        assertEquals(1, prva.path("strana").asInt());
        assertEquals(2, prva.path("brojStrana").asInt());
        assertEquals(21, prva.path("ukupno").asLong());
        JsonNode stavke = prva.path("stavke");
        assertEquals(20, stavke.size());
        JsonNode najnovija = stavke.get(0);
        assertEquals(Set.of("id", "naslov", "text", "datum"), new TreeSet<>(najnovija.propertyNames()));
        assertEquals("Domaći 21", najnovija.path("naslov").asString());
        assertEquals("Zadatak 21: pretvori 21 u binarni.", najnovija.path("text").asString());
        assertEquals("2026-09-22", najnovija.path("datum").asString());

        JsonNode bezStrane = McpTestKlijent.json(uspeh("domaci_tekstovi", mapa("predmetId", predmet.getId())));
        assertEquals(1, bezStrane.path("strana").asInt());
        JsonNode druga = McpTestKlijent.json(uspeh("domaci_tekstovi", mapa("predmetId", predmet.getId(), "strana", 2)));
        assertEquals(1, druga.path("stavke").size());
        assertEquals("Domaći 1", druga.path("stavke").get(0).path("naslov").asString());
    }

    @Test
    void domaciTekstoviGreske() {
        ocekujGresku("domaci_tekstovi", mapa("predmetId", predmet.getId(), "strana", 0), "Neispravan parametar: strana.");
        ocekujGresku("domaci_tekstovi", mapa("predmetId", Long.MAX_VALUE), "Predmet nije pronađen.");
        ocekujGresku("domaci_tekstovi", Map.of(), "Predmet je obavezan.");
    }
}
