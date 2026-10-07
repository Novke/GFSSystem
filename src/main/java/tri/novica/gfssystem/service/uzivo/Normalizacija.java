package tri.novica.gfssystem.service.uzivo;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Normalizacija teksta i brojeva iz odgovora učesnika. Čiste funkcije. */
public final class Normalizacija {
    private static final Map<Character, String> ZAMENE = Map.ofEntries(
        Map.entry('č', "c"), Map.entry('ć', "c"), Map.entry('š', "s"), Map.entry('ž', "z"), Map.entry('đ', "dj"),
        Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
        Map.entry('ђ', "dj"), Map.entry('е', "e"), Map.entry('ж', "z"), Map.entry('з', "z"), Map.entry('и', "i"),
        Map.entry('ј', "j"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('љ', "lj"), Map.entry('м', "m"),
        Map.entry('н', "n"), Map.entry('њ', "nj"), Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"),
        Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('ћ', "c"), Map.entry('у', "u"), Map.entry('ф', "f"),
        Map.entry('х', "h"), Map.entry('ц', "c"), Map.entry('ч', "c"), Map.entry('џ', "dz"), Map.entry('ш', "s"));
    private static final Pattern BROJ = Pattern.compile("[+-]?\\d+([.,]\\d+)?");

    private Normalizacija() {}

    /** Ključ za poređenje: mala slova, ćirilica u latinicu, bez kvačica, razmaci na krajevima i višestruki razmaci uklonjeni. */
    public static String tekst(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            String z = ZAMENE.get(c);
            if (z != null) b.append(z); else b.append(c);
        }
        return b.toString().trim().replaceAll("\\s+", " ");
    }

    /** Ime za prikaz: kontrolni znaci postaju razmaci, razmaci na krajevima i višestruki razmaci uklonjeni. */
    public static String ime(String s) {
        if (s == null) return "";
        return s.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    }

    /** Broj sa tačkom ili zarezom kao decimalnim znakom (razmaci su razdvajači hiljada); {@code null} ako nije broj. */
    public static Double broj(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[\\s\\u00A0\\u202F]", "");
        if (!BROJ.matcher(t).matches()) return null;
        return Double.valueOf(t.replace(',', '.'));
    }
}
