package tri.novica.gfssystem.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP i prijava zajedno, preko pravog Tomcat-a (redosled servlet filtera kao u kontejneru; spec 3 i 13.2), sa sintetičkim
 * test ključem iz test {@code application.properties}: {@code McpKljucFilter} radi pre Spring Security lanca i jedini
 * odlučuje na {@code /mcp}, {@code /mcp/prilozi} i {@code /mcp/slike} (401 Bearer bez ključa, prolaz sa ključem i bez
 * nastavničke sesije); nastavnički kolačić sam ne otvara MCP, a MCP ključ ne otvara ništa van te tri putanje.
 * Isključen MCP (404) je u {@link McpLanacIskljucenIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpLanacIT {

    /** Sintetički test ključ (isti kao {@code mcp.McpKljuceviTest.TEST_KLJUC}; na serveru je samo njegov SHA-256). */
    static final String TEST_KLJUC = "gfs_TEST000000000000000000000000000000000000000";
    static final List<String> MCP_PUTANJE = List.of("/mcp", "/mcp/prilozi", "/mcp/slike");
    static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
            "capabilities":{},"clientInfo":{"name":"mcp-lanac-it","version":"1"}}}""";
    static final String GRANICA = "----gfs-mcp-lanac-it";

    @LocalServerPort int port;
    @Autowired ApplicationContext ctx;
    @Autowired JsonMapper json;
    @Autowired NastavnikSesijaServis nastavnici;
    @Autowired JdbcTemplate jdbc;

    final HttpClient http = HttpClient.newHttpClient();
    final List<String> tokeni = new ArrayList<>();

    @AfterEach
    void obrisiSesije() {
        tokeni.forEach(t -> jdbc.update("delete from nastavnicke_sesije where token_hes = ?", TokenHash.od(t)));
    }

    /** Nastavnička sesija; vraća zaglavlje {@code Cookie}. */
    String nastavnik() {
        String kolacic = PrijavaTestUtil.nastavnickiKolacic(nastavnici);
        tokeni.add(kolacic.substring(kolacic.indexOf('=') + 1));
        return kolacic;
    }

    HttpResponse<String> posalji(String metod, String putanja, String kljuc, String kolacic) throws Exception {
        return posalji(metod, putanja, kljuc, kolacic, null);
    }

    HttpResponse<String> posalji(String metod, String putanja, String kljuc, String kolacic, String origin) throws Exception {
        HttpRequest.Builder z = HttpRequest.newBuilder(URI.create("http://localhost:" + port + putanja))
                .header("Accept", "application/json, text/event-stream");
        if (origin != null) z.header("Origin", origin);
        if (putanja.startsWith("/mcp/")) {
            // multipart sa fajlom koji nije slika ni PDF: handler ga odbija svojom proverom (ništa se ne upisuje)
            String telo = "--" + GRANICA + "\r\nContent-Disposition: form-data; name=\"fajl\"; filename=\"x.txt\"\r\n"
                    + "Content-Type: text/plain\r\n\r\nnije slika\r\n--" + GRANICA + "--\r\n";
            z.header("Content-Type", "multipart/form-data; boundary=" + GRANICA)
                    .method(metod, HttpRequest.BodyPublishers.ofString(telo, StandardCharsets.UTF_8));
        } else {
            z.header("Content-Type", "application/json")
                    .method(metod, "GET".equals(metod) ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(INITIALIZE));
        }
        if (kljuc != null) z.header("Authorization", "Bearer " + kljuc);
        if (kolacic != null) z.header("Cookie", kolacic);
        return http.send(z.build(), HttpResponse.BodyHandlers.ofString());
    }

    String razlog(HttpResponse<String> r) {
        return json.readTree(r.body()).path("reason").asString();
    }

    void ocekujBearer401(HttpResponse<String> r, String opis) {
        assertEquals(401, r.statusCode(), opis + ": " + r.body());
        assertEquals("Bearer", r.headers().firstValue("WWW-Authenticate").orElse(null), opis);
        assertEquals("Neispravan MCP ključ.", razlog(r), opis + ": odgovara filter ključa, ne nastavnički lanac");
        assertTrue(r.headers().allValues("Set-Cookie").isEmpty(), opis + ": MCP ne dira kolačiće");
    }

    @Test
    void filterKljucaJePreSpringSecurityLanca() {
        int mcp = ((FilterRegistrationBean<?>) ctx.getBean("mcpKljucFilter")).getOrder();
        Map<String, DelegatingFilterProxyRegistrationBean> lanci = ctx.getBeansOfType(DelegatingFilterProxyRegistrationBean.class);
        assertEquals(1, lanci.size(), lanci.keySet().toString());
        int security = lanci.values().iterator().next().getOrder();
        assertTrue(mcp < security, "McpKljucFilter (" + mcp + ") mora pre Spring Security lanca (" + security + ")");
    }

    @Test
    void bezKljucaIliSaPogresnimJe401BearerNikadPotrebnaPrijava() throws Exception {
        for (String p : MCP_PUTANJE) {
            ocekujBearer401(posalji("POST", p, null, null), "POST " + p + " bez ključa");
            ocekujBearer401(posalji("POST", p, "gfs_pogresan", null), "POST " + p + " pogrešan ključ");
            ocekujBearer401(posalji("GET", p, null, null), "GET " + p + " bez ključa");
        }
    }

    @Test
    void nastavnickiKolacicSamNeOtvaraMcp() throws Exception {
        String kolacic = nastavnik();
        for (String p : MCP_PUTANJE) {
            ocekujBearer401(posalji("POST", p, null, kolacic), "POST " + p + " samo sa nastavničkim kolačićem");
        }
    }

    @Test
    void saKljucemBezNastavnickeSesijeIBezPorekla() throws Exception {
        HttpResponse<String> r = posalji("POST", "/mcp", TEST_KLJUC, null);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("gfs", json.readTree(r.body()).path("result").path("serverInfo").path("name").asString());
        assertTrue(r.headers().allValues("Set-Cookie").isEmpty());

        // upload stiže do kontrolera (njegova provera tipa fajla odbija tekst), ne do lanca prijave ni provere porekla
        for (String p : List.of("/mcp/prilozi", "/mcp/slike")) {
            HttpResponse<String> u = posalji("POST", p, TEST_KLJUC, null);
            assertEquals(400, u.statusCode(), p + ": " + u.body());
            assertNotEquals("Potrebna je prijava.", razlog(u), p);
            assertNotEquals("Zahtev nije dozvoljen.", razlog(u), p);
        }
    }

    @Test
    void kljucNeOtvaraNistaVanTriPutanje() throws Exception {
        for (String[] z : new String[][]{{"GET", "/predmeti"}, {"GET", "/banka/serije"}, {"GET", "/mcp/slike/1"},
                {"POST", "/mcp/nesto"}, {"GET", "/auth/sesije"}}) {
            // dozvoljen Origin, da upis prođe proveru porekla glavnog lanca i stigne do pravila pristupa
            HttpResponse<String> r = posalji(z[0], z[1], TEST_KLJUC, null, PrijavaTestUtil.ORIGIN);
            assertEquals(401, r.statusCode(), z[0] + " " + z[1] + " sa MCP ključem: " + r.body());
            assertEquals("Potrebna je prijava.", razlog(r), z[0] + " " + z[1]);
        }
    }
}
