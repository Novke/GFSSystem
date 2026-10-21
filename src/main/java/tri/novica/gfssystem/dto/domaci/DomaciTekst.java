package tri.novica.gfssystem.dto.domaci;

import java.time.LocalDate;

/**
 * Tekst domaćeg zadatka kao izvor za banku (MCP {@code domaci_tekstovi}): samo zadatak, nikad grupa, studenti ni predaje.
 */
public record DomaciTekst(Long id, String naslov, String text, LocalDate datum) {
}
