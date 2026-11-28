package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDate;
import java.util.List;
import tri.novica.gfssystem.entity.test.RezimTesta;

/** Komanda „Generiši“ (plan 4.2). */
public record NoviOnlineTestCmd(Long sablonId, Long grupaId, Long tipTestaId, LocalDate datum, Integer trajanjeMin, RezimTesta rezim, Integer brojGrupa, List<Long> studentIds) {
}
