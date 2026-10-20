package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Rezultat runde zadatka po varijanti. {@code odgovoriPrikazani} false: brojevi su 0, a vrednosti i broj tačnih
 * {@code null} (projektor tada prikazuje samo tačno ili rešenje).
 */
public record ZadatakRezultat(int ukupno, List<RezultatVarijante> varijante, boolean odgovoriPrikazani) {
}
