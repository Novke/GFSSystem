package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDate;

/** Termin (stavka, datum) za otkazivanje ({@code napomena} do 200 znakova, ne mora) i vraćanje ({@code napomena} se ne čita). */
public record TerminCmd(Long stavkaId, LocalDate datum, String napomena) {
}
