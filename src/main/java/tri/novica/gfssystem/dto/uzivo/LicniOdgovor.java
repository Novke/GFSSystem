package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Odgovor učesnika u trenutnoj rundi: {@code tacno} i {@code poeni} tek kad je tačan odgovor prikazan. {@code izabrane}:
 * sopstvene izabrane opcije (rastuće po id-ju) čim je odgovor primljen, jer ih je učesnik sam poslao ({@code null} bez
 * odgovora i za pitanje bez opcija). {@code udeo} (0..1, delimično tačno kod "više tačnih", ostali tipovi 1/0) samo uz
 * vidljiv {@code tacno}, inače {@code null}: pre TACAN ništa ne odaje tačnost ni broj tačnih opcija.
 */
public record LicniOdgovor(Long rundaId, boolean primljen, Boolean tacno, Integer poeni, List<Long> izabrane,
                           Double udeo) {
}
