package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import tri.novica.gfssystem.entity.test.RezimTesta;

/** Šablon u spisku; {@code maxPoena} = Σ poeni × broj. */
public record SablonInfo(Long id, Long predmetId, Long tipTestaId, String tipTesta, String naziv, int trajanjeMin, RezimTesta rezim, int brojGrupa, int maxPoena, int brojStavki, boolean arhiviran, LocalDateTime izmenjeno) {
}
