package tri.novica.gfssystem.service.uzivo;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /**
     * Ime za prikaz: znaci formata ({@code \\p{Cf}}: bidi preokretanje, nevidljivi znaci nulte širine) se brišu,
     * kontrolni znaci (C0 i C1, U+0080-U+009F: Javin {@code \\p{Cntrl}} je samo ASCII) postaju razmaci, svi Unicode
     * razmaci postaju jedan običan, a razmaci na krajevima se uklanjaju.
     */
    public static String ime(String s) {
        if (s == null) return "";
        return s.replaceAll("\\p{Cf}", "").replaceAll("[\\p{Cntrl}\\x{80}-\\x{9F}]", " ")
                .replaceAll("[\\s\\p{Z}]+", " ").strip();
    }

    /** Prazni znaci koji nisu razmaci po Unicode kategoriji (hangul i brajevi "prazni" znaci). */
    private static final Set<Integer> PRAZNI = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

    /**
     * Da li ime ima bar jedan vidljiv znak: kodnu tačku kategorije slovo (L), broj (N), interpunkcija (P) ili simbol (S)
     * van {@link #PRAZNI}. Spisak dozvoljenih, ne zabranjenih: samo razmaci, kombinujući znaci (U+034F, selektori
     * varijanti), kontrolni i neraspoređeni znaci nisu ime.
     */
    public static boolean imaVidljivZnak(String ime) {
        return ime != null && ime.codePoints().anyMatch(cp -> vidljiv(cp) && !PRAZNI.contains(cp));
    }

    private static boolean vidljiv(int cp) {
        return switch (Character.getType(cp)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                 Character.MODIFIER_LETTER, Character.OTHER_LETTER,
                 Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
                 Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION,
                 Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    /** Broj sa tačkom ili zarezom kao decimalnim znakom (razmaci su razdvajači hiljada); {@code null} ako nije broj. */
    public static Double broj(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[\\s\\u00A0\\u202F]", "");
        if (!BROJ.matcher(t).matches()) return null;
        return Double.valueOf(t.replace(',', '.'));
    }
}
