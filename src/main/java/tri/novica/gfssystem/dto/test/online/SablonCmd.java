package tri.novica.gfssystem.dto.test.online;

import java.util.List;
import tri.novica.gfssystem.entity.test.RezimTesta;

/** Komanda šablona testa (plan 4.1). */
public record SablonCmd(Long predmetId, Long tipTestaId, String naziv, String opis, Integer trajanjeMin, RezimTesta rezim, Integer brojGrupa, Integer pragProlaza, List<StavkaSablonaCmd> stavke) {
}
