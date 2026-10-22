package tri.novica.gfssystem.utility;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indeks studenta. Broj indeksa se ponavlja iz generacije u generaciju, pa je student jednoznačan tek po paru
 * (kanonski indeks, godina upisa); godina je u {@code studenti.godina}, nikad u samom indeksu.
 * <ul>
 *   <li>{@link #normalizuj}: bez razmaka (i neprekidivih: U+00A0, U+202F, ... iz telefonskih tastatura i copy-paste),
 *       velikim slovima ("gd 12" -> "GD12").</li>
 *   <li>{@link #kanonski}: oblik koji se čuva i poredi, {@code <SMER><broj bez vodećih nula>} ("GD05/2026" uz godinu
 *       2026 -> "GD5"). Sufiks {@code /<godina>} se skida samo kad je jednak godini upisa; indeks koji nije
 *       {@code <slova><cifre>} ostaje samo normalizovan (stari i neobični oblici se ne pogađaju).</li>
 * </ul>
 */
public final class IndeksUtil {
    private static final Pattern RAZMACI = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern SUFIKS_GODINE = Pattern.compile("^(.+)/([0-9]{4})$");
    private static final Pattern SMER_BROJ = Pattern.compile("^([A-Z]{1,4})0*([0-9]+)$");

    /**
     * Smerovi koje javne forme (nalog, upis) nude kao prvi deo indeksa. Za sada samo geodezija; novi smer se dodaje ovde i
     * u frontu ({@code shared/util/indeks.ts}, {@code SMEROVI}), bez druge izmene.
     */
    public static final List<String> SMEROVI = List.of("GD");

    /** Broj indeksa u javnoj formi: 1-3 cifre (prvih devet studenata generacije ima jednocifren broj). */
    public static final int MAX_CIFARA_BROJA = 3;

    /** Indeks iz javne forme u tri dela: smer sa liste i 1-3 cifre, kanonski (bez vodećih nula). */
    private static final Pattern JAVNI_OBLIK = Pattern.compile(
            "^(?:" + String.join("|", SMEROVI) + ")[1-9][0-9]{0," + (MAX_CIFARA_BROJA - 1) + "}$");

    private IndeksUtil() {}

    public static String normalizuj(String indeks) {
        return indeks == null ? null : RAZMACI.matcher(indeks).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /**
     * Kanonski oblik za čuvanje i poređenje (vidi opis klase); {@code godina} sme biti null (tada se sufiks ne skida).
     * Null za null.
     */
    public static String kanonski(String indeks, Integer godina) {
        String n = normalizuj(indeks);
        if (n == null) {
            return null;
        }
        Matcher sufiks = SUFIKS_GODINE.matcher(n);
        if (godina != null && sufiks.matches() && sufiks.group(2).equals(String.valueOf(godina))) {
            n = sufiks.group(1);
        }
        Matcher sb = SMER_BROJ.matcher(n);
        return sb.matches() ? sb.group(1) + sb.group(2) : n;
    }

    /** Da li je kanonski indeks u obliku javne forme ({@link #SMEROVI} + 1-3 cifre, bez vodeće nule). */
    public static boolean jeJavniOblik(String kanonski) {
        return kanonski != null && JAVNI_OBLIK.matcher(kanonski).matches();
    }

    /** Indeks i godina iz starog zahteva sa jednim poljem ({@code "GD5/2026"}); {@code godina} je null bez sufiksa. */
    public record Razdvojen(String indeks, Integer godina) {}

    /**
     * Stari oblik zahteva (jedno polje, keširan SPA posle deploya): sufiks {@code /YYYY} je godina upisa, inače godine nema.
     * PRIVREMENO: ukloniti posle izdanja koje sledi ono sa indeksom u tri dela (videti CLAUDE.md, "Prijava").
     */
    public static Razdvojen razdvoji(String indeks) {
        String n = normalizuj(indeks);
        if (n == null) {
            return new Razdvojen(null, null);
        }
        Matcher sufiks = SUFIKS_GODINE.matcher(n);
        if (sufiks.matches()) {
            return new Razdvojen(sufiks.group(1), Integer.valueOf(sufiks.group(2)));
        }
        return new Razdvojen(n, null);
    }
}
