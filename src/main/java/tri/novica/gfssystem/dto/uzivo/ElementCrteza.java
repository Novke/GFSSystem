package tri.novica.gfssystem.dto.uzivo;

import java.util.List;

/**
 * Jedan element crteža u jedinicama scene 960 x 540 (spec crtanja 4.1): boja {@code #RRGGBB}, debljina 1-40, tačke
 * {@code x0, y0, x1, y1, ...} (OLOVKA 1-5000 tačaka, ostali tipovi tačno 4 broja). Proverava {@code CrtanjePravila}.
 */
public record ElementCrteza(TipElementa tip, String boja, Double debljina, List<Double> tacke) {
}
