package tri.novica.gfssystem.dto.portal;

import java.util.Map;

/** Ishod provere: ceo zadatak i tačno/netačno po polju, bez tačnih vrednosti. */
public record ProveraVezbanja(boolean tacno, Map<String, Boolean> ishod) {
}
