package tri.novica.gfssystem.dto.portal;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;

import java.util.Map;

/** "Prikaži rešenje": SVG rešenja ({@code null} = nije uneto) i tačne vrednosti po polju. */
public record ResenjeVezbanja(String resenjeSvgId, Map<String, OdgovorPolja> tacneVrednosti) {
}
