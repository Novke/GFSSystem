package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Startne provere MCP-a (protokol, rute pod putanjom filtera) i čitanje uputstava servera. */
class McpKonfigTest {

    static final HandlerFunction<ServerResponse> H = zahtev -> ServerResponse.ok().build();

    @Test
    void ukljucenMcpTraziStateless() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> McpKonfig.proveri(true, "STREAMABLE", List.of("/mcp"), "/mcp"));
        assertTrue(e.getMessage().contains("STATELESS"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> McpKonfig.proveri(true, null, List.of("/mcp"), "/mcp"));
        // Spring AI uslov poredi bez obzira na velika slova
        assertDoesNotThrow(() -> McpKonfig.proveri(true, "stateless", List.of("/mcp"), "/mcp"));
    }

    @Test
    void rutaVanPutanjeFilteraRusiStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> McpKonfig.proveri(true, "STATELESS", List.of("/mcp", "/drugo"), "/mcp"));
        assertTrue(e.getMessage().contains("/drugo"), e.getMessage());
        // "/mcpx" nije pod /mcp/*
        assertThrows(IllegalStateException.class,
                () -> McpKonfig.proveri(true, "STATELESS", List.of("/mcpx"), "/mcp"));
        assertDoesNotThrow(() -> McpKonfig.proveri(true, "STATELESS", List.of("/mcp", "/mcp/prilozi", "/mcp/{x}"), "/mcp"));
    }

    @Test
    void iskljucenMcpNePraviRute() {
        assertDoesNotThrow(() -> McpKonfig.proveri(false, "STREAMABLE", List.of("/drugo"), "/mcp"));
    }

    /**
     * Kontekst sa McpKonfig, ključem i jednom RouterFunction rutom na zadatoj putanji; transport (u aplikaciji iz Spring AI
     * auto-konfiguracije) je ovde bean, jer McpKonfig pravi stateless server nad njim.
     */
    static ApplicationContextRunner kontekst(String putanjaRute, String... svojstva) {
        return new ApplicationContextRunner()
                .withUserConfiguration(McpKonfig.class)
                .withBean(McpStatelessServerTransport.class, () -> WebMvcStatelessServerTransport.builder().build())
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(Clock.class, Clock::systemDefaultZone)
                .withBean("ruta", RouterFunction.class, () -> RouterFunctions.route().POST(putanjaRute, H).build())
                .withPropertyValues("gfs.mcp.kljucevi=test:" + McpKljuceviTest.TEST_HES)
                .withPropertyValues(svojstva);
    }

    @Test
    void startnaProveraVidiRouterFunctionBeanove() {
        String[] ukljucen = {"spring.ai.mcp.server.enabled=true", "spring.ai.mcp.server.protocol=STATELESS"};
        kontekst("/mcp", ukljucen).run(ctx -> assertNull(ctx.getStartupFailure()));
        kontekst("/drugo", ukljucen).run(ctx -> {
            assertNotNull(ctx.getStartupFailure());
            assertTrue(ctx.getStartupFailure().getMessage().contains("/drugo"), ctx.getStartupFailure().getMessage());
        });
        kontekst("/mcp", "spring.ai.mcp.server.enabled=true", "spring.ai.mcp.server.protocol=STREAMABLE").run(ctx -> {
            assertNotNull(ctx.getStartupFailure());
            assertTrue(ctx.getStartupFailure().getMessage().contains("STATELESS"), ctx.getStartupFailure().getMessage());
        });
        kontekst("/drugo", "spring.ai.mcp.server.enabled=false").run(ctx -> assertNull(ctx.getStartupFailure()));
    }

    @Test
    void putanjeRute() {
        assertEquals(List.of("/mcp"), McpKonfig.putanje(RouterFunctions.route().POST("/mcp", H).build()));
        assertEquals(List.of("/mcp", "/mcp"),
                McpKonfig.putanje(RouterFunctions.route().GET("/mcp", H).POST("/mcp", H).build()));
        // ugnežđena ruta dobija prefiks
        assertEquals(List.of("/mcp/prilozi"),
                McpKonfig.putanje(RouterFunctions.route().path("/mcp", b -> b.POST("/prilozi", H)).build()));
        // "ili" daje obe putanje
        assertEquals(List.of("/mcp", "/drugo"), McpKonfig.putanje(
                RouterFunctions.route(RequestPredicates.path("/mcp").or(RequestPredicates.path("/drugo")), H)));
    }

    @Test
    void rutaBezPutanjeIliSaNegacijomNeProlazi() {
        RouterFunction<ServerResponse> sve = RouterFunctions.route(RequestPredicates.all(), H);
        assertThrows(IllegalStateException.class,
                () -> McpKonfig.proveri(true, "STATELESS", McpKonfig.putanje(sve), "/mcp"));
        RouterFunction<ServerResponse> samoMetoda = RouterFunctions.route(RequestPredicates.method(HttpMethod.POST), H);
        assertThrows(IllegalStateException.class,
                () -> McpKonfig.proveri(true, "STATELESS", McpKonfig.putanje(samoMetoda), "/mcp"));
        RouterFunction<ServerResponse> negacija = RouterFunctions.route(RequestPredicates.path("/mcp").negate(), H);
        assertThrows(IllegalStateException.class,
                () -> McpKonfig.proveri(true, "STATELESS", McpKonfig.putanje(negacija), "/mcp"));
    }

    @Test
    void uputstvaSeCitajuKaoUtf8() {
        String tekst = McpKonfig.ucitajUputstva(new ByteArrayResource("Čitaj šemu: ćao, đak, žaba.\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Čitaj šemu: ćao, đak, žaba.", tekst);
    }

    @Test
    void preDugaUputstvaRusePokretanje() {
        assertDoesNotThrow(() -> McpKonfig.ucitajUputstva(new ByteArrayResource("č".repeat(4_000).getBytes(StandardCharsets.UTF_8))));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> McpKonfig.ucitajUputstva(new ByteArrayResource("č".repeat(4_001).getBytes(StandardCharsets.UTF_8))));
        assertTrue(e.getMessage().contains("4000") || e.getMessage().contains("4 000"), e.getMessage());
    }

    @Test
    void uputstvaKojaNisuUtf8RusePokretanje() {
        byte[] latin2 = "čitaj".getBytes(java.nio.charset.Charset.forName("ISO-8859-2"));
        assertThrows(IllegalStateException.class, () -> McpKonfig.ucitajUputstva(new ByteArrayResource(latin2)));
    }

    @Test
    void pravaUputstvaSuIspravna() {
        String tekst = McpKonfig.ucitajUputstva(new ClassPathResource(McpKonfig.UPUTSTVA));
        assertTrue(tekst.contains("č"));
        assertTrue(tekst.length() <= McpKonfig.MAX_UPUTSTVA);
    }
}
