package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Rezultat pitanja tipa BROJ: sve različite unete vrednosti (tačno ista vrednost je jedna stavka, bez zaokruživanja),
 * najčešće prve, pa po vrednosti rastuće. Broj tačnih je u {@link Rezultat#tacnih}. Medijane više nema (uneti odgovori su
 * čipovi kao na Zadatak slajdu).
 */
public record RezultatBrojevi(List<BrojStavka> vrednosti) {
}
