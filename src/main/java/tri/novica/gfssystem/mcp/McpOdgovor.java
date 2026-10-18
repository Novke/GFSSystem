package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;

/**
 * Rezultat MCP alata (spec MCP 4.1): JSON u prvom tekstualnom bloku, opciono napomena u drugom (npr.
 * {@code Slika nije stigla na vreme.}) i jedna PNG slika. Aplikacioni {@link JsonMapper}, isti kao za REST. Tekst duži od
 * {@link #MAX_ZNAKOVA} (oko 8 000 tokena; Claude Code upozorava na 10k, seče na 25k) ostavlja WARN sa dužinom: spiskovi
 * su stranični, pa je to greška u alatu.
 */
@Slf4j
@Component
public class McpOdgovor {

    public static final int MAX_ZNAKOVA = 28_000;

    private final JsonMapper json;

    public McpOdgovor(JsonMapper json) {
        this.json = json;
    }

    public CallToolResult json(Object podaci) {
        return jsonSaSlikom(podaci, null, null);
    }

    public CallToolResult jsonSaSlikom(Object podaci, byte[] png, String napomena) {
        String tekst = json.writeValueAsString(podaci);
        if (tekst.length() > MAX_ZNAKOVA) {
            log.warn("MCP odgovor ima {} znakova (najviše {})", tekst.length(), MAX_ZNAKOVA);
        }
        CallToolResult.Builder rezultat = CallToolResult.builder().addTextContent(tekst);
        if (napomena != null) {
            rezultat.addTextContent(napomena);
        }
        if (png != null) {
            rezultat.addContent(ImageContent.builder(Base64.getEncoder().encodeToString(png), "image/png").build());
        }
        return rezultat.build();
    }
}
