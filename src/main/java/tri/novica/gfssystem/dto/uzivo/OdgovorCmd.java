package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Odgovor učesnika na rundu (STOMP {@code /app/izvodjenja/{id}/odgovor}). Koristi se samo polje koje odgovara tipu
 * pitanja; {@code broj} je tekst (prima i decimalni zarez, "3,5").
 */
public record OdgovorCmd(Long rundaId, List<Long> opcije, String broj, String tekst, Integer skala) {
}
