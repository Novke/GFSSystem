package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import java.util.List;
import tri.novica.gfssystem.entity.TestGrupa;

/** Primerak jednog studenta za pregled. */
public record PrimerakNastavnika(Long primerakId, Long studentId, String ime, String prezime, String indeks, TestGrupa grupaTesta, StanjePrimerkaKonzole stanje, LocalDateTime usao, LocalDateTime predato, String nacinPredaje, Double poeni, double maxPoena, List<ZadatakPrimerka> zadaci, List<DogadjajTesta> dogadjaji) {
}
