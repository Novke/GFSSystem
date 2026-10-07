package tri.novica.gfssystem.utility;

import java.util.Locale;

/** Indeks se svuda poredi i čuva normalizovan: bez razmaka, velikim slovima ("gd 12" -> "GD12"). */
public final class IndeksUtil {
    private IndeksUtil() {}

    public static String normalizuj(String indeks) {
        return indeks == null ? null : indeks.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
