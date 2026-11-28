package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import java.util.List;
import tri.novica.gfssystem.entity.TestGrupa;

/** Stanje polaganja za studenta; {@code zadaci == null} pre U_TOKU. */
public record PolaganjeStudenta(Long testId, String naziv, String predmet, FazaTesta faza, String ime, String prezime, String indeks, TestGrupa grupaTesta, Long rokMs, long serverVremeMs, int maxPoena, List<ZadatakTesta> zadaci, LocalDateTime predato, boolean racunar) {
}
