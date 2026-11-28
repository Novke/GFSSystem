package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import java.util.List;
import tri.novica.gfssystem.entity.test.RezimTesta;

/** Šablon sa stavkama ({@code id == null} za proveru bez čuvanja). */
public record SablonDetails(Long id, Long predmetId, Long tipTestaId, String tipTesta, String naziv, int trajanjeMin, RezimTesta rezim, int brojGrupa, int maxPoena, boolean arhiviran, LocalDateTime izmenjeno, String opis, Integer pragProlaza, List<StavkaSablona> stavke, List<String> upozorenja) {
}
