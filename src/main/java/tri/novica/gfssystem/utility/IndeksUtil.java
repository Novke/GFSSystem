package tri.novica.gfssystem.utility;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Indeks se svuda poredi i čuva normalizovan: bez razmaka, velikim slovima ("gd 12" -> "GD12").
 * Uklanjaju se i neprekidivi razmaci (U+00A0, U+202F, ...) koje ubacuju telefonske tastature i copy-paste.
 */
public final class IndeksUtil {
    private static final Pattern RAZMACI = Pattern.compile("[\\s\\p{Z}]+");

    private IndeksUtil() {}

    public static String normalizuj(String indeks) {
        return indeks == null ? null : RAZMACI.matcher(indeks).replaceAll("").toUpperCase(Locale.ROOT);
    }
}
