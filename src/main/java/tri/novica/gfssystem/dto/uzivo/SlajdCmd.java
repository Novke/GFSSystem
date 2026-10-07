package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TipSlajda;

/** Slajd (puna zamena); {@code pitanje} važi samo za tip PITANJE. Pravila: {@code validation/SlajdPP}. */
public record SlajdCmd(TipSlajda tip, String naslov, String sadrzaj, String slikaId, String beleske, boolean postepeno,
                       PitanjeCmd pitanje) {
}
