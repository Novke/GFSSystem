package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDate;

/** Objavljen rezultat testa na nalogu. */
public record RezultatTestaKratko(Long testId, LocalDate datum, String tipTesta, Double poeni, int maxPoena, Boolean polozio, boolean racunaSe, boolean online) {
}
