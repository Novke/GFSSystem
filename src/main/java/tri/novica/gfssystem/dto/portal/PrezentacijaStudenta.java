package tri.novica.gfssystem.dto.portal;

import java.time.LocalDateTime;

/** Prezentacija održana nekoj od grupa studenta (spec 6.1): prvo završeno izvođenje i broj slajdova koje student vidi. */
public record PrezentacijaStudenta(Long id, String naziv, LocalDateTime odrzana, int slajdova) {
}
