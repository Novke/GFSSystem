package tri.novica.gfssystem.dto.raspored;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Stavka perioda; {@code otkazani} = otkazani termini stavke od danas (rastuće), da forma upozori kad bi ih izmena izgubila.
 */
public record StavkaInfo(Long id, PredmetInfo predmet, GrupaInfo grupa, int dan, LocalTime pocetak, LocalTime kraj,
                         List<LocalDate> otkazani) {
}
