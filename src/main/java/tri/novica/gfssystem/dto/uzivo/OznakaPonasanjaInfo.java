package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.VrstaOznake;

import java.time.LocalDateTime;

/**
 * Oznaka ponašanja na kartonu studenta (samo nastavnik): vrsta, sakriveni tekst, vreme, predmet, predavanje, izvođenje i
 * naziv prezentacije zapamćen uz oznaku (ostaje i kad se izvođenje obriše; id-jevi tada postaju null).
 */
public record OznakaPonasanjaInfo(Long id, VrstaOznake vrsta, String tekst, LocalDateTime kreirano, PredmetKratko predmet,
                                  Long predavanjeId, Long izvodjenjeId, String prezentacija) {
}
