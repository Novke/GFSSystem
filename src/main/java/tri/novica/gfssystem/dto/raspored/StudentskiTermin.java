package tri.novica.gfssystem.dto.raspored;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Termin na nalogu studenta ({@code /public/nalog/casovi}). Nikad broj najava ni tuđa imena: samo njegova najava
 * ({@code najavljeno}, {@code razlog}). {@code svojaGrupa}: grupa stavke je studentova (inače sluša sa mlađom grupom).
 */
public record StudentskiTermin(Long stavkaId, LocalDate datum, LocalTime pocetak, LocalTime kraj, String predmet,
                               String grupa, boolean svojaGrupa, boolean otkazan, String napomena, boolean najavljeno,
                               String razlog) {
}
