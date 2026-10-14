package tri.novica.gfssystem.dto.uzivo;

import java.time.LocalDate;

/** Predavanje ponuđeno u dijalogu "Pokreni" (izvođenje vezano za predavanje i njegovu grupu). */
public record PredavanjeZaPokretanjeInfo(Long id, int rb, LocalDate datum, String tema, Boolean zavrseno,
                                         GrupaKratko grupa) {
}
