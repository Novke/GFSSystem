package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;
import tri.novica.gfssystem.entity.uzivo.Tema;

import java.time.LocalDateTime;

/**
 * Prezentacija u listi; {@code brojPitanja} je broj slajdova sa rundama (PITANJE i ZADATAK), {@code aktivnoIzvodjenjeId}
 * je izvođenje u toku ili {@code null}.
 */
public record PrezentacijaInfo(Long id, String naziv, String opis, PredmetKratko predmet, long brojSlajdova,
                               long brojPitanja, LocalDateTime izmenjeno, boolean takmicenje,
                               TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, long brojIzvodjenja,
                               Long aktivnoIzvodjenjeId, Tema tema) {
}
