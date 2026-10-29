package tri.novica.gfssystem.dto.raspored;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalTime;

public record StavkaInfo(Long id, PredmetInfo predmet, GrupaInfo grupa, int dan, LocalTime pocetak, LocalTime kraj) {
}
