package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.dto.banka.SerijaInfo;

import java.util.List;

/**
 * Rezultat alata {@code kontekst} (spec MCP 4.2): okruženje ({@code gfs.okruzenje}: {@code PROD}, {@code STAGING}, ...),
 * baza UI-ja ({@code McpLinkovi.baza()}), svi predmeti po id-ju i poslednje serije preko svih predmeta, najnovije prve.
 */
public record McpKontekstInfo(String okruzenje, String linkUi, List<McpPredmetKontekst> predmeti,
                              List<SerijaInfo> poslednjeSerije) {
}
