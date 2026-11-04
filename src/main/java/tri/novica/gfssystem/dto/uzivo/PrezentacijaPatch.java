package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Tema;
import tri.novica.gfssystem.utility.Promena;

/** Klodova delimična izmena prezentacije ({@code izmeni_prezentaciju}): odsutno polje ostaje; {@code null} briše opis i vežbu. */
public record PrezentacijaPatch(Promena<String> naziv, Promena<String> opis, Promena<Tema> tema, Promena<Integer> vezba) {
}
