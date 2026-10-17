package tri.novica.gfssystem.dto.banka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Odgovor render servisa. Uspeh nosi {@code svg} ili {@code pdf} (base64) prema traženom formatu, neuspeh kompilacije
 * {@code greska} i {@code log}. Nepoznata polja se ignorišu.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenderOdgovor(boolean ok, String svg, String pdf, RenderGreskaInfo greska, String log, Long trajanjeMs) {
}
