package tri.novica.gfssystem.dto.portal;

import java.util.Map;

/** Odgovori za proveru: {@code kljuc -> vrednost} (string za BROJ i TEKST, lista id-jeva opcija za izbor), kao na času. */
public record ProveraCmd(Map<String, Object> polja) {
}
