package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDate;

/** Test na nalogu studenta (otvoren za ulaz ili započet, nije završen). */
public record TestNaNalogu(Long testId, String naziv, String predmet, LocalDate datum, int trajanjeMin, FazaTesta faza, boolean trebaKod) {
}
