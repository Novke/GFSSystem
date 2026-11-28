package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDate;
import java.util.List;

/** Rezultat jednog testa za studenta (prazni zadaci za test na papiru). */
public record RezultatTestaStudenta(Long testId, String tipTesta, String predmet, LocalDate datum, Double poeni, int maxPoena, Boolean polozio, boolean tacniPrikazani, List<ZadatakRezultataStudenta> zadaci) {
}
