package tri.novica.gfssystem.dto.cas;

import java.time.LocalDate;

/** Predavanje na kome je student čekiran, za telefon (bez grupe, napomena i ičega nastavničkog). */
public record CasPredavanje(Long id, String predmet, LocalDate datum, int rb, String tema, boolean zavrseno) {
}
