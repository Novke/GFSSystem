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
import tri.novica.gfssystem.service.banka.LatexDokument;
import tri.novica.gfssystem.service.banka.RenderNedostupan;
import tri.novica.gfssystem.service.banka.VarijantaService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alati varijanti kroz pravi {@code /mcp} (spec MCP 4.1, 4.3): {@code napravi_varijantu} (Klodov nacrt iz serije i kad
 * argumenti ne pominju izvor, PNG iz istog prevođenja po nameni grupe, greška rendera sa linijom i delom, jedno pravljenje
 * iz budžeta, {@code linkPregleda}), {@code izmeni_varijantu} (merge patch: na mestu samo sopstveni neodobren nacrt, inače
 * kopija sa {@code linkOriginala}; nepoznat ključ, {@code null} za polje koje se ne briše i prazna izmena ne upisuju ništa;
 * keš rendera daje sliku sačuvanog sadržaja; 409 kad Novica promeni sadržaj tokom rendera), {@code dupliraj_varijantu}
 * (Klodov nacrt), {@code obrisi_varijantu} (samo sopstveni neodobren nacrt) i {@code proba_rendera} (ništa se ne upisuje,
 * slika sa rešenjem). Nijedan rezultat ne nosi id-jeve SVG-a.
 */
class VarijanteAlatiDbTest extends McpDbTestOsnova {

    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>"
            .getBytes(StandardCharsets.UTF_8);
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    /** {@link #ODGOVOR_A} kao argument alata. */
    static final Map<String, Object> ODGOVORI = Map.of("a", Map.of("broj", 11));
    static final String TEKST = "Pretvori $1011_{(2)}$ u dekadni.";
    static final String NOVI_TEKST = "Pretvori $1100_{(2)}$ u dekadni.";
    static final String RESENJE = "Rešenje: $11$.";
    static final String LINK = "http://localhost:4200";
    static final String NIJE_STIGLA = "Slika nije stigla na vreme.";
    static final String IZMENJENA = "Varijanta ili preambula je u međuvremenu izmenjena; pokušaj ponovo.";
    static final String SAMO_SOPSTVENI = "Klod briše samo svoje neodobrene nacrte.";
    static final String BEZ_POLJA = "Izmena mora sadržati bar jedno polje varijante.";
    static final List<String> SVG_I_PNG = List.of("svg", "png");
    static final List<String> SAMO_PNG = List.of("png");
    /** Linije koje dokument za telefon dodaje ispred teksta (preambula predmeta je podrazumevana). */
    static final int POMAK = LatexDokument.zaTelefon(null, "").pomakLinija();
    /** Visina slike zadatka i rešenja u moku, i spojene slike (razmak, linija, razmak između njih). */
    static final int VISINA_ZADATKA = 40;
    static final int VISINA_RESENJA = 60;
    static final int VISINA_SPOJENE = VISINA_ZADATKA + 16 + 2 + 16 + VISINA_RESENJA;
    static final byte[] PNG_ZADATKA = png(100, VISINA_ZADATKA);
    static final byte[] PNG_RESENJA = png(100, VISINA_RESENJA);

    @Autowired GrupaZadatakaService grupaService;
    @Autowired VarijantaService varijantaService;
    @MockitoSpyBean McpBudzet budzet;

    Predmet predmet;
    String serija;
    /** Novicin list PREDAVANJE sa jednom odobrenom (AKTIVNOM) Novicinom varijantom {@link #rucna}. */
    GrupaDetails list;
    /** Novicin list TEST bez varijanti. */
    GrupaDetails testList;
    Long rucna;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, b64(SVG), null, null, null, 7L, false));
        when(latexRender.renderuj(anyList(), anyString(), anyList()))
                .thenAnswer(inv -> odgovor(inv.getArgument(0), inv.getArgument(1)));
        predmet = noviPredmet("Banka varijante MCP test");
        serija = "varijante-" + UUID.randomUUID().toString().substring(0, 8);
        GrupaDetails koren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "Brojni sistemi",
                null, null, null, null));
        list = grupaService.kreiraj(new GrupaCmd(null, koren.id(), null, "2 → 10", null, 2, null, List.of(BROJ_A)));
        testList = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum 1", null, 3, null,
                List.of(BROJ_A)));
        rucna = varijantaService.kreiraj(list.id(), cmd("V1", TEKST, RESENJE, null)).id();
        varijantaService.odobri(rucna);
        clearInvocations(latexRender);
    }

    static VarijantaCmd cmd(String naziv, String tekst, String resenje, IzvorVarijante izvor) {
        return new VarijantaCmd(naziv, tekst, resenje, ODGOVOR_A, List.of(), izvor);
    }

    /** Klodov nacrt iz serije testa (kroz servis, bez PNG-a), render OK. */
    Long klodovNacrt(GrupaDetails grupa, String tekst, String resenje) {
        return varijantaService.kreiraj(grupa.id(), cmd("K1", tekst, resenje, IzvorVarijante.CLAUDE), serija, false)
                .varijanta().id();
    }

    static String b64(byte[] bajtovi) {
        return Base64.getEncoder().encodeToString(bajtovi);
    }

    /** Uspeh sa svakim traženim formatom; PNG rešenja je viši od PNG-a zadatka. */
    static RenderOdgovor odgovor(List<String> formati, String tex) {
        String svg = formati.contains("svg") ? b64(SVG) : null;
        String png = formati.contains("png") ? b64(tex.contains("Rešenje:") ? PNG_RESENJA : PNG_ZADATKA) : null;
        return new RenderOdgovor(true, svg, null, null, null, 7L, false, png);
    }

    /** Neuspela kompilacija sa greškom u liniji {@code linijaUTelu} dela (tekst ili rešenje). */
    static RenderOdgovor neuspeh(int linijaUTelu, String poruka) {
        int linija = POMAK + linijaUTelu;
        return new RenderOdgovor(false, null, null, new RenderGreskaInfo(linija, poruka),
                "(./dokument.tex\n./dokument.tex:" + linija + ": " + poruka + "\n", null, false);
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

    static Set<String> kljucevi(JsonNode cvor) {
        return new TreeSet<>(cvor.propertyNames());
    }

    /** Ni jedan tekstualni blok ne nosi id-jeve SVG-a (javni URL-ovi, modelu ne trebaju). */
    static void bezSvgId(JsonNode rezultat) {
        String tekst = McpTestKlijent.tekst(rezultat);
        assertFalse(tekst.contains("svgId"), tekst);
        assertFalse(tekst.contains("resenjeSvgId"), tekst);
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

    int brojVarijanti() {
        Integer n = jdbc.queryForObject("select count(*) from varijante v join grupe_zadataka g on g.id = v.grupa_id "
                + "where g.predmet_id = ?", Integer.class, predmet.getId());
        return n == null ? 0 : n;
    }

    boolean postoji(Long varijantaId) {
        Integer n = jdbc.queryForObject("select count(*) from varijante where id = ?", Integer.class, varijantaId);
        return n != null && n > 0;
    }

    Map<String, Object> red(Long varijantaId) {
        return jdbc.queryForMap("select naziv, tekst, resenje, odgovori, status, izvor, serija, ikad_odobrena, zamenjuje_id, "
                + "render_status, render_svg_id, render_hash, izmenjeno from varijante where id = ?", varijantaId);
    }

    Map<String, Object> napravi(GrupaDetails grupa, Object... dodatno) {
        Map<String, Object> m = mapa("serija", serija, "grupaId", grupa.id(), "naziv", "Zbirka 1.2", "tekst", TEKST,
                "resenje", RESENJE, "odgovori", ODGOVORI);
        m.putAll(mapa(dodatno));
        return m;
    }

    // ---------------------------------------------------------------- napravi_varijantu

    @Test
    void napraviVarijantuJeKlodovNacrtSaSlikomILinkomPregleda() {
        int pre = brojVarijanti();
        JsonNode r = uspeh("napravi_varijantu", napravi(list));
        bezSvgId(r);
        assertEquals(VISINA_SPOJENE, visinaSlike(r));
        assertEquals(1, tekstovi(r).size(), "slika je stigla, nema napomene");

        JsonNode u = McpTestKlijent.json(r);
        assertEquals(Set.of("radnja", "varijanta", "link", "linkPregleda"), kljucevi(u));
        assertEquals("nova", u.path("radnja").asString());
        JsonNode v = u.path("varijanta");
        Long id = v.path("id").asLong();
        assertEquals("NACRT", v.path("status").asString());
        assertEquals("CLAUDE", v.path("izvor").asString());
        assertEquals(serija, v.path("serija").asString());
        assertEquals("OK", v.path("renderStatus").asString());
        assertTrue(v.path("renderGreska").isNull());
        assertTrue(v.path("renderDeo").isNull());
        assertEquals("PREDAVANJE", v.path("namena").asString());
        assertEquals("Zbirka 1.2", v.path("naziv").asString());
        assertEquals(TEKST, v.path("tekst").asString());
        assertEquals(RESENJE, v.path("resenje").asString());
        assertEquals(11.0, v.path("odgovori").path("a").path("broj").asDouble());
        assertFalse(v.path("ikadOdobrena").asBoolean(true));
        assertTrue(v.path("zamenjuje").isNull());
        assertEquals(LINK + "/banka/varijante/" + id, u.path("link").asString());
        assertEquals(u.path("link").asString(), v.path("link").asString());
        assertEquals(LINK + "/banka/pregled?serija=" + serija, u.path("linkPregleda").asString());

        Map<String, Object> red = red(id);
        assertEquals("CLAUDE", red.get("izvor"));
        assertEquals(serija, red.get("serija"));
        assertEquals("NACRT", red.get("status"));
        assertEquals(pre + 1, brojVarijanti());
        verify(budzet, times(1)).trosiPravljenje("test", 1);
        // SVG za čuvanje i PNG za Kloda iz istog prevođenja po delu, bez posebnog rendera slike
        verify(latexRender, times(2)).renderuj(eq(SVG_I_PNG), anyString(), anyList());
        verify(latexRender, never()).renderuj(eq(SAMO_PNG), anyString(), anyList());
        verify(latexRender, never()).renderuj(eq("svg"), anyString(), anyList());
    }

    @Test
    void napraviVarijantuNeBiraIzvor() {
        int pre = brojVarijanti();
        ocekujGresku("napravi_varijantu", napravi(list, "izvor", "RUCNO"), "Nepoznato polje 'izvor'.");
        ocekujGresku("napravi_varijantu", napravi(list, "odgovori", Map.of("a", Map.of("broj", 11, "x", 1))),
                "Nepoznato polje 'odgovori.a.x'.");
        assertEquals(pre, brojVarijanti());
        verify(budzet, never()).trosiPravljenje(anyString(), anyInt());
    }

    @Test
    void napraviVarijantuUTestuDajeSamoZadatakOsimUzSlikuResenja() {
        JsonNode samoZadatak = uspeh("napravi_varijantu", napravi(testList));
        assertEquals(VISINA_ZADATKA, visinaSlike(samoZadatak));
        assertEquals("TEST", McpTestKlijent.json(samoZadatak).path("varijanta").path("namena").asString());

        JsonNode saResenjem = uspeh("napravi_varijantu", napravi(testList, "naziv", "Zbirka 1.3", "slikaResenja", true));
        assertEquals(VISINA_SPOJENE, visinaSlike(saResenjem));
    }

    @Test
    void napraviVarijantuBezSlike() {
        JsonNode r = uspeh("napravi_varijantu", napravi(list, "slika", false));
        assertEquals(List.of(), McpTestKlijent.slike(r));
        assertEquals(1, tekstovi(r).size(), "bez slike nema ni napomene");
        assertEquals("OK", McpTestKlijent.json(r).path("varijanta").path("renderStatus").asString());
        verify(latexRender, times(2)).renderuj(eq("svg"), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyList(), anyString(), anyList());
    }

    @Test
    void greskaRenderaJeUpisSaLinijomIDelom() {
        doReturn(neuspeh(2, "Undefined control sequence.")).when(latexRender)
                .renderuj(anyList(), contains("\\greska"), anyList());

        JsonNode r = uspeh("napravi_varijantu", napravi(list, "resenje", "Rešenje:\n$\\greska 11$."));
        JsonNode v = McpTestKlijent.json(r).path("varijanta");
        assertEquals("GRESKA", v.path("renderStatus").asString());
        assertEquals("resenje", v.path("renderDeo").asString());
        assertEquals(2, v.path("renderGreska").path("linija").asInt());
        assertEquals("Undefined control sequence.", v.path("renderGreska").path("poruka").asString());
        assertEquals(VISINA_ZADATKA, visinaSlike(r), "palo je samo rešenje: slika zadatka stiže");
        assertEquals(1, tekstovi(r).size());

        JsonNode t = uspeh("napravi_varijantu", napravi(list, "naziv", "Zbirka 1.4", "tekst", "Pretvori\n$\\greska$."));
        JsonNode tv = McpTestKlijent.json(t).path("varijanta");
        assertEquals("GRESKA", tv.path("renderStatus").asString());
        assertEquals("tekst", tv.path("renderDeo").asString());
        assertEquals(2, tv.path("renderGreska").path("linija").asInt());
        assertEquals(List.of(), McpTestKlijent.slike(t), "tekst se nije preveo: slike nema");
        assertEquals(1, tekstovi(t).size(), "greška je u podacima, bez napomene o slici");
    }

    @Test
    void potrosenBudzetNeUpisujeNiNeRenderuje() {
        int pre = brojVarijanti();
        doThrow(new SystemException(McpBudzet.PORUKA, 429)).when(budzet).trosiPravljenje(anyString(), anyInt());
        ocekujGresku("napravi_varijantu", napravi(list), "Dnevni limit za Kloda je potrošen.");
        ocekujGresku("dupliraj_varijantu", mapa("serija", serija, "varijantaId", rucna),
                "Dnevni limit za Kloda je potrošen.");
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", rucna, "tekst", NOVI_TEKST),
                "Dnevni limit za Kloda je potrošen.");
        assertEquals(pre, brojVarijanti());
        verify(latexRender, never()).renderuj(anyList(), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyString(), anyString(), anyList());
    }

    // ---------------------------------------------------------------- izmeni_varijantu

    @Test
    void izmenaSopstvenogNacrtaJeNaMestu() {
        Long id = klodovNacrt(list, TEKST, RESENJE);
        clearInvocations(latexRender);

        JsonNode r = uspeh("izmeni_varijantu", mapa("serija", serija, "varijantaId", id, "tekst", NOVI_TEKST));
        bezSvgId(r);
        assertEquals(VISINA_SPOJENE, visinaSlike(r));
        JsonNode u = McpTestKlijent.json(r);
        assertEquals(Set.of("radnja", "varijanta", "link"), kljucevi(u));
        assertEquals("izmenjena", u.path("radnja").asString());
        assertEquals(id, u.path("varijanta").path("id").asLong());
        assertEquals(NOVI_TEKST, u.path("varijanta").path("tekst").asString());
        assertEquals(RESENJE, u.path("varijanta").path("resenje").asString(), "izostavljeno rešenje ostaje");
        assertEquals(LINK + "/banka/varijante/" + id, u.path("link").asString());
        assertEquals(NOVI_TEKST, red(id).get("tekst"));
        assertEquals(RESENJE, red(id).get("resenje"));

        JsonNode bez = McpTestKlijent.json(uspeh("izmeni_varijantu", mapa("serija", serija, "varijantaId", id,
                "resenje", null, "slika", false)));
        assertTrue(bez.path("varijanta").path("resenje").isNull());
        assertNull(red(id).get("resenje"));
        verify(budzet, never()).trosiPravljenje(anyString(), anyInt());
    }

    @Test
    void nepoznatoPoljeINullNebrisivogNeUpisujuNista() {
        Long id = klodovNacrt(list, TEKST, RESENJE);
        Map<String, Object> pre = red(id);
        int varijante = brojVarijanti();
        clearInvocations(latexRender);

        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", id, "resenja", "x"),
                "Nepoznato polje 'resenja'.");
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", id, "tekst", null),
                "Polje 'tekst' se ne briše.");
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", id, "odgovori", null),
                "Polje 'odgovori' se ne briše.");
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", id,
                "odgovori", Map.of("a", Map.of("broj", 11, "x", 1))), "Nepoznato polje 'odgovori.a.x'.");
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", id,
                "prilozi", List.of(Map.of("ime", "slika", "medijId", "x", "y", 1))), "Nepoznato polje 'prilozi[0].y'.");
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", id, "tekst", 5),
                "Neispravan format podataka.");

        assertEquals(pre, red(id));
        assertEquals(varijante, brojVarijanti());
        verify(latexRender, never()).renderuj(anyList(), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyString(), anyString(), anyList());
    }

    @Test
    void izmenaNovicineVarijantePraviKopiju() {
        int pre = brojVarijanti();
        JsonNode r = uspeh("izmeni_varijantu", mapa("serija", serija, "varijantaId", rucna, "tekst", NOVI_TEKST));
        bezSvgId(r);
        assertEquals(VISINA_SPOJENE, visinaSlike(r));
        JsonNode u = McpTestKlijent.json(r);
        assertEquals(Set.of("radnja", "varijanta", "link", "linkOriginala"), kljucevi(u));
        assertEquals("kopija", u.path("radnja").asString());
        JsonNode k = u.path("varijanta");
        Long kopija = k.path("id").asLong();
        assertNotEquals(rucna, kopija);
        assertEquals(rucna, k.path("zamenjuje").asLong());
        assertEquals("CLAUDE", k.path("izvor").asString());
        assertEquals("NACRT", k.path("status").asString());
        assertEquals(serija, k.path("serija").asString());
        assertEquals(NOVI_TEKST, k.path("tekst").asString());
        assertEquals(RESENJE, k.path("resenje").asString());
        assertEquals(LINK + "/banka/varijante/" + kopija, u.path("link").asString());
        assertEquals(LINK + "/banka/varijante/" + rucna, u.path("linkOriginala").asString());

        Map<String, Object> original = red(rucna);
        assertEquals("AKTIVNA", original.get("status"));
        assertEquals(TEKST, original.get("tekst"));
        assertEquals("RUCNO", original.get("izvor"));
        assertEquals(pre + 1, brojVarijanti());
        verify(budzet, times(1)).trosiPravljenje("test", 1);
    }

    @Test
    void praznaIzmenaNePraviKopiju() {
        int pre = brojVarijanti();
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", rucna), BEZ_POLJA);
        ocekujGresku("izmeni_varijantu", mapa("serija", serija, "varijantaId", rucna, "slika", true,
                "slikaResenja", true), BEZ_POLJA);
        assertEquals(pre, brojVarijanti());
        verify(budzet, never()).trosiPravljenje(anyString(), anyInt());
        verify(latexRender, never()).renderuj(anyList(), anyString(), anyList());
    }

    @Test
    void izmenaBezRenderaDajeSlikuSacuvanogSadrzaja() {
        Long id = klodovNacrt(list, TEKST, RESENJE);
        clearInvocations(latexRender);

        // naziv nije deo ulaza rendera: keš važi, upis ne renderuje, slika dolazi iz VarijantaService.slika
        JsonNode r = uspeh("izmeni_varijantu", mapa("serija", serija, "varijantaId", id, "naziv", "Zbirka 2.1"));
        JsonNode u = McpTestKlijent.json(r);
        assertEquals("izmenjena", u.path("radnja").asString());
        assertEquals("Zbirka 2.1", u.path("varijanta").path("naziv").asString());
        assertEquals(VISINA_SPOJENE, visinaSlike(r));
        assertEquals(1, tekstovi(r).size());
        verify(latexRender, never()).renderuj(eq(SVG_I_PNG), anyString(), anyList());
        verify(latexRender, never()).renderuj(eq("svg"), anyString(), anyList());
        verify(latexRender, times(2)).renderuj(eq(SAMO_PNG), anyString(), anyList());

        Long testNacrt = klodovNacrt(testList, TEKST, RESENJE);
        JsonNode t = uspeh("izmeni_varijantu", mapa("serija", serija, "varijantaId", testNacrt, "naziv", "K2"));
        assertEquals(VISINA_ZADATKA, visinaSlike(t), "TEST: samo zadatak");

        doThrow(new RenderNedostupan()).when(latexRender).renderuj(eq(SAMO_PNG), anyString(), anyList());
        JsonNode n = uspeh("izmeni_varijantu", mapa("serija", serija, "varijantaId", id, "naziv", "Zbirka 3"));
        assertEquals(List.of(), McpTestKlijent.slike(n));
        assertEquals(List.of(McpTestKlijent.tekst(n), NIJE_STIGLA), tekstovi(n));
        assertEquals("Zbirka 3", red(id).get("naziv"), "izmena je upisana i bez slike");
    }

    @Test
    void izmenaKojuNovicaPromeniTokomRenderaJe409BezSlike() throws Exception {
        Long id = klodovNacrt(list, TEKST, null);
        int pre = brojVarijanti();
        CountDownLatch uRenderu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        doAnswer(inv -> {
            uRenderu.countDown();
            pusti.await(10, TimeUnit.SECONDS);
            return odgovor(inv.getArgument(0), inv.getArgument(1));
        }).when(latexRender).renderuj(anyList(), contains("1100"), anyList());

        CompletableFuture<JsonNode> izmena = CompletableFuture.supplyAsync(() -> mcp.pozovi("izmeni_varijantu",
                mapa("serija", serija, "varijantaId", id, "tekst", NOVI_TEKST)));
        assertTrue(uRenderu.await(10, TimeUnit.SECONDS));
        // Novica u UI-ju dodaje rešenje (polje van Klodovog patch-a) dok Klodov render traje
        varijantaService.izmeni(id, cmd("K1", TEKST, RESENJE, null));
        pusti.countDown();
        JsonNode r = izmena.get(30, TimeUnit.SECONDS);

        assertTrue(McpTestKlijent.greska(r), r.toString());
        assertEquals(IZMENJENA, McpTestKlijent.tekst(r));
        assertEquals(List.of(), McpTestKlijent.slike(r));
        Map<String, Object> red = red(id);
        assertEquals(TEKST, red.get("tekst"));
        assertEquals(RESENJE, red.get("resenje"));
        assertEquals(pre, brojVarijanti());
    }

    // ---------------------------------------------------------------- dupliraj i obriši

    @Test
    void duplikatJeKlodovNacrtIzSerije() {
        int pre = brojVarijanti();
        JsonNode r = uspeh("dupliraj_varijantu", mapa("serija", serija, "varijantaId", rucna));
        bezSvgId(r);
        assertEquals(List.of(), McpTestKlijent.slike(r));
        JsonNode d = McpTestKlijent.json(r);
        assertEquals(Set.of("varijanta", "link"), kljucevi(d));
        JsonNode v = d.path("varijanta");
        Long id = v.path("id").asLong();
        assertNotEquals(rucna, id);
        assertEquals("CLAUDE", v.path("izvor").asString());
        assertEquals(serija, v.path("serija").asString());
        assertEquals("NACRT", v.path("status").asString());
        assertEquals(TEKST, v.path("tekst").asString());
        assertEquals("PREDAVANJE", v.path("namena").asString());
        assertEquals(LINK + "/banka/varijante/" + id, d.path("link").asString());
        assertEquals("CLAUDE", red(id).get("izvor"));
        assertEquals(serija, red(id).get("serija"));
        assertEquals(pre + 1, brojVarijanti());
        verify(budzet, times(1)).trosiPravljenje("test", 1);
    }

    @Test
    void brisanjeSamoSopstvenogNeodobrenogNacrta() {
        ocekujGresku("obrisi_varijantu", mapa("serija", serija, "varijantaId", rucna), SAMO_SOPSTVENI);
        assertTrue(postoji(rucna));

        Long odobren = klodovNacrt(list, TEKST, RESENJE);
        varijantaService.odobri(odobren);
        ocekujGresku("obrisi_varijantu", mapa("serija", serija, "varijantaId", odobren), SAMO_SOPSTVENI);
        assertTrue(postoji(odobren));

        Long nacrt = klodovNacrt(list, TEKST, RESENJE);
        JsonNode r = McpTestKlijent.json(uspeh("obrisi_varijantu", mapa("serija", serija, "varijantaId", nacrt)));
        assertEquals(Set.of("obrisano"), kljucevi(r));
        assertTrue(r.path("obrisano").asBoolean());
        assertFalse(postoji(nacrt));
        ocekujGresku("obrisi_varijantu", mapa("serija", serija, "varijantaId", nacrt), "Varijanta nije pronađena.");
        ocekujGresku("obrisi_varijantu", mapa("serija", serija), "Neispravan parametar: varijantaId.");
    }

    // ---------------------------------------------------------------- proba_rendera

    @Test
    void probaRenderaNeUpisujeIImaSlikuSaResenjem() {
        int pre = brojVarijanti();
        JsonNode r = uspeh("proba_rendera", mapa("predmetId", predmet.getId(), "tekst", TEKST, "resenje", RESENJE));
        assertFalse(McpTestKlijent.tekst(r).contains("svg"), McpTestKlijent.tekst(r));
        JsonNode p = McpTestKlijent.json(r);
        assertEquals(Set.of("ok", "greska", "deo"), kljucevi(p));
        assertTrue(p.path("ok").asBoolean());
        assertTrue(p.path("greska").isNull());
        assertTrue(p.path("deo").isNull());
        assertEquals(VISINA_SPOJENE, visinaSlike(r), "proba uvek prikazuje poslato rešenje");
        verify(latexRender, times(2)).renderuj(eq(SAMO_PNG), anyString(), anyList());
        verify(latexRender, never()).renderuj(eq("svg"), anyString(), anyList());
        verify(latexRender, never()).renderuj(eq(SVG_I_PNG), anyString(), anyList());
        assertEquals(pre, brojVarijanti());

        JsonNode bez = uspeh("proba_rendera", mapa("predmetId", predmet.getId(), "tekst", TEKST, "slika", false));
        assertEquals(List.of(), McpTestKlijent.slike(bez));
        assertTrue(McpTestKlijent.json(bez).path("ok").asBoolean());

        doReturn(neuspeh(1, "Undefined control sequence.")).when(latexRender)
                .renderuj(anyList(), contains("\\greska"), anyList());
        JsonNode g = uspeh("proba_rendera", mapa("predmetId", predmet.getId(), "tekst", "$\\greska$"));
        JsonNode gp = McpTestKlijent.json(g);
        assertFalse(gp.path("ok").asBoolean(true));
        assertEquals("tekst", gp.path("deo").asString());
        assertEquals(1, gp.path("greska").path("linija").asInt());
        assertEquals(List.of(), McpTestKlijent.slike(g));
        assertEquals(1, tekstovi(g).size());
        assertEquals(pre, brojVarijanti());
        verify(budzet, never()).trosiPravljenje(anyString(), anyInt());

        ocekujGresku("proba_rendera", mapa("tekst", TEKST), "Predmet je obavezan.");
    }
}
