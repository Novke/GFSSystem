package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import tri.novica.gfssystem.entity.test.VrstaIspravke;

/** Važeća ispravka ključa. */
public record IspravkaTesta(Long id, Long varijantaId, String polje, VrstaIspravke vrsta, String vrednost, LocalDateTime kreirano) {
}
