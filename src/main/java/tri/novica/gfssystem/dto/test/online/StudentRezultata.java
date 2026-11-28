package tri.novica.gfssystem.dto.test.online;

import java.util.List;
import tri.novica.gfssystem.entity.TestGrupa;

/** Red tabele rezultata. */
public record StudentRezultata(Long primerakId, Long studentId, String ime, String prezime, String indeks, TestGrupa grupaTesta, StanjePrimerkaKonzole stanje, Double poeni, List<ZadatakRezultata> zadaci, boolean prepisivao, List<String> znaci) {
}
