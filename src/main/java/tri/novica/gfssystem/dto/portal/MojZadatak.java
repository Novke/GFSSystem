package tri.novica.gfssystem.dto.portal;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sopstveni zadatak sa časa (spec 5.5), samo iz završenih izvođenja, sve iz snimka runde (šta je student stvarno dobio):
 * SVG zadatka i rešenja, šema polja, šta je uneo po polju ({@code uneto}, tekst za prikaz), ishod po polju i ukupno
 * ({@code null} bez odgovora ili za rad na papiru), tačne vrednosti. {@code tipId} je list varijante, a
 * {@code vezbanjeOtvoreno} da li ga grupa studenta sme da vežba.
 */
public record MojZadatak(Long rundaId, LocalDateTime vreme, Long prezentacijaId, String prezentacija, Long slajdId,
                         String slajd, String tip, String svgId, String resenjeSvgId, List<Polje> polja,
                         Map<String, String> uneto, Map<String, Boolean> ishod, Boolean tacno,
                         Map<String, OdgovorPolja> tacneVrednosti, Long tipId, boolean vezbanjeOtvoreno) {
}
