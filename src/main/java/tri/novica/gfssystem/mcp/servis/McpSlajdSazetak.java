package tri.novica.gfssystem.mcp.servis;

import com.fasterxml.jackson.annotation.JsonInclude;
import tri.novica.gfssystem.dto.uzivo.Prelaz;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

import java.util.List;

/**
 * Slajd u sažetom pregledu ({@link PrezentacijaPregled#sazetak}; spec MCP 4.4): zajednička polja (beleške skraćene,
 * prelaz, {@code brojKoraka} = klikova do kraja slajda), pa po tipu: INFO {@code naslov} i skraćen {@code sadrzaj},
 * PITANJE {@code pitanje}, PLATNO {@code slojevi} u z-redu, ZADATAK {@code zadatak} ({@link McpZadatakSazetak}). Bez poteza crteža i id-jeva medija van slojeva; odsutna polja
 * se ne pišu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpSlajdSazetak(Long id, int rb, TipSlajda tip, String beleske, Prelaz prelaz, int brojKoraka,
                              String naslov, String sadrzaj, McpPitanjeSazetak pitanje, List<McpSlojSazetak> slojevi,
                              McpZadatakSazetak zadatak) {
}
