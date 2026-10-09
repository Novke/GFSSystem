package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Raspodela odgovora na skali 1-5 (pet brojeva) i prosek na dve decimale ({@code null} bez odgovora). */
public record RezultatSkala(List<Integer> raspodela, Double prosek) {
}
