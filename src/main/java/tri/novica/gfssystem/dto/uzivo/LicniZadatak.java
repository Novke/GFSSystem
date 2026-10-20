package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.entity.uzivo.Faza;

import java.util.List;
import java.util.Map;

/**
 * Zadatak jednog učesnika: samo njegova dodeljena varijanta, nikad druga. {@code ishod} (po polju) i {@code tacno} u
 * {@code ZATVORENO} kad je odgovorio; {@code tacneVrednosti} samo uz prikazan tačan odgovor, {@code resenjeSvgId} samo uz
 * rešenje uključeno na zatvorenoj rundi.
 */
public record LicniZadatak(Long rundaId, Long varijantaId, String svgId, List<Polje> polja, Faza faza, boolean primljen,
                           Map<String, Boolean> ishod, Boolean tacno, Map<String, OdgovorPolja> tacneVrednosti,
                           String resenjeSvgId) {
}
