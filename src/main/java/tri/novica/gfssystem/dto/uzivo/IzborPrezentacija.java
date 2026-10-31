package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Odgovor {@code GET /prezentacije/izbor}: sve prezentacije predmeta po redosledu vežbi
 * ({@code RedosledPrezentacija.REDOSLED}) i id-jevi predloženih sledećih (najviše 3, redom predloga).
 */
public record IzborPrezentacija(List<StavkaIzbora> stavke, List<Long> predlog) {
}
