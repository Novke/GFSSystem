package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDate;
import java.util.List;

/**
 * Period sa stavkama, po danu pa početku ({@code GET /rasporedi/{id}}, odgovor na upis). {@code izgubljenaOtkazivanja}:
 * koliko otkazanih termina od danas je izmena ({@code PUT}) ukinula (stavka obrisana ili napravljena iznova, drugi dan,
 * datum van novog perioda); 0 na čitanju i pravljenju.
 */
public record RasporedDetails(Long id, String naziv, LocalDate vaziOd, LocalDate vaziDo, int pragNajava,
                              List<StavkaInfo> stavke, int izgubljenaOtkazivanja) {
}
