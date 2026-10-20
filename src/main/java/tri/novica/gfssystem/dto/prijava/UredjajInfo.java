package tri.novica.gfssystem.dto.prijava;

import tri.novica.gfssystem.entity.prijava.NacinPrijave;

import java.time.LocalDateTime;

/** Jedan prijavljen uređaj studenta; {@code trenutni} = uređaj sa kog se gleda. */
public record UredjajInfo(Long id, String opis, NacinPrijave nacin, LocalDateTime prviUlaz, LocalDateTime poslednjiUlaz,
                          boolean trenutni) {
}
