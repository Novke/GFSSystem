package tri.novica.gfssystem.dto.test.online;

import java.util.Map;

/** Čuvanje jednog zadatka: redni broj izmene koju klijent menja i ceo unos zadatka. */
public record CuvanjeCmd(Integer rev, Map<String, Object> uneto) {
}
