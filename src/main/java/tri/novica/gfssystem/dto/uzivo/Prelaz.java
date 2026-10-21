package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.SmerPrelaza;
import tri.novica.gfssystem.entity.uzivo.TipPrelaza;

/** Prelaz na slajd; {@code null} u SlajdCmd/SlajdDetails = bez prelaza. Pravila: {@code SlajdPP.normalizuj(Prelaz)}. */
public record Prelaz(TipPrelaza efekat, SmerPrelaza smer, Integer trajanjeMs) {
}
