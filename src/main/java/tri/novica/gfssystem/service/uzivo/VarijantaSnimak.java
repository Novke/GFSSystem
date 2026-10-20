package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;

import java.util.List;
import java.util.Map;

/**
 * Izvučena varijanta u {@link ZadatakSnimak}u: kopija varijante, šeme polja njene grupe i tačnih odgovora u trenutku
 * otvaranja runde. {@code svgId}/{@code resenjeSvgId} su {@code RENDER_SVG} mediji (rešenje može da fali); prazna ili
 * {@code null} {@code polja} znače rad na papiru.
 */
public record VarijantaSnimak(Long varijantaId, String naziv, Long grupaId, String svgId, String resenjeSvgId,
                              List<Polje> polja, Map<String, OdgovorPolja> odgovori) {

    /** Da li varijanta ima polja za odgovor (inače se radi na papiru). */
    public boolean imaPolja() {
        return polja != null && !polja.isEmpty();
    }
}
