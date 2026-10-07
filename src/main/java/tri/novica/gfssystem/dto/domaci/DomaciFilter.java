package tri.novica.gfssystem.dto.domaci;

import java.time.LocalDate;

/**
 * Filteri liste domaćih ({@code GET /domaci/pretraga}); svako polje je opciono, null = bez filtera.
 * {@code godina} je školska godina (1. 10. godina - 30. 9. godina+1), {@code q} se traži u naslovu bez obzira na
 * velika i mala slova, {@code od}/{@code doDatuma} su uključivi.
 */
public record DomaciFilter(Long predmetId, Long grupaId, Integer godina, Boolean pregledan, String q,
                           LocalDate od, LocalDate doDatuma) {
}
