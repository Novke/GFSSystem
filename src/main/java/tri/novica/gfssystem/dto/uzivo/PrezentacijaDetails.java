package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;

import java.time.LocalDateTime;
import java.util.List;

/** Polja iz {@link PrezentacijaInfo} i slajdovi po {@code rb}. */
public record PrezentacijaDetails(Long id, String naziv, String opis, PredmetKratko predmet, long brojSlajdova,
                                  long brojPitanja, LocalDateTime izmenjeno, boolean takmicenje,
                                  TelefonPrikaz telefonPrikaz, boolean detaljiDozvoljeni, long brojIzvodjenja,
                                  Long aktivnoIzvodjenjeId, List<SlajdDetails> slajdovi) {
}
