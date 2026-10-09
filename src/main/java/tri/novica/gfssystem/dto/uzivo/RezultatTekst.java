package tri.novica.gfssystem.dto.uzivo;

/** Grupa istih (normalizovanih) kratkih odgovora; {@code tekst} je najčešći originalni oblik. */
public record RezultatTekst(String kljuc, String tekst, int broj, boolean sakriven, Boolean tacan) {
}
