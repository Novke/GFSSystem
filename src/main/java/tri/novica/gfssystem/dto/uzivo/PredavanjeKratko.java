package tri.novica.gfssystem.dto.uzivo;

import java.time.LocalDate;

/** Predavanje za koje je vezano izvođenje. */
public record PredavanjeKratko(Long id, int rb, LocalDate datum, String tema) {
}
