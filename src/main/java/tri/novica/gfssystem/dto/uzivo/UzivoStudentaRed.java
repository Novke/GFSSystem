package tri.novica.gfssystem.dto.uzivo;

import java.time.LocalDate;

/**
 * Jedan čas na kartici studenta ({@code GET /studenti/{id}/uzivo}): predavanje (ili izvođenje bez predavanja,
 * {@code predavanjeId = null}), studentov red u rangu tog časa ({@code stavka.mesto}) i koliko je redova u tom rangu
 * ({@code ukupnoURangu}, i gosti).
 */
public record UzivoStudentaRed(Long predavanjeId, Long izvodjenjeId, LocalDate datum, Long predmetId, String predmet,
                               String naslov, RangTacnostiStavka stavka, int ukupnoURangu) {
}
