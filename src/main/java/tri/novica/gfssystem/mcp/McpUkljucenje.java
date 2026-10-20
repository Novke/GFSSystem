package tri.novica.gfssystem.mcp;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * {@code spring.ai.mcp.server.enabled} prati postojanje ključeva (spec MCP 3.1): prazan {@code gfs.mcp.kljucevi} gasi
 * Spring AI MCP server (nema ni rute), inače ga pali. Izvor je najvišeg prioriteta, pa ga ni env ni svojstvo ne
 * nadjačavaju. Registrovan u {@code META-INF/spring.factories}; ide posle učitavanja konfiguracije (najniži prioritet
 * među post-procesorima), da bi {@code gfs.mcp.kljucevi} iz {@code application.properties} već bio vidljiv.
 */
public class McpUkljucenje implements EnvironmentPostProcessor, Ordered {

    static final String IZVOR = "gfsMcpUkljucenje";
    static final String SVOJSTVO = "spring.ai.mcp.server.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication aplikacija) {
        boolean ukljucen = StringUtils.hasText(env.getProperty("gfs.mcp.kljucevi", ""));
        env.getPropertySources().remove(IZVOR);
        env.getPropertySources().addFirst(new MapPropertySource(IZVOR, Map.of(SVOJSTVO, String.valueOf(ukljucen))));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
