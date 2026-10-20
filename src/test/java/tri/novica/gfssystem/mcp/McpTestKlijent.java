package tri.novica.gfssystem.mcp;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * JSON-RPC prema {@code /mcp} preko MockMvc sa sintetičkim test ključem (filter ključa radi kao u kontejneru). Za testove
 * alata (BE-2 i dalje, B i A2): {@link #pozovi} vraća {@code result} poziva alata, a statički pomoćnici čitaju tekst,
 * JSON iz prvog tekstualnog bloka, zastavicu greške i slike.
 */
public class McpTestKlijent {

    public static final String KLJUC = "gfs_TEST000000000000000000000000000000000000000";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private final AtomicInteger id = new AtomicInteger();

    public McpTestKlijent(MockMvc mvc) {
        this.mvc = mvc;
    }

    public JsonNode initialize() {
        return rezultat("initialize", Map.of(
                "protocolVersion", "2025-06-18",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "mcp-test-klijent", "version", "1")));
    }

    public List<JsonNode> alati() {
        List<JsonNode> alati = new ArrayList<>();
        rezultat("tools/list", Map.of()).path("tools").forEach(alati::add);
        return alati;
    }

    /** {@code result} poziva alata ({@code content}, {@code isError}); JSON-RPC greška obara test. */
    public JsonNode pozovi(String alat, Map<String, Object> argumenti) {
        return rezultat("tools/call", Map.of("name", alat, "arguments", argumenti));
    }

    /** Sirov HTTP odgovor (za provere statusa, npr. bez ključa). */
    public MockHttpServletResponse posalji(String metoda, Map<String, Object> parametri, String kljuc) {
        ObjectNode telo = JSON.createObjectNode();
        telo.put("jsonrpc", "2.0");
        telo.put("id", id.incrementAndGet());
        telo.put("method", metoda);
        telo.set("params", JSON.valueToTree(parametri));
        var zahtev = post("/mcp")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .content(JSON.writeValueAsString(telo));
        if (kljuc != null) {
            zahtev.header("Authorization", "Bearer " + kljuc);
        }
        try {
            MockHttpServletResponse odgovor = mvc.perform(zahtev).andReturn().getResponse();
            odgovor.setCharacterEncoding(StandardCharsets.UTF_8.name());
            return odgovor;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode rezultat(String metoda, Map<String, Object> parametri) {
        MockHttpServletResponse odgovor = posalji(metoda, parametri, KLJUC);
        String telo;
        try {
            telo = odgovor.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (odgovor.getStatus() != 200) {
            throw new AssertionError(metoda + " -> HTTP " + odgovor.getStatus() + ": " + telo);
        }
        JsonNode cvor = JSON.readTree(telo);
        if (cvor.has("error")) {
            throw new AssertionError(metoda + " -> JSON-RPC greška: " + cvor.get("error"));
        }
        return cvor.path("result");
    }

    public static String tekst(JsonNode rezultat) {
        for (JsonNode blok : rezultat.path("content")) {
            if ("text".equals(blok.path("type").asString())) {
                return blok.path("text").asString();
            }
        }
        throw new AssertionError("Rezultat nema tekstualni blok: " + rezultat);
    }

    public static JsonNode json(JsonNode rezultat) {
        return JSON.readTree(tekst(rezultat));
    }

    public static boolean greska(JsonNode rezultat) {
        return rezultat.path("isError").asBoolean(false);
    }

    public static List<JsonNode> slike(JsonNode rezultat) {
        List<JsonNode> slike = new ArrayList<>();
        for (JsonNode blok : rezultat.path("content")) {
            if ("image".equals(blok.path("type").asString())) {
                slike.add(blok);
            }
        }
        return slike;
    }
}
