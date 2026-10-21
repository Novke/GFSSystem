package tri.novica.gfssystem.dto.banka;

import java.util.List;

/**
 * Tačan odgovor varijante za jedno polje ({@code Varijanta.odgovori} je JSON objekat {@code kljuc -> OdgovorPolja}).
 * Postavlja se tačno jedno svojstvo, po tipu polja: BROJ {@code broj}, TEKST {@code prihvatljivi}, JEDAN_TACAN
 * {@code tacna} (id opcije), VISE_TACNIH {@code tacne} (ids opcija).
 */
public record OdgovorPolja(Double broj, List<String> prihvatljivi, Long tacna, List<Long> tacne) {
}
