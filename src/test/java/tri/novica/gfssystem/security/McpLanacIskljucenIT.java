package tri.novica.gfssystem.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Isključen MCP (prazan {@code gfs.mcp.kljucevi}) uz prijavu, preko pravog Tomcat-a: {@code /mcp}, {@code /mcp/prilozi} i
 * {@code /mcp/slike} su 404 i sa nekada važećim ključem i sa nastavničkim kolačićem; nastavnička sesija ih ne otvara.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "gfs.mcp.kljucevi=")
class McpLanacIskljucenIT {

    @LocalServerPort int port;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired JdbcTemplate jdbc;

    final HttpClient http = HttpClient.newHttpClient();
    String token;

    @AfterEach
    void obrisiSesiju() {
        if (token != null) jdbc.update("delete from nastavnicke_sesije where token_hes = ?", TokenHash.od(token));
    }

    int post(String putanja, String kolacic) throws Exception {
        HttpRequest.Builder z = HttpRequest.newBuilder(URI.create("http://localhost:" + port + putanja))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + McpLanacIT.TEST_KLJUC)
                .POST(HttpRequest.BodyPublishers.ofString(McpLanacIT.INITIALIZE));
        if (kolacic != null) z.header("Cookie", kolacic);
        return http.send(z.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void mcpJe404IZaNastavnika() throws Exception {
        String kolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        token = kolacic.substring(kolacic.indexOf('=') + 1);
        for (String p : McpLanacIT.MCP_PUTANJE) {
            assertEquals(404, post(p, null), p + " bez kolačića");
            assertEquals(404, post(p, kolacic), p + " sa nastavničkim kolačićem");
        }
    }
}
