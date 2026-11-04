package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Slajd posle Klodove izmene; {@code izmenaId} je unos istorije za vraćanje, {@code null} kad se ništa nije promenilo. */
public record IshodIzmeneSlajda(Long prezentacijaId, SlajdDetails slajd, Long izmenaId, List<String> upozorenja) {
}
