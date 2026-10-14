package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

/** Zbirni rezultat jedne runde; popunjeno je samo polje koje odgovara tipu pitanja, ostala su {@code null}. */
public record Rezultat(TipPitanja tip, int ukupno, List<RezultatOpcija> opcije, RezultatBrojevi brojevi,
                       List<RezultatTekst> tekstovi, RezultatSkala skala) {
}
