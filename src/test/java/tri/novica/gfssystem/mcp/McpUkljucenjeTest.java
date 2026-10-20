package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code spring.ai.mcp.server.enabled} prati postojanje ključeva i ne može se nadjačati ručno. */
class McpUkljucenjeTest {

    static String ukljucen(MockEnvironment env) {
        new McpUkljucenje().postProcessEnvironment(env, new SpringApplication());
        return env.getProperty("spring.ai.mcp.server.enabled");
    }

    @Test
    void bezKljucevaIskljucen() {
        assertEquals("false", ukljucen(new MockEnvironment()));
        assertEquals("false", ukljucen(new MockEnvironment().withProperty("gfs.mcp.kljucevi", "")));
        assertEquals("false", ukljucen(new MockEnvironment().withProperty("gfs.mcp.kljucevi", "   ")));
        // kao u glavnom application.properties bez env GFS_MCP_KLJUCEVI
        assertEquals("false", ukljucen(new MockEnvironment().withProperty("gfs.mcp.kljucevi", "${GFS_MCP_KLJUCEVI:}")));
    }

    @Test
    void saKljucemUkljucen() {
        assertEquals("true", ukljucen(new MockEnvironment()
                .withProperty("gfs.mcp.kljucevi", "test:" + McpKljuceviTest.TEST_HES)));
    }

    @Test
    void rucnoSvojstvoNeNadjacava() {
        // bez ključa MCP ostaje isključen i kad neko postavi enabled=true (izvor najvišeg prioriteta)
        assertEquals("false", ukljucen(new MockEnvironment().withProperty("spring.ai.mcp.server.enabled", "true")));
        assertEquals("true", ukljucen(new MockEnvironment()
                .withProperty("spring.ai.mcp.server.enabled", "false")
                .withProperty("gfs.mcp.kljucevi", "test:" + McpKljuceviTest.TEST_HES)));
    }
}
