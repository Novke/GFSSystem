package tri.novica.gfssystem.dto.uzivo;

import java.util.List;
import java.util.Map;

/**
 * Odgovor učesnika na rundu (STOMP {@code /app/izvodjenja/{id}/odgovor}). Za pitanje se koristi samo polje koje odgovara
 * tipu pitanja; {@code broj} je tekst (prima i decimalni zarez, "3,5"). Za zadatak važi samo {@code polja}
 * ({@code kljuc -> vrednost}: string za BROJ i TEKST, lista id-jeva opcija za JEDAN_TACAN i VISE_TACNIH); varijantu
 * server uzima iz dodele, ne iz poruke.
 */
public record OdgovorCmd(Long rundaId, List<Long> opcije, String broj, String tekst, Integer skala,
                         Map<String, Object> polja) {

    public OdgovorCmd(Long rundaId, List<Long> opcije, String broj, String tekst, Integer skala) {
        this(rundaId, opcije, broj, tekst, skala, null);
    }
}
