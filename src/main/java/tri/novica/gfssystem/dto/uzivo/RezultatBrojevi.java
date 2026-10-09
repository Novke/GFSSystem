package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Rezultat pitanja tipa BROJ; {@code uOdstupanju} je {@code null} kad pitanje nema tačnu vrednost (procena). */
public record RezultatBrojevi(Double medijana, Integer uOdstupanju, List<BrojStavka> najcesce) {
}
