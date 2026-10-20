package tri.novica.gfssystem.service.uzivo;

/**
 * Odgovor učesnika na jedno polje zadatka, kako se čuva u {@code odgovori.polja} (JSON {@code kljuc -> PoljeOdgovora}).
 * {@code vrednost} je {@code String} za BROJ i TEKST (kako je student uneo, bez razmaka na krajevima) i sortirana
 * {@code List<Long>} id-jeva opcija za JEDAN_TACAN i VISE_TACNIH; {@code tacno} je ocena polja po snimku runde.
 */
public record PoljeOdgovora(Object vrednost, boolean tacno) {
}
