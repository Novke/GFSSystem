package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDate;
import java.util.List;

/** Period sa stavkama, po danu pa početku ({@code GET /rasporedi/{id}}, odgovor na upis). */
public record RasporedDetails(Long id, String naziv, LocalDate vaziOd, LocalDate vaziDo, int pragNajava,
                              List<StavkaInfo> stavke) {
}
