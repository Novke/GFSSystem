package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import tri.novica.gfssystem.entity.test.RezimTesta;

/** Online test za stranu pripreme i toka (plan 4.2). */
public record OnlineTest(Long testId, String tipTesta, Long predmetId, String predmet, Long grupaId, String grupa, LocalDate datum, StanjeOnline stanje, RezimTesta rezim, int brojGrupa, int trajanjeMin, int maxPoena, Integer pragProlaza, boolean pregledan, LocalDateTime objavljeno, boolean objavaTacni, Long sablonId, SmenaTesta smena, long serverVremeMs, List<StavkaTesta> stavke, List<GrupaIzbora> izbor, int studenti, List<String> upozorenja) {
}
