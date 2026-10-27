package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.IskljuceneTemeService;
import tri.novica.gfssystem.service.banka.RenderNedostupan;
import tri.novica.gfssystem.service.banka.VarijantaService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alati banke za čitanje i isključene teme kroz pravi {@code /mcp} (spec MCP 4.3, 6.2): {@code stablo_banke} (izvor,
 * Klodovi nacrti, isključenost), {@code grupa} (sažetak varijanti sa {@code zamenjuje}, link), {@code varijanta} (bez id-jeva
 * SVG-a; PNG po nameni: PREDAVANJE zadatak i rešenje, TEST samo zadatak osim uz {@code slikaResenja}; render koji ne radi
 * je uspeh sa napomenom), {@code pretraga_banke} (izvor i serija bez {@code q}, 25 po strani), {@code iskljucene_teme},
 * {@code iskljuci_temu} (CLAUDE i serija, ponovljen poziv je 409 bez drugog reda, jedno pravljenje iz budžeta) i
 * {@code ukljuci_temu} (traži potvrdu).
 */
class BankaAlatiCitanjeDbTest extends McpDbTestOsnova {

    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>"
            .getBytes(StandardCharsets.UTF_8);
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    static final String TEKST = "Pretvori $1011_{(2)}$ u dekadni.";
    static final String RESENJE = "Rešenje: $11$.";
    static final String NIJE_STIGLA = "Slika nije stigla na vreme.";
    /** Visina slike zadatka i rešenja u moku, i spojene slike (razmak, linija, razmak između njih). */
    static final int VISINA_ZADATKA = 40;
    static final int VISINA_RESENJA = 60;
    static final int VISINA_SPOJENE = VISINA_ZADATKA + 16 + 2 + 16 + VISINA_RESENJA;

    @Autowired GrupaZadatakaService grupaService;
    @Autowired VarijantaService varijantaService;
    @Autowired IskljuceneTemeService temeService;
    @MockitoSpyBean McpBudzet budzet;

    Predmet predmet;
    String serija;
    GrupaDetails predavanjeKoren;
    GrupaDetails list;
    GrupaDetails testKoren;
    Long aktivna;
    Long claudeNacrt;
    Long kopija;
    Long testVarijanta;
    IskljucenaTemaInfo tema;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, b64(SVG), null, null, null, 7L, false));
        when(latexRender.renderuj(eq(List.of("png")), contains("Pretvori"), anyList()))
                .thenReturn(png(png(100, VISINA_ZADATKA)));
        when(latexRender.renderuj(eq(List.of("png")), contains("Rešenje:"), anyList()))
                .thenReturn(png(png(100, VISINA_RESENJA)));
        predmet = noviPredmet("Banka čitanje MCP test");
        serija = "citanje-" + UUID.randomUUID().toString().substring(0, 8);

        // PREDAVANJE (Novicino): koren -> list sa AKTIVNOM, Klodovim nacrtom iz serije i Klodovom kopijom AKTIVNE
        predavanjeKoren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "Brojni sistemi",
                null, null, null, null));
        list = grupaService.kreiraj(new GrupaCmd(null, predavanjeKoren.id(), null, "2 → 10", null, 2, null, List.of(BROJ_A)));
        aktivna = varijantaService.kreiraj(list.id(), cmd("V1", null)).id();
        varijantaService.odobri(aktivna);
        claudeNacrt = varijantaService.kreiraj(list.id(), cmd("V2", IzvorVarijante.CLAUDE), serija, false).varijanta().id();
        kopija = varijantaService.kreiraj(list.id(), cmd("V1 (kopija)", IzvorVarijante.CLAUDE)).id();
        jdbc.update("update varijante set zamenjuje_id = ? where id = ?", aktivna, kopija);

        // TEST (Klodov koren iz serije, list) sa jednim nacrtom koji ima rešenje
        testKoren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum 1", null, 3, null,
                List.of(BROJ_A)), IzvorVarijante.CLAUDE, serija);
        testVarijanta = varijantaService.kreiraj(testKoren.id(), cmd("K1", IzvorVarijante.CLAUDE), serija, false)
                .varijanta().id();

        tema = temeService.dodaj(predmet.getId(), new IskljucenaTemaCmd("Razlomljeni brojevi 10 → 2", list.id()),
                IzvorVarijante.RUCNO, null);
    }

    static VarijantaCmd cmd(String naziv, IzvorVarijante izvor) {
        return new VarijantaCmd(naziv, TEKST, RESENJE, ODGOVOR_A, List.of(), izvor);
    }

    static String b64(byte[] bajtovi) {
        return Base64.getEncoder().encodeToString(bajtovi);
    }

    static RenderOdgovor png(byte[] png) {
        return new RenderOdgovor(true, null, null, null, null, 5L, false, b64(png));
    }

    static byte[] png(int sirina, int visina) {
        BufferedImage slika = new BufferedImage(sirina, visina, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream izlaz = new ByteArrayOutputStream();
        try {
            assertTrue(ImageIO.write(slika, "png", izlaz));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return izlaz.toByteArray();
    }

    /** Visina jedine slike rezultata. */
    static int visinaSlike(JsonNode rezultat) {
        List<JsonNode> slike = McpTestKlijent.slike(rezultat);
        assertEquals(1, slike.size(), rezultat.toString());
        assertEquals("image/png", slike.get(0).path("mimeType").asString());
        try {
            BufferedImage slika = ImageIO.read(new ByteArrayInputStream(
                    Base64.getDecoder().decode(slike.get(0).path("data").asString())));
            assertNotNull(slika);
            return slika.getHeight();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Tekstualni blokovi rezultata, redom. */
    static List<String> tekstovi(JsonNode rezultat) {
        List<String> tekstovi = new ArrayList<>();
        for (JsonNode blok : rezultat.path("content")) {
            if ("text".equals(blok.path("type").asString())) {
                tekstovi.add(blok.path("text").asString());
            }
        }
        return tekstovi;
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

    JsonNode alat(String ime) {
        return mcp.alati().stream().filter(a -> ime.equals(a.path("name").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("Nema alata " + ime));
    }

    static Set<Long> ids(JsonNode niz, String polje) {
        Set<Long> ids = new TreeSet<>();
        niz.forEach(n -> ids.add(n.path(polje).asLong()));
        return ids;
    }

    int brojTemaBezGrupe() {
        Integer n = jdbc.queryForObject("select count(*) from iskljucene_teme where predmet_id = ? and grupa_id is null",
                Integer.class, predmet.getId());
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- stablo i grupa

    @Test
    void stabloImaIzvorKlodoveNacrteIIskljucenost() {
        JsonNode stablo = McpTestKlijent.json(uspeh("stablo_banke",
                mapa("predmetId", predmet.getId(), "namena", "PREDAVANJE")));
        assertEquals(1, stablo.size(), stablo.toString());
        JsonNode koren = stablo.get(0);
        assertEquals(predavanjeKoren.id(), koren.path("id").asLong());
        assertEquals("RUCNO", koren.path("izvor").asString());
        assertEquals(2, koren.path("brojClaudeNacrta").asInt());
        assertFalse(koren.path("iskljucena").asBoolean(true));
        assertTrue(koren.path("iskljucenjeId").isNull());
        JsonNode cvor = koren.path("deca").get(0);
        assertEquals(list.id(), cvor.path("id").asLong());
        assertEquals("RUCNO", cvor.path("izvor").asString());
        assertEquals(3, cvor.path("brojVarijanti").asInt());
        assertEquals(2, cvor.path("brojClaudeNacrta").asInt());
        assertTrue(cvor.path("iskljucena").asBoolean());
        assertEquals(tema.id(), cvor.path("iskljucenjeId").asLong());

        JsonNode test = McpTestKlijent.json(uspeh("stablo_banke", mapa("predmetId", predmet.getId(), "namena", "TEST")));
        assertEquals(1, test.size(), test.toString());
        assertEquals("CLAUDE", test.get(0).path("izvor").asString());
        assertEquals(1, test.get(0).path("brojClaudeNacrta").asInt());

        ocekujGresku("stablo_banke", mapa("predmetId", predmet.getId()), "Namena je obavezna.");
        ocekujGresku("stablo_banke", mapa("namena", "TEST"), "Predmet je obavezan.");
        ocekujGresku("stablo_banke", mapa("predmetId", predmet.getId(), "namena", "ISPIT"), "Neispravan format podataka.");
    }

    @Test
    void grupaImaSazetakVarijantiSaZamenjujeILink() {
        JsonNode r = uspeh("grupa", mapa("grupaId", list.id()));
        assertFalse(McpTestKlijent.tekst(r).contains("svgId"), McpTestKlijent.tekst(r));
        JsonNode g = McpTestKlijent.json(r);
        assertEquals(Set.of("grupa", "varijante", "link"), new TreeSet<>(g.propertyNames()));
        assertEquals("http://localhost:4200/banka/grupe/" + list.id(), g.path("link").asString());
        JsonNode grupa = g.path("grupa");
        assertEquals(list.id(), grupa.path("id").asLong());
        assertEquals("RUCNO", grupa.path("izvor").asString());
        assertTrue(grupa.path("iskljucena").asBoolean());
        assertEquals(tema.id(), grupa.path("iskljucenjeId").asLong());
        assertEquals("a", grupa.path("polja").get(0).path("kljuc").asString());
        assertEquals(List.of(predavanjeKoren.id(), list.id()),
                grupa.path("putanja").valueStream().map(p -> p.path("id").asLong()).toList());

        JsonNode varijante = g.path("varijante");
        assertEquals(3, varijante.size(), varijante.toString());
        Map<Long, JsonNode> poId = new HashMap<>();
        varijante.forEach(v -> poId.put(v.path("id").asLong(), v));
        assertEquals(Set.of("id", "naziv", "status", "izvor", "renderStatus", "zamenjuje", "nivo"),
                new TreeSet<>(poId.get(aktivna).propertyNames()));
        assertEquals("AKTIVNA", poId.get(aktivna).path("status").asString());
        assertEquals("RUCNO", poId.get(aktivna).path("izvor").asString());
        assertEquals("OK", poId.get(aktivna).path("renderStatus").asString());
        assertTrue(poId.get(aktivna).path("zamenjuje").isNull());
        assertEquals("NACRT", poId.get(claudeNacrt).path("status").asString());
        assertEquals("CLAUDE", poId.get(claudeNacrt).path("izvor").asString());
        assertEquals(aktivna, poId.get(kopija).path("zamenjuje").asLong());
        assertEquals("V1 (kopija)", poId.get(kopija).path("naziv").asString());

        JsonNode testGrupa = McpTestKlijent.json(uspeh("grupa", mapa("grupaId", testKoren.id()))).path("grupa");
        assertEquals("CLAUDE", testGrupa.path("izvor").asString());
        assertEquals(serija, testGrupa.path("serija").asString());

        ocekujGresku("grupa", mapa("grupaId", Long.MAX_VALUE), "Grupa nije pronađena.");
        ocekujGresku("grupa", Map.of(), "Neispravan parametar: grupaId.");
    }

    // ---------------------------------------------------------------- varijanta

    @Test
    void varijantaBezIdJevaSvgaISlikeKadNijeTrazena() {
        JsonNode r = uspeh("varijanta", mapa("varijantaId", claudeNacrt));
        String tekst = McpTestKlijent.tekst(r);
        assertFalse(tekst.contains("svgId"), tekst);
        assertFalse(tekst.contains("resenjeSvgId"), tekst);
        assertEquals(List.of(), McpTestKlijent.slike(r));
        assertEquals(1, tekstovi(r).size(), "bez slike nema ni napomene");
        verify(latexRender, never()).renderuj(eq(List.of("png")), anyString(), anyList());

        JsonNode v = McpTestKlijent.json(r);
        assertEquals(Set.of("id", "grupaId", "putanja", "namena", "naziv", "tekst", "resenje", "odgovori", "polja",
                "prilozi", "status", "izvor", "ikadOdobrena", "zamenjuje", "serija", "nivo", "renderStatus", "renderGreska",
                "renderDeo", "izmenjeno", "link"), new TreeSet<>(v.propertyNames()));
        assertEquals(claudeNacrt, v.path("id").asLong());
        assertEquals(list.id(), v.path("grupaId").asLong());
        assertEquals(List.of(predavanjeKoren.id(), list.id()),
                v.path("putanja").valueStream().map(p -> p.path("id").asLong()).toList());
        assertEquals("PREDAVANJE", v.path("namena").asString());
        assertEquals("V2", v.path("naziv").asString());
        assertEquals(TEKST, v.path("tekst").asString());
        assertEquals(RESENJE, v.path("resenje").asString());
        assertEquals(11.0, v.path("odgovori").path("a").path("broj").asDouble());
        assertEquals("BROJ", v.path("polja").get(0).path("tip").asString());
        assertEquals(0, v.path("prilozi").size());
        assertEquals("NACRT", v.path("status").asString());
        assertEquals("CLAUDE", v.path("izvor").asString());
        assertFalse(v.path("ikadOdobrena").asBoolean(true));
        assertTrue(v.path("zamenjuje").isNull());
        assertEquals(serija, v.path("serija").asString());
        assertEquals("OK", v.path("renderStatus").asString());
        assertEquals("http://localhost:4200/banka/varijante/" + claudeNacrt, v.path("link").asString());

        JsonNode odobrena = McpTestKlijent.json(uspeh("varijanta", mapa("varijantaId", aktivna)));
        assertTrue(odobrena.path("ikadOdobrena").asBoolean());
        assertEquals("RUCNO", odobrena.path("izvor").asString());
        assertEquals(aktivna, McpTestKlijent.json(uspeh("varijanta", mapa("varijantaId", kopija))).path("zamenjuje").asLong());

        ocekujGresku("varijanta", mapa("varijantaId", Long.MAX_VALUE), "Varijanta nije pronađena.");
        ocekujGresku("varijanta", Map.of(), "Neispravan parametar: varijantaId.");
    }

    @Test
    void varijantaUPredavanjuImaSlikuZadatkaIResenja() {
        JsonNode r = uspeh("varijanta", mapa("varijantaId", claudeNacrt, "slika", true));
        assertEquals(VISINA_SPOJENE, visinaSlike(r));
        assertEquals(1, tekstovi(r).size(), "slika je stigla, nema napomene");
        assertFalse(McpTestKlijent.tekst(r).contains("svgId"));
    }

    @Test
    void varijantaUTestuImaSamoZadatakOsimUzSlikuResenja() {
        JsonNode samoZadatak = uspeh("varijanta", mapa("varijantaId", testVarijanta, "slika", true));
        assertEquals(VISINA_ZADATKA, visinaSlike(samoZadatak));
        assertEquals("TEST", McpTestKlijent.json(samoZadatak).path("namena").asString());

        JsonNode saResenjem = uspeh("varijanta", mapa("varijantaId", testVarijanta, "slika", true, "slikaResenja", true));
        assertTrue(visinaSlike(saResenjem) > VISINA_ZADATKA);
        assertEquals(VISINA_SPOJENE, visinaSlike(saResenjem));

        JsonNode bezSlike = uspeh("varijanta", mapa("varijantaId", testVarijanta, "slikaResenja", true));
        assertEquals(List.of(), McpTestKlijent.slike(bezSlike));
    }

    @Test
    void renderKojiNeRadiJeUspehSaNapomenom() {
        when(latexRender.renderuj(eq(List.of("png")), anyString(), anyList())).thenThrow(new RenderNedostupan());
        JsonNode r = uspeh("varijanta", mapa("varijantaId", claudeNacrt, "slika", true));
        assertEquals(List.of(), McpTestKlijent.slike(r));
        List<String> tekstovi = tekstovi(r);
        assertEquals(2, tekstovi.size(), r.toString());
        assertEquals(claudeNacrt, McpTestKlijent.json(r).path("id").asLong());
        assertEquals(NIJE_STIGLA, tekstovi.get(1));
    }

    @Test
    void slikaKojaSeNeCitaJeUspehSaNapomenom() {
        when(latexRender.renderuj(eq(List.of("png")), anyString(), anyList()))
                .thenReturn(png("nije png".getBytes(StandardCharsets.UTF_8)));
        JsonNode r = uspeh("varijanta", mapa("varijantaId", claudeNacrt, "slika", true));
        assertEquals(List.of(), McpTestKlijent.slike(r));
        assertEquals(List.of(McpTestKlijent.tekst(r), NIJE_STIGLA), tekstovi(r));
    }

    // ---------------------------------------------------------------- pretraga

    @Test
    void pretragaPoIzvoruISerijiBezUpita() {
        JsonNode strana = McpTestKlijent.json(uspeh("pretraga_banke",
                mapa("predmetId", predmet.getId(), "izvor", "CLAUDE", "serija", serija)));
        assertEquals(Set.of(claudeNacrt, testVarijanta), ids(strana.path("stavke"), "varijantaId"));
        assertEquals(2, strana.path("ukupno").asLong());
        assertEquals(1, strana.path("strana").asInt());
        assertEquals(1, strana.path("brojStrana").asInt());
        JsonNode stavka = strana.path("stavke").get(0);
        assertEquals("CLAUDE", stavka.path("izvor").asString());
        assertEquals(serija, stavka.path("serija").asString());
        assertFalse(McpTestKlijent.tekst(mcp.pozovi("pretraga_banke", mapa("predmetId", predmet.getId()))).contains("svgId"));

        JsonNode claude = McpTestKlijent.json(uspeh("pretraga_banke", mapa("predmetId", predmet.getId(), "izvor", "CLAUDE")));
        assertEquals(Set.of(claudeNacrt, kopija, testVarijanta), ids(claude.path("stavke"), "varijantaId"));
        JsonNode test = McpTestKlijent.json(uspeh("pretraga_banke",
                mapa("predmetId", predmet.getId(), "namena", "TEST", "status", "NACRT")));
        assertEquals(Set.of(testVarijanta), ids(test.path("stavke"), "varijantaId"));
        JsonNode upit = McpTestKlijent.json(uspeh("pretraga_banke", mapa("predmetId", predmet.getId(), "q", "kopija")));
        assertEquals(Set.of(kopija), ids(upit.path("stavke"), "varijantaId"));
    }

    @Test
    void pretragaPo25NajnovijePrve() {
        for (int i = 0; i < 24; i++) {
            varijantaService.kreiraj(list.id(), cmd("Dodatna " + i, IzvorVarijante.CLAUDE), serija, false);
        }
        jdbc.update("update varijante set izmenjeno = '2020-01-01 10:00:00' where id = ?", claudeNacrt);
        JsonNode prva = McpTestKlijent.json(uspeh("pretraga_banke",
                mapa("predmetId", predmet.getId(), "serija", serija, "strana", 1)));
        assertEquals(26, prva.path("ukupno").asLong());
        assertEquals(2, prva.path("brojStrana").asInt());
        assertEquals(25, prva.path("stavke").size());
        JsonNode druga = McpTestKlijent.json(uspeh("pretraga_banke",
                mapa("predmetId", predmet.getId(), "serija", serija, "strana", 2)));
        assertEquals(2, druga.path("strana").asInt());
        assertEquals(1, druga.path("stavke").size());
        assertEquals(claudeNacrt, druga.path("stavke").get(0).path("varijantaId").asLong(), "najstarija je poslednja");
        ocekujGresku("pretraga_banke", mapa("predmetId", predmet.getId(), "strana", 0), "Neispravan parametar: strana.");
    }

    @Test
    void pretragaProveravaUpitSerijuIPredmet() {
        ocekujGresku("pretraga_banke", mapa("predmetId", predmet.getId(), "q", "x"), "Upit mora imati bar 2 znaka.");
        ocekujGresku("pretraga_banke", mapa("predmetId", predmet.getId(), "q", " "), "Upit mora imati bar 2 znaka.");
        ocekujGresku("pretraga_banke", mapa("predmetId", predmet.getId(), "serija", "Uvoz-1"),
                "Serija mora imati 3 do 60 malih slova, cifara ili crtica.");
        ocekujGresku("pretraga_banke", mapa("q", "Pretvori"), "Predmet je obavezan.");
    }

    @Test
    void semaPretrageNeTraziUpitNiFiltere() {
        JsonNode sema = alat("pretraga_banke").path("inputSchema");
        Set<String> obavezno = new TreeSet<>();
        sema.path("required").forEach(n -> obavezno.add(n.asString()));
        assertEquals(Set.of("predmetId"), obavezno, sema.toString());
        assertEquals(Set.of("predmetId", "q", "namena", "status", "izvor", "serija", "nivo", "strana"),
                new TreeSet<>(sema.path("properties").propertyNames()));
    }

    // ---------------------------------------------------------------- isključene teme

    @Test
    void iskljuciTemuPaPonovljenPozivJe409BezDrugogReda() {
        Map<String, Object> argumenti = mapa("serija", serija, "predmetId", predmet.getId(),
                "opis", "Razlomljeni (decimalni) brojevi 2 → 10");
        JsonNode t = McpTestKlijent.json(uspeh("iskljuci_temu", argumenti));
        assertEquals("CLAUDE", t.path("izvor").asString());
        assertEquals(serija, t.path("serija").asString());
        assertEquals(predmet.getId(), t.path("predmetId").asLong());
        assertTrue(t.path("grupaId").isNull());
        assertEquals(1, brojTemaBezGrupe());

        ocekujGresku("iskljuci_temu", mapa("serija", serija, "predmetId", predmet.getId(),
                "opis", "  razlomljeni (DECIMALNI)   brojevi 2 → 10 "), "Ova tema je već isključena.");
        assertEquals(1, brojTemaBezGrupe());

        JsonNode saGrupom = McpTestKlijent.json(uspeh("iskljuci_temu", mapa("serija", serija, "predmetId", predmet.getId(),
                "opis", "Kolokvijum 1", "grupaId", testKoren.id())));
        assertEquals(testKoren.id(), saGrupom.path("grupaId").asLong());
        ocekujGresku("iskljuci_temu", mapa("serija", serija, "predmetId", predmet.getId(), "opis", "Opet",
                "grupaId", testKoren.id()), "Ova grupa je već isključena.");

        JsonNode teme = McpTestKlijent.json(uspeh("iskljucene_teme", mapa("predmetId", predmet.getId())));
        assertEquals(Set.of(tema.id(), t.path("id").asLong(), saGrupom.path("id").asLong()), ids(teme, "id"));
    }

    @Test
    void iskljuciTemuTrosiJednoPravljenjePreUpisa() {
        uspeh("iskljuci_temu", mapa("serija", serija, "predmetId", predmet.getId(), "opis", "Oktalni brojevi"));
        verify(budzet, times(1)).trosiPravljenje("test", 1);
        assertEquals(1, brojTemaBezGrupe());

        doThrow(new SystemException(McpBudzet.PORUKA, 429)).when(budzet).trosiPravljenje(anyString(), anyInt());
        ocekujGresku("iskljuci_temu", mapa("serija", serija, "predmetId", predmet.getId(), "opis", "Heksadekadni brojevi"),
                "Dnevni limit za Kloda je potrošen.");
        assertEquals(1, brojTemaBezGrupe());
    }

    @Test
    void ukljuciTemuBriseIskljucenje() {
        JsonNode r = McpTestKlijent.json(uspeh("ukljuci_temu", mapa("temaId", tema.id())));
        assertEquals(Set.of("ukljuceno"), new TreeSet<>(r.propertyNames()));
        assertTrue(r.path("ukljuceno").asBoolean());
        assertEquals(0, jdbc.queryForObject("select count(*) from iskljucene_teme where id = ?", Integer.class, tema.id()));
        JsonNode cvor = McpTestKlijent.json(uspeh("grupa", mapa("grupaId", list.id()))).path("grupa");
        assertFalse(cvor.path("iskljucena").asBoolean(true));
        assertEquals(0, McpTestKlijent.json(uspeh("iskljucene_teme", mapa("predmetId", predmet.getId()))).size());

        ocekujGresku("ukljuci_temu", mapa("temaId", tema.id()), "Isključena tema nije pronađena.");
        ocekujGresku("ukljuci_temu", Map.of(), "Neispravan parametar: temaId.");
    }

    @Test
    void iskljuceneTemeNepostojecegPredmeta() {
        ocekujGresku("iskljucene_teme", mapa("predmetId", Long.MAX_VALUE), "Predmet nije pronađen.");
        ocekujGresku("iskljucene_teme", Map.of(), "Predmet je obavezan.");
    }

    @Test
    void ukljuciTemuTraziPotvrdu() {
        assertTrue(alat("ukljuci_temu").path("_meta").path("anthropic/requiresUserInteraction").asBoolean(false));
        assertFalse(alat("iskljuci_temu").path("_meta").path("anthropic/requiresUserInteraction").asBoolean(false));
    }
}
