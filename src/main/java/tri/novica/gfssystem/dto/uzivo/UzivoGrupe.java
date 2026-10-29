package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Rang studenata grupe ({@code GET /grupe/{id}/uzivo}): samo studenti grupe, rangirani; studenti bez ijednog dugovanog
 * pitanja na dnu sa {@code mesto = null}. {@code brojCasova}: koliko časova je ušlo u zbir.
 */
public record UzivoGrupe(Long grupaId, int brojCasova, List<RangTacnostiStavka> rang) {
}
