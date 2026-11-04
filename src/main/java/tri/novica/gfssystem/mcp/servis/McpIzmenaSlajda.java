package tri.novica.gfssystem.mcp.servis;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Rezultat izmene slajda: sažetak, {@code izmenaId} za {@code vrati_izmenu} ({@code null} kad se ništa nije promenilo) i link. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpIzmenaSlajda(McpSlajdSazetak slajd, Long izmenaId, List<String> upozorenja, String link) {
}
