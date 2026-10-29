package tri.novica.gfssystem.dto.raspored;

import java.util.List;

/** Naredni termini studenta (najviše 4 po stavci), po datumu pa početku. */
public record NaredniCasovi(List<StudentskiTermin> termini) {
}
