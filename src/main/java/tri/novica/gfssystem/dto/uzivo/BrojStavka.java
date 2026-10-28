package tri.novica.gfssystem.dto.uzivo;

/**
 * Vrednost i koliko puta je uneta; {@code tacan} (u dozvoljenom odstupanju, {@code Ocenjivac.uOdstupanju}) je {@code null}
 * kad pitanje nema tačnu vrednost (procena) ili tačnost još nije vidljiva.
 */
public record BrojStavka(double vrednost, int broj, Boolean tacan) {
}
