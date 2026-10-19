package tri.novica.gfssystem.dto.prijava;

import java.time.LocalDateTime;

/** Jedna nastavnička sesija (uređaj) za spisak "Prijavljeni uređaji"; {@code trenutna} = uređaj sa kog se gleda. */
public record SesijaInfo(Long id, String opis, LocalDateTime kreirano, LocalDateTime poslednjiUlaz, boolean trenutna) {
}
