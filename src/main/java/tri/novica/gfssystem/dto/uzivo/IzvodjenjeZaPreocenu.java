package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;

import java.time.LocalDateTime;
import java.util.List;

/** Izvođenje prezentacije sa rundama, najnovije prvo; bez sadržaja pitanja i bez imena učesnika. */
public record IzvodjenjeZaPreocenu(Long id, StatusIzvodjenja status, LocalDateTime pocetak, String grupa,
                                   List<RundaZaPreocenu> runde) {
}
