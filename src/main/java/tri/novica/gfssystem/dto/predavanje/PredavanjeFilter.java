package tri.novica.gfssystem.dto.predavanje;

import java.time.LocalDate;

/**
 * Filteri liste predavanja ({@code GET /predavanja/pretraga}); svako polje je opciono, null = bez filtera.
 * {@code godina} je školska godina (1. 10. godina - 30. 9. godina+1), {@code q} se traži u temi bez obzira na
 * velika i mala slova, {@code od}/{@code doDatuma} su uključivi.
 */
public record PredavanjeFilter(Long predmetId, Long grupaId, Integer godina, Boolean zavrseno, String q,
                               LocalDate od, LocalDate doDatuma) {
}
