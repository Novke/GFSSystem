package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Konzola testa (front čita na 3 s). */
public record KonzolaTesta(Long testId, StanjeOnline stanje, SmenaTesta smena, long serverVremeMs, int maxPoena, int usli, int predalo, int ukupno, List<StudentKonzole> studenti) {
}
