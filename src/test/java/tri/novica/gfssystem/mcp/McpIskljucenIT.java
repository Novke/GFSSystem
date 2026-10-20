package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.function.RouterFunction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bez ključeva MCP ne postoji: nema MCP rute, a {@code /mcp} i {@code /mcp/*} su 404 i sa (nekada važećim) ključem. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "gfs.mcp.kljucevi=")
class McpIskljucenIT {

    @LocalServerPort int port;
    @Autowired ApplicationContext kontekst;

    final HttpClient http = HttpClient.newHttpClient();

    int post(String putanja) throws Exception {
        HttpRequest z = HttpRequest.newBuilder(URI.create("http://localhost:" + port + putanja))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + McpKljuceviTest.TEST_KLJUC)
                .POST(HttpRequest.BodyPublishers.ofString(McpRuteIT.INITIALIZE))
                .build();
        return http.send(z, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void mcpJe404() throws Exception {
        assertEquals(404, post("/mcp"));
        assertEquals(404, post("/mcp/prilozi"));
        assertEquals(404, post("/mcp/slike"));
    }

    @Test
    void springAiNePraviRutu() {
        assertEquals("false", kontekst.getEnvironment().getProperty("spring.ai.mcp.server.enabled"));
        assertTrue(kontekst.getBeansOfType(RouterFunction.class).isEmpty());
    }
}
