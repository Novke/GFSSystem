package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;

import java.time.LocalDateTime;

/**
 * Izvođenje u listi i u stanju; {@code brojPitanja} je broj postavljenih pitanja (rundi), {@code boduje} = bodovan run
 * (prijava studenata obavezna, aktivnosti se upisuju).
 */
public record IzvodjenjeInfo(Long id, PrezentacijaKratko prezentacija, String kod, StatusIzvodjenja status,
                             boolean cuvanje, GrupaKratko grupa, PredavanjeKratko predavanje, LocalDateTime pocetak,
                             LocalDateTime kraj, long brojUcesnika, long brojPitanja, boolean boduje) {

    /** Nebodovan run (oblik pre bodovanja). */
    public IzvodjenjeInfo(Long id, PrezentacijaKratko prezentacija, String kod, StatusIzvodjenja status,
                          boolean cuvanje, GrupaKratko grupa, PredavanjeKratko predavanje, LocalDateTime pocetak,
                          LocalDateTime kraj, long brojUcesnika, long brojPitanja) {
        this(id, prezentacija, kod, status, cuvanje, grupa, predavanje, pocetak, kraj, brojUcesnika, brojPitanja, false);
    }
}
