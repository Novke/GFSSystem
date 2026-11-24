package tri.novica.gfssystem.repository.uzivo;

import tri.novica.gfssystem.entity.uzivo.PozadinaCrteza;

import java.time.LocalDateTime;

/** Red upita "ranije" ({@link CrtezIzvodjenjaRepository#findRanije}): strana drugog izvođenja sa početkom i grupom. */
public record RanijiCrtezRed(Long izvodjenjeId, LocalDateTime pocetak, String grupa, PozadinaCrteza pozadina, String elementi) {
}
