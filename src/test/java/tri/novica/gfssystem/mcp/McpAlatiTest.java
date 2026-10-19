package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.mcp.alati.McpAlati;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tabela alata iz plana (Ugovori, "MCP alati A1"): broj alata, anotacije, potvrda korisnika ({@code _meta}), dužina punog
 * imena, {@link McpAlati#NE_PONAVLJAJ} na kraju opisa neidempotentnih upisa i provera serije pre posla (obavezna u
 * upisnim alatima, opcioni filter u alatima za čitanje). Provere idu nad {@code tools/list}, pa rastu same sa alatima;
 * svaki nov alat dobija red u {@link #OCEKIVANO}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class McpAlatiTest {

    /** {@code ro} = readOnlyHint, {@code d} = destructiveHint, {@code i} = idempotentHint, {@code p} = traži potvrdu. */
    record Red(boolean ro, boolean d, boolean i, boolean p) {}

    static final Map<String, Red> OCEKIVANO = Map.ofEntries(
            Map.entry("kontekst", new Red(true, false, true, false)),
            Map.entry("vodic", new Red(true, false, true, false)),
            Map.entry("preambula", new Red(true, false, true, false)),
            Map.entry("domaci_tekstovi", new Red(true, false, true, false)),
            Map.entry("stablo_banke", new Red(true, false, true, false)),
            Map.entry("grupa", new Red(true, false, true, false)),
            Map.entry("varijanta", new Red(true, false, true, false)),
            Map.entry("pretraga_banke", new Red(true, false, true, false)),
            Map.entry("iskljucene_teme", new Red(true, false, true, false)),
            Map.entry("iskljuci_temu", new Red(false, false, true, false)),
            Map.entry("ukljuci_temu", new Red(false, true, true, true)),
            Map.entry("napravi_grupu", new Red(false, false, false, false)),
            Map.entry("izmeni_grupu", new Red(false, true, true, false)),
            Map.entry("izmeni_polja_grupe", new Red(false, true, true, true)),
            Map.entry("premesti_grupu", new Red(false, false, true, false)),
            Map.entry("obrisi_grupu", new Red(false, true, true, true)),
            Map.entry("kopiraj_grupu", new Red(false, false, false, false)),
            Map.entry("ponisti_seriju", new Red(false, true, true, true)),
            Map.entry("napravi_varijantu", new Red(false, false, false, false)),
            Map.entry("izmeni_varijantu", new Red(false, true, false, false)),
            Map.entry("dupliraj_varijantu", new Red(false, false, false, false)),
            Map.entry("obrisi_varijantu", new Red(false, true, true, true)),
            Map.entry("proba_rendera", new Red(true, false, true, false)));

    static final String SERIJA_PORUKA = "Serija mora imati 3 do 60 malih slova, cifara ili crtica.";

    @Autowired MockMvc mvc;

    McpTestKlijent klijent;

    @BeforeEach
    void setUp() {
        klijent = new McpTestKlijent(mvc);
    }

    @Test
    void spisakAlataJeTacnoTabela() {
        Set<String> imena = new TreeSet<>();
        klijent.alati().forEach(a -> imena.add(a.path("name").asString()));
        assertEquals(new TreeSet<>(OCEKIVANO.keySet()), imena);
        assertEquals(OCEKIVANO.size(), klijent.alati().size());
    }

    @Test
    void anotacijeIPotvrdaPoTabeli() {
        List<Executable> provere = new ArrayList<>();
        for (JsonNode alat : klijent.alati()) {
            String ime = alat.path("name").asString();
            Red red = OCEKIVANO.get(ime);
            JsonNode a = alat.path("annotations");
            provere.add(() -> assertNotNull(red, "alat van tabele: " + ime));
            if (red == null) {
                continue;
            }
            provere.add(() -> assertEquals(red.ro(), a.path("readOnlyHint").asBoolean(), ime + " readOnlyHint"));
            provere.add(() -> assertEquals(red.d(), a.path("destructiveHint").asBoolean(), ime + " destructiveHint"));
            provere.add(() -> assertEquals(red.i(), a.path("idempotentHint").asBoolean(), ime + " idempotentHint"));
            provere.add(() -> assertFalse(a.path("openWorldHint").asBoolean(true), ime + " openWorldHint"));
            provere.add(() -> assertEquals(red.p(),
                    alat.path("_meta").path("anthropic/requiresUserInteraction").asBoolean(false), ime + " potvrda"));
            provere.add(() -> assertTrue(("mcp__gfs-staging__" + ime).length() <= 128, ime + " predugo ime"));
            provere.add(() -> assertTrue(ime.matches("^[a-z][a-z0-9_]*$"), ime + " nije snake_case ASCII"));
        }
        assertAll(provere);
    }

    @Test
    void opisNeidempotentnihUpisaZavrsavaUpozorenjem() {
        List<Executable> provere = new ArrayList<>();
        for (JsonNode alat : klijent.alati()) {
            String ime = alat.path("name").asString();
            String opis = alat.path("description").asString();
            JsonNode a = alat.path("annotations");
            boolean neidempotentanUpis = !a.path("idempotentHint").asBoolean() && !a.path("readOnlyHint").asBoolean();
            if (neidempotentanUpis) {
                provere.add(() -> assertTrue(opis.endsWith(McpAlati.NE_PONAVLJAJ), ime + ": " + opis));
            } else {
                provere.add(() -> assertFalse(opis.contains(McpAlati.NE_PONAVLJAJ), ime + ": " + opis));
            }
        }
        assertAll(provere);
    }

    /**
     * Upisni alat ima obaveznu seriju i proverava je pre posla (i bez ostalih argumenata); alat za čitanje je ima samo kao
     * opcioni filter ({@code pretraga_banke}), pa se proverava samo serija van obrasca (bez nje alat traži druge argumente).
     */
    @Test
    void serijaSeProveravaPrePosla() {
        List<Executable> provere = new ArrayList<>();
        for (JsonNode alat : klijent.alati()) {
            String ime = alat.path("name").asString();
            JsonNode sema = alat.path("inputSchema");
            if (!sema.path("properties").has("serija")) {
                continue;
            }
            boolean upis = !alat.path("annotations").path("readOnlyHint").asBoolean();
            boolean obavezna = sema.path("required").valueStream().anyMatch(n -> "serija".equals(n.asString()));
            provere.add(() -> assertEquals(upis, obavezna, ime + ": serija je obavezna tačno u upisnom alatu"));
            List<Map<String, Object>> pozivi = upis ? List.of(Map.of("serija", "Uvoz-1"), Map.of())
                    : List.of(Map.of("serija", "Uvoz-1"));
            for (Map<String, Object> argumenti : pozivi) {
                JsonNode r = klijent.pozovi(ime, argumenti);
                provere.add(() -> assertTrue(McpTestKlijent.greska(r), ime + " " + argumenti));
                provere.add(() -> assertEquals(SERIJA_PORUKA, McpTestKlijent.tekst(r), ime + " " + argumenti));
            }
        }
        assertAll(provere);
    }

    @Test
    void konstantaUpozorenja() {
        assertEquals("Ne ponavljaj posle isteka vremena; prvo proveri `pretraga_banke`, `grupa` ili `prezentacija`.",
                McpAlati.NE_PONAVLJAJ);
    }
}
