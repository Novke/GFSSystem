package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDate;
import java.util.List;

/**
 * Ceo period sa stavkama ({@code POST /rasporedi}, {@code PUT /rasporedi/{id}}). {@code pragNajava} null = 5. {@code PUT}
 * zamenjuje stavke: stavka sa {@code id}-jem ostaje (i njene najave) dok joj se ne promene predmet ili grupa, nove nemaju
 * {@code id}, izostavljene se brišu.
 */
public record RasporedCmd(String naziv, LocalDate vaziOd, LocalDate vaziDo, Integer pragNajava, List<StavkaCmd> stavke) {
}
