package tri.novica.gfssystem.mcp.servis;

import com.fasterxml.jackson.annotation.JsonInclude;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;

import java.util.Map;

/**
 * Rezultat alata {@code slajd} (spec MCP 4.4): ceo slajd kao u editoru, ali u {@code slajd.platno} CRTEZ slojevi imaju
 * {@code potezi = null} ({@link PrezentacijaPregled#bezPoteza}); {@code brojTacaka} je id CRTEZ sloja -> broj tačaka (samo
 * za PLATNO, inače se ne piše) i {@code link} prezentacije u UI-ju.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpSlajd(SlajdDetails slajd, Map<String, Integer> brojTacaka, String link) {
}
