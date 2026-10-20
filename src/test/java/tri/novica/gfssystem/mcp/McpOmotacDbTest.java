package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Omotač alata nad onim što server zaista koristi (stateless {@code SyncToolSpecification}), kroz pravi {@code /mcp}:
 * nepoznat ključ (i u objektnom argumentu, sa putanjom), pogrešan tip i {@code null} za primitiv daju poruku na srpskom
 * bez tehničkih detalja i bez poziva metode alata; greške iz alata prolaze kroz {@link McpGreske}. SDK provera argumenata
 * prema šemi je isključena (parametri probnih alata su obavezni u šemi, a poziv bez njih stiže do omotača i alata).
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)   // MockMvc bi posle palog testa ispisao zaglavlja, i ključ
@Import(McpProbaAlati.class)
@ExtendWith(OutputCaptureExtension.class)
class McpOmotacDbTest {

    static final Pattern KLJUC_U_LOGU = Pattern.compile("gfs_[A-Za-z0-9_-]{43}");

    @Autowired MockMvc mvc;
    @Autowired McpProbaAlati alati;

    McpTestKlijent klijent;

    @BeforeEach
    void setUp() {
        klijent = new McpTestKlijent(mvc);
        alati.poziviAlata.set(0);
        alati.poziviObjekta.set(0);
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    void ocekujGresku(String alat, Map<String, Object> argumenti, String poruka) {
        JsonNode r = klijent.pozovi(alat, argumenti);
        assertTrue(McpTestKlijent.greska(r), r.toString());
        assertEquals(poruka, McpTestKlijent.tekst(r));
    }

    @Test
    void omotaniAlatiSuUSpiskuSaAnotacijamaIPotvrdom() {
        Map<String, JsonNode> alatiPoImenu = new HashMap<>();
        klijent.alati().forEach(a -> alatiPoImenu.put(a.path("name").asString(), a));
        JsonNode objekat = alatiPoImenu.get("proba_objekat");
        JsonNode alat = alatiPoImenu.get("proba_alat");
        assertNotNull(objekat, alatiPoImenu.keySet().toString());
        assertNotNull(alat, alatiPoImenu.keySet().toString());
        assertTrue(objekat.path("_meta").path("anthropic/requiresUserInteraction").asBoolean(), objekat.toString());
        assertFalse(alat.path("_meta").path("anthropic/requiresUserInteraction").asBoolean(false), alat.toString());
        assertFalse(alat.path("annotations").path("openWorldHint").asBoolean(true));
        assertTrue(alat.path("inputSchema").path("properties").has("broj"));
        assertFalse(alat.path("inputSchema").path("properties").has("zahtev"), "CallToolRequest nije argument");
    }

    @Test
    void nepoznatKljucNaVrhu() {
        ocekujGresku("proba_alat", mapa("broj", 1, "serija", "proba-1", "visak", 2), "Nepoznato polje 'visak'.");
        assertEquals(0, alati.poziviAlata.get());
    }

    @Test
    void pogresanTipProstogArgumentaJeNeispravanFormat(CapturedOutput izlaz) {
        JsonNode r = klijent.pozovi("proba_alat", mapa("broj", "abc"));
        assertTrue(McpTestKlijent.greska(r));
        assertEquals("Neispravan format podataka.", McpTestKlijent.tekst(r));
        assertEquals(0, alati.poziviAlata.get());
        String red = izlaz.getOut().lines().filter(l -> l.contains("MCP alat=proba_alat")).findFirst().orElseThrow();
        assertTrue(red.contains("ishod=neispravni-argumenti"), red);
        assertTrue(red.contains("kljuc=test"), red);
    }

    @Test
    void nepoznatKljucUObjektnomArgumentuSaPutanjom() {
        ocekujGresku("proba_objekat", mapa("obj", mapa("a", 1, "x", 2)), "Nepoznato polje 'obj.x'.");
        ocekujGresku("proba_objekat", mapa("obj", mapa("stavke", List.of(mapa("b", "q", "y", 1)))),
                "Nepoznato polje 'obj.stavke[0].y'.");
        assertEquals(0, alati.poziviObjekta.get());
    }

    @Test
    void pogresanTipINullZaPrimitivUObjektu(CapturedOutput izlaz) {
        ocekujGresku("proba_objekat", mapa("obj", mapa("zastavica", null)), "Neispravan format podataka.");
        ocekujGresku("proba_objekat", mapa("obj", mapa("a", "abc")), "Neispravan format podataka.");
        assertEquals(0, alati.poziviObjekta.get());
        assertEquals(2, izlaz.getOut().lines()
                .filter(l -> l.contains("MCP alat=proba_objekat") && l.contains("ishod=neispravni-argumenti")).count());
    }

    @Test
    void ispravanObjekatProlazi() {
        // izostavljen primitiv (zastavica) je podrazumevana vrednost, i za vezivanje u Spring AI-ju
        JsonNode r = klijent.pozovi("proba_objekat", mapa("obj", mapa("a", 1), "serija", "proba-1"));
        assertFalse(McpTestKlijent.greska(r), r.toString());
        assertEquals(1, McpTestKlijent.json(r).path("a").asInt());
        assertEquals(1, alati.poziviObjekta.get());

        r = klijent.pozovi("proba_objekat", mapa("obj", mapa("a", 2, "zastavica", true,
                "stavke", List.of(mapa("b", "q"))), "serija", "proba-1"));
        assertFalse(McpTestKlijent.greska(r), r.toString());
        assertEquals(2, McpTestKlijent.json(r).path("a").asInt());
    }

    @Test
    void greskeIzAlata(CapturedOutput izlaz) {
        ocekujGresku("proba_alat", mapa("broj", 1, "serija", "proba-1", "rezim", "sistem"), "Probna poruka.");
        ocekujGresku("proba_alat", mapa("broj", 1, "serija", "proba-1", "rezim", "pad"), "Sistemska greška.");
        assertEquals(2, alati.poziviAlata.get());
        assertTrue(izlaz.getOut().contains("ishod=greska-409"));
        assertTrue(izlaz.getOut().contains("ishod=sistemska"));
    }

    @Test
    void uspehIBezSerijeKrozPraviServer(CapturedOutput izlaz) {
        JsonNode r = klijent.pozovi("proba_alat", mapa("broj", 7, "serija", "proba-1"));
        assertFalse(McpTestKlijent.greska(r), r.toString());
        assertEquals(7, McpTestKlijent.json(r).path("broj").asInt());

        ocekujGresku("proba_alat", mapa(), "Serija mora imati 3 do 60 malih slova, cifara ili crtica.");
        assertFalse(KLJUC_U_LOGU.matcher(izlaz.getOut()).find(), "ključ u logu");
    }

    @Test
    void nullStizeDoAlataKaoPrisutanKljuc() {
        Map<String, Object> argumenti = mapa("serija", "proba-1");
        argumenti.put("broj", null);
        JsonNode r = klijent.pozovi("proba_alat", argumenti);
        assertFalse(McpTestKlijent.greska(r), r.toString());
        assertTrue(McpTestKlijent.json(r).path("imaBroj").asBoolean(), r.toString());
        assertTrue(McpTestKlijent.json(r).path("broj").isNull(), r.toString());

        r = klijent.pozovi("proba_alat", mapa("serija", "proba-1"));
        assertFalse(McpTestKlijent.json(r).path("imaBroj").asBoolean(true), r.toString());
    }

    @Test
    void bezKljucaJe401() {
        assertEquals(401, klijent.posalji("tools/call", Map.of("name", "proba_alat", "arguments", Map.of()), null)
                .getStatus());
        assertEquals(0, alati.poziviAlata.get());
    }
}
