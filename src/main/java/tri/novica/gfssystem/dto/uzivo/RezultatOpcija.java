package tri.novica.gfssystem.dto.uzivo;

/** Broj glasova za jednu opciju; {@code tacna} je {@code null} dok tačan odgovor nije prikazan. */
public record RezultatOpcija(Long id, String tekst, int broj, Boolean tacna) {
}
