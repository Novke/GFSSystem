package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.function.RouterFunction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP preko pravog HTTP-a (Tomcat, kao u kontejneru): bez ključa nijedna MCP ruta ne odgovara 200, ni preko
 * zaobilaznih oblika putanje; sa test ključem {@code initialize} vraća ime servera i UTF-8 uputstva. Ništa ne upisuje.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpRuteIT {

    static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
            "capabilities":{},"clientInfo":{"name":"mcp-rute-it","version":"1"}}}""";

    @LocalServerPort int port;
    @Autowired ApplicationContext kontekst;
    @Autowired JsonMapper json;

    final HttpClient http = HttpClient.newHttpClient();

    HttpResponse<String> post(String putanja, String kljuc) throws Exception {
        HttpRequest.Builder z = HttpRequest.newBuilder(URI.create("http://localhost:" + port + putanja))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE));
        if (kljuc != null) {
            z.header("Authorization", "Bearer " + kljuc);
        }
        return http.send(z.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void bezKljucaJe401SaBearerIzazovom() throws Exception {
        HttpResponse<String> odgovor = post("/mcp", null);
        assertEquals(401, odgovor.statusCode());
        assertEquals("Bearer", odgovor.headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals("Neispravan MCP ključ.", json.readTree(odgovor.body()).path("reason").asString());

        assertEquals(401, post("/mcp", "gfs_x").statusCode());
        assertEquals(401, post("/mcp/prilozi", null).statusCode());
    }

    @Test
    void zaobilazniOblikPutanjeNikadNije200() throws Exception {
        for (String putanja : List.of("/mcp/", "/mcp;x=1", "/%6Dcp", "//mcp", "/MCP", "/mcp/../mcp", "/./mcp")) {
            int status = post(putanja, null).statusCode();
            assertNotEquals(200, status, putanja);
        }
    }

    @Test
    void svakaRouterFunctionRutaJeIzaFiltera() throws Exception {
        List<String> putanje = new ArrayList<>();
        for (RouterFunction<?> ruta : kontekst.getBeansOfType(RouterFunction.class).values()) {
            putanje.addAll(McpKonfig.putanje(ruta));
        }
        assertTrue(putanje.contains("/mcp"), "MCP ruta nije pronađena: " + putanje);
        for (String obrazac : putanje) {
            String putanja = obrazac.replaceAll("\\{[^}]*}", "x").replace("**", "x").replace("*", "x");
            int status = post(putanja, null).statusCode();
            assertTrue(status == 401 || status == 404, putanja + " -> " + status);
        }
    }

    @Test
    void initializeSaKljucem() throws Exception {
        HttpResponse<String> odgovor = post("/mcp", McpKljuceviTest.TEST_KLJUC);
        assertEquals(200, odgovor.statusCode(), odgovor.body());
        JsonNode rezultat = json.readTree(odgovor.body()).path("result");
        assertEquals("gfs", rezultat.path("serverInfo").path("name").asString());
        String uputstva = rezultat.path("instructions").asString();
        assertTrue(uputstva.contains("č"), uputstva);
        assertTrue(uputstva.length() <= 4_000);
    }
}
