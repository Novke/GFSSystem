package tri.novica.gfssystem.dto.uzivo;

import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

/**
 * Slajd (puna zamena); {@code pitanje} važi samo za tip PITANJE, {@code platno} (dokument platna kao sirovi JSON, strogo
 * ga čita {@code PlatnoJson}) samo za PLATNO; {@code prelaz} (faza 5) važi za svaki tip, {@code null} = bez prelaza.
 * Pravila: {@code validation/SlajdPP} i {@code validation/PlatnoPP}.
 */
public record SlajdCmd(TipSlajda tip, String naslov, String sadrzaj, String slikaId, String beleske, boolean postepeno,
                       PitanjeCmd pitanje, JsonNode platno, Prelaz prelaz) {
}
