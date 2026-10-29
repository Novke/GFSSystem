package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDate;
import java.time.LocalTime;

/** Red liste perioda ({@code GET /rasporedi}); {@code podsetnikVreme} je {@code null} kad je podsetnik isključen. */
public record RasporedInfo(Long id, String naziv, LocalDate vaziOd, LocalDate vaziDo, int pragNajava, long brojStavki,
                           LocalTime podsetnikVreme) {
}
