package tri.novica.gfssystem.service.uzivo;

import java.util.regex.Pattern;

/** Pomoćne funkcije za Markdown sadržaj slajda. */
public final class Markdown {
    private static final Pattern STAVKA = Pattern.compile("^(?:[-*+]|\\d+[.)]) .*");

    private Markdown() {}

    /** Broj stavki liste: linije koje počinju (bez uvlačenja) sa {@code "- "}, {@code "* "}, {@code "+ "} ili {@code "1. "}/{@code "1) "}. */
    public static int brojStavki(String sadrzaj) {
        if (sadrzaj == null || sadrzaj.isEmpty()) return 0;
        int n = 0;
        for (String linija : sadrzaj.split("\\R")) {
            if (STAVKA.matcher(linija).matches()) n++;
        }
        return n;
    }
}
