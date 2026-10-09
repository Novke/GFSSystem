package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;

import java.time.LocalDateTime;

/** Izvođenje u listi i u stanju; {@code brojPitanja} je broj postavljenih pitanja (rundi). */
public record IzvodjenjeInfo(Long id, PrezentacijaKratko prezentacija, String kod, StatusIzvodjenja status,
                             boolean cuvanje, GrupaKratko grupa, PredavanjeKratko predavanje, LocalDateTime pocetak,
                             LocalDateTime kraj, long brojUcesnika, long brojPitanja) {
}
