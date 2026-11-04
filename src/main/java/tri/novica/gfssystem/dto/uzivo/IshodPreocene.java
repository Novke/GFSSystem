package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/** Ishod preocene: id-jevi odgovora kojima se ocena promenila i ukupno tačnih u rundi posle toga. */
public record IshodPreocene(List<Long> izmenjeni, long brojTacnih, long brojOdgovora) {
}
