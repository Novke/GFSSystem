package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Zadatak primerka za studenta: tekst (SVG), polja i sačuvan unos; nikad ključ ni rešenje. */
public record ZadatakTesta(Long zadatakId, int rb, String naziv, double maxPoena, String svgId, boolean rucno, List<PoljeTesta> polja, Map<String, Object> uneto, int rev, LocalDateTime sacuvano) {
}
