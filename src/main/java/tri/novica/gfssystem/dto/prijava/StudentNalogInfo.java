package tri.novica.gfssystem.dto.prijava;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sekcija "Nalog" na kartici studenta (nastavnik, spec 5.3): da li student ima mejl (bez same adrese), važeći uređaji,
 * Moodle veza i poslednja prijava (bilo kog uređaja, i opozvanog). Bez tokena, heševa i Moodle identifikatora.
 */
public record StudentNalogInfo(boolean imaMejl, List<UredjajInfo> uredjaji, MoodleVezaInfo moodle,
                               LocalDateTime poslednjaPrijava) {
}
