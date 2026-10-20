package tri.novica.gfssystem.mcp.alati;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.Map;

/**
 * {@code _meta} alata koji traži potvrdu korisnika pre poziva ({@code @McpTool(metaProvider = TraziPotvrdu.class)}):
 * {@code obrisi_*}, {@code ponisti_seriju}, {@code ukljuci_temu}, {@code izmeni_polja_grupe} (spec MCP 4.1, "Anotacije").
 */
public class TraziPotvrdu implements MetaProvider {

    @Override
    public Map<String, Object> getMeta() {
        return Map.of("anthropic/requiresUserInteraction", true);
    }
}
