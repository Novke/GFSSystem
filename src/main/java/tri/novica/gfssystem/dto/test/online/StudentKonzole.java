package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import tri.novica.gfssystem.entity.TestGrupa;

/** Red konzole testa. */
public record StudentKonzole(Long primerakId, Long studentId, String ime, String prezime, String indeks, TestGrupa grupaTesta, StanjePrimerkaKonzole stanje, int popunjeno, int ukupnoPolja, Long rokMs, int produzenjeMin, LocalDateTime predato, boolean racunar, int promenaUredjaja, int napustanja, int napustanjaSekundi, boolean drugaMreza, boolean prepisivao, Double poeni) {
}
