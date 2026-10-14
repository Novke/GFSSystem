package tri.novica.gfssystem.dto.test;

import java.time.LocalDate;

/**
 * Filteri liste testova ({@code GET /test/pretraga}); svako polje je opciono, null = bez filtera.
 * {@code godina} je školska godina (1. 10. godina - 30. 9. godina+1), {@code od}/{@code doDatuma} su uključivi.
 * {@code q} traži u nazivu tipa testa i nazivu predmeta (globalna pretraga, {@code GET /pretraga}).
 */
public record TestFilter(Long predmetId, Long grupaId, Integer godina, Boolean pregledan, Long tipTestaId,
                         LocalDate od, LocalDate doDatuma, String q) {

    /** Bez teksta za pretragu (lista testova). */
    public TestFilter(Long predmetId, Long grupaId, Integer godina, Boolean pregledan, Long tipTestaId,
                      LocalDate od, LocalDate doDatuma) {
        this(predmetId, grupaId, godina, pregledan, tipTestaId, od, doDatuma, null);
    }
}
