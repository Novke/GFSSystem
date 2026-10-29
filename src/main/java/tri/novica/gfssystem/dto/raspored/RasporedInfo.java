package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDate;

/** Red liste perioda ({@code GET /rasporedi}). */
public record RasporedInfo(Long id, String naziv, LocalDate vaziOd, LocalDate vaziDo, int pragNajava, long brojStavki) {
}
