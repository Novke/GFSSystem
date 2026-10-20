package tri.novica.gfssystem.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Linkovi u UI koje upisni alati vraćaju ({@code link}, {@code linkPregleda}; spec MCP 4.1): baza je prvi URL iz
 * {@code gfs.front.url} (lista razdvojena zarezom, kao za CORS), bez završne {@code /}.
 */
@Component
public class McpLinkovi {

    private final String baza;

    public McpLinkovi(@Value("${gfs.front.url}") String frontUrl) {
        String prvi = frontUrl.split(",")[0].strip();
        while (prvi.endsWith("/")) {
            prvi = prvi.substring(0, prvi.length() - 1);
        }
        this.baza = prvi;
    }

    public String baza() {
        return baza;
    }

    public String varijanta(Long id) {
        return baza + "/banka/varijante/" + id;
    }

    public String grupa(Long id) {
        return baza + "/banka/grupe/" + id;
    }

    public String prezentacija(Long id) {
        return baza + "/prezentacije/" + id;
    }

    /** Pregled nacrta jedne serije; serija je url-enkodovana (razmak kao {@code %20}). */
    public String pregled(String serija) {
        return baza + "/banka/pregled?serija=" + URLEncoder.encode(serija, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
