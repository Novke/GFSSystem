package tri.novica.gfssystem.dto.test.online;

/** Ručni poeni zadatka (0..maxPoena, 2 decimale; {@code null} briše ručnu vrednost). */
public record RucniPoeniCmd(Double poeni, String napomena) {
}
