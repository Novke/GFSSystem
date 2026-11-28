package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Rezultati online testa. */
public record RezultatiTesta(Long testId, int maxPoena, Integer pragProlaza, List<StavkaRezultata> stavke, List<StudentRezultata> studenti) {
}
