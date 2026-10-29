package tri.novica.gfssystem.dto.raspored;

import java.time.LocalTime;

/** Jedna stavka u {@link RasporedCmd}: {@code dan} ISO 1-7, vreme na minut ({@code "10:00"}). */
public record StavkaCmd(Long id, Long predmetId, Long grupaId, Integer dan, LocalTime pocetak, LocalTime kraj) {
}
