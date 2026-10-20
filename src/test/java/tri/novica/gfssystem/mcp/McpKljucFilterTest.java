package tri.novica.gfssystem.mcp;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Filter ključa nad {@code /mcp}: 404 kad nema ključeva, 401 bez ispravnog Bearer ključa, oznaka u atribut zahteva. */
@ExtendWith(OutputCaptureExtension.class)
class McpKljucFilterTest {

    static final String TEST_KLJUC = McpKljuceviTest.TEST_KLJUC;
    static final Pattern KLJUC_U_LOGU = Pattern.compile("gfs_[A-Za-z0-9_-]{43}");

    final JsonMapper json = JsonMapper.builder().build();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-18T10:15:30Z"), ZoneId.of("Europe/Belgrade"));
    final McpKljucevi kljucevi = McpKljucevi.parsiraj("test:" + McpKljuceviTest.TEST_HES);

    MockHttpServletResponse filtriraj(McpKljucevi k, MockHttpServletRequest zahtev, FilterChain lanac) throws Exception {
        MockHttpServletResponse odgovor = new MockHttpServletResponse();
        new McpKljucFilter(k, json, clock).doFilter(zahtev, odgovor, lanac);
        return odgovor;
    }

    static MockHttpServletRequest zahtev(String authorization) {
        MockHttpServletRequest z = new MockHttpServletRequest("POST", "/mcp");
        if (authorization != null) {
            z.addHeader("Authorization", authorization);
        }
        return z;
    }

    void ocekuj401(MockHttpServletResponse odgovor) throws Exception {
        assertEquals(401, odgovor.getStatus());
        assertEquals("Bearer", odgovor.getHeader("WWW-Authenticate"));
        assertTrue(odgovor.getContentType().startsWith("application/json"));
        JsonNode telo = json.readTree(odgovor.getContentAsString());
        assertEquals("Neispravan MCP ključ.", telo.path("reason").asString());
        assertEquals("2026-10-18T12:15:30", telo.path("time").asString());
    }

    @Test
    void bezKljucevaJe404ILanacNijePozvan() throws Exception {
        FilterChain lanac = mock(FilterChain.class);
        MockHttpServletResponse odgovor = filtriraj(McpKljucevi.parsiraj(""), zahtev("Bearer " + TEST_KLJUC), lanac);
        assertEquals(404, odgovor.getStatus());
        assertNull(odgovor.getHeader("WWW-Authenticate"));
        verifyNoInteractions(lanac);
    }

    @Test
    void bezZaglavljaJe401() throws Exception {
        FilterChain lanac = mock(FilterChain.class);
        ocekuj401(filtriraj(kljucevi, zahtev(null), lanac));
        verifyNoInteractions(lanac);
    }

    @Test
    void basicJe401() throws Exception {
        FilterChain lanac = mock(FilterChain.class);
        ocekuj401(filtriraj(kljucevi, zahtev("Basic xyz"), lanac));
        ocekuj401(filtriraj(kljucevi, zahtev("Basic " + TEST_KLJUC), lanac));
        ocekuj401(filtriraj(kljucevi, zahtev(TEST_KLJUC), lanac));          // bez šeme
        ocekuj401(filtriraj(kljucevi, zahtev("Bearer"), lanac));
        ocekuj401(filtriraj(kljucevi, zahtev("Bearer "), lanac));
        verifyNoInteractions(lanac);
    }

    @Test
    void pogresanKljucJe401() throws Exception {
        FilterChain lanac = mock(FilterChain.class);
        ocekuj401(filtriraj(kljucevi, zahtev("Bearer " + McpKljuceviTest.DRUGI_KLJUC), lanac));
        ocekuj401(filtriraj(kljucevi, zahtev("Bearer " + McpKljuceviTest.TEST_HES), lanac));
        verifyNoInteractions(lanac);
    }

    @Test
    void ispravanKljucPropustaIUpisujeOznaku() throws Exception {
        for (String sema : new String[]{"Bearer ", "bearer ", "BEARER "}) {
            FilterChain lanac = mock(FilterChain.class);
            MockHttpServletRequest z = zahtev(sema + TEST_KLJUC);
            MockHttpServletResponse odgovor = filtriraj(kljucevi, z, lanac);
            verify(lanac).doFilter(any(), any());
            assertEquals(200, odgovor.getStatus());
            assertEquals("test", z.getAttribute(McpKljucFilter.OZNAKA));
        }
    }

    @Test
    void logNikadNeSadrziKljucNiZaglavlje(CapturedOutput izlaz) throws Exception {
        FilterChain lanac = mock(FilterChain.class);
        filtriraj(kljucevi, zahtev("Bearer " + TEST_KLJUC), lanac);
        filtriraj(kljucevi, zahtev("Bearer " + McpKljuceviTest.DRUGI_KLJUC), lanac);
        filtriraj(kljucevi, zahtev("Basic dGFqbmE6bG96aW5rYQ=="), lanac);
        filtriraj(kljucevi, zahtev(null), lanac);
        filtriraj(McpKljucevi.parsiraj(""), zahtev("Bearer " + TEST_KLJUC), lanac);
        String log = izlaz.getAll();
        assertFalse(KLJUC_U_LOGU.matcher(log).find(), "ključ u logu");
        assertFalse(log.contains("dGFqbmE6bG96aW5rYQ=="), "zaglavlje u logu");
        assertFalse(log.contains(McpKljuceviTest.TEST_HES), "heš u logu");
    }
}
