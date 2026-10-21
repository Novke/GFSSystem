package tri.novica.gfssystem.mcp.servis;

import com.fasterxml.jackson.annotation.JsonInclude;
import tri.novica.gfssystem.dto.uzivo.OpcijaCmd;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

/**
 * Pitanje u sažetku slajda: tip, tekst, opcije sa tačnim ({@code null} za tipove bez opcija), tačan broj (BROJ) i
 * prihvatljivi odgovori (KRATAK_TEKST). Ostala polja pitanja daje alat {@code slajd}; odsutna polja se ne pišu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpPitanjeSazetak(TipPitanja tip, String tekst, List<OpcijaCmd> opcije, Double brojTacno,
                                List<String> prihvatljiviOdgovori) {
}
