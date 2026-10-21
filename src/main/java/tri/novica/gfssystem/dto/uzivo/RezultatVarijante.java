package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Rezultat jedne izvučene varijante (redom snimka). {@code tacnih} je {@code null} dok rezultati i tačan odgovor nisu
 * prikazani, {@code resenjeSvgId} dok rešenje nije uključeno na zatvorenoj rundi.
 */
public record RezultatVarijante(Long varijantaId, String naziv, String svgId, String resenjeSvgId, int dodeljeno,
                                int odgovorilo, Integer tacnih, List<RezultatPolja> polja) {
}
