package tri.novica.gfssystem.dto.test.online;

import java.util.List;
import java.util.Map;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;

/** Izvučen zadatak u pregledu pripreme (sa rešenjem i ključem, samo nastavniku). */
public record ZadatakIzbora(Long stavkaId, int mesto, Long varijantaId, Long tipId, String tip, int nivo, String svgId, String resenjeSvgId, List<Polje> polja, Map<String, OdgovorPolja> odgovori, double maxPoena, int studenata) {
}
