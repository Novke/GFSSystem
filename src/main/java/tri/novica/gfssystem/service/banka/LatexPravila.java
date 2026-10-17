package tri.novica.gfssystem.service.banka;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deny-lista LaTeX komandi (spec banke 3.2), proverava se pre slanja bilo čega kontejneru za render. Čiste funkcije.
 * Lista je rana pomoć koja daje jasnu poruku nastavniku; prava zaštita je izolacija kontejnera
 * ({@code -no-shell-escape}, {@code openin_any=p}, mreža bez izlaza).
 * <p>
 * Ime komande se poklapa kao cela kontrolna reč: iza {@code \input} ne sme slovo ({@code \inputx} je druga komanda), a
 * {@code \input{x}}, {@code \input x} i {@code \input} na kraju reda jesu zabranjeni. Komentar se ne skida: komanda iza
 * {@code %} je i dalje zabranjena. Isto važi za {@code \\input} (prelom reda pa reč "input"), koji se konzervativno odbija.
 * Vraća se prva komanda po redosledu pravila, ne po položaju u tekstu; {@code \write18} je ispred {@code \write}.
 */
public final class LatexPravila {
    private record Pravilo(String ime, Predicate<String> poklapa) {}

    private static final Pattern INCLUDEGRAPHICS = Pattern.compile("\\\\includegraphics(?![A-Za-z])");
    /** Jedini dozvoljen oblik: samo ime priloga (mala slova, cifre, {@code _}, {@code -}), bez putanje i ekstenzije. */
    private static final Pattern INCLUDEGRAPHICS_DOZVOLJEN =
            Pattern.compile("\\\\includegraphics(\\[[^\\]]*\\])?\\{[a-z0-9_-]+\\}");

    /** Pravila za telo i preambulu. */
    private static final List<Pravilo> ZAJEDNICKA = List.of(
            pravilo("\\write18", "\\\\write\\s*0*18(?!\\d)", 0),
            rec("\\input"), rec("\\include"), rec("\\openin"), rec("\\openout"), rec("\\read"), rec("\\write"),
            rec("\\immediate"), rec("\\csname"), rec("\\directlua"),
            new Pravilo("\\includegraphics", LatexPravila::losIncludegraphics),
            pravilo("\\url{file:", "\\\\url\\s*\\{\\s*file:", Pattern.CASE_INSENSITIVE));

    /** Pravila samo za telo: preambula sme pakete, katkodove i svoj omot. */
    private static final List<Pravilo> SAMO_TELO = List.of(
            rec("\\usepackage"), rec("\\documentclass"),
            pravilo("\\begin{document}", "\\\\begin\\s*\\{\\s*document\\s*\\}", 0),
            pravilo("\\end{document}", "\\\\end\\s*\\{\\s*document\\s*\\}", 0),
            rec("\\catcode"));

    private static final List<Pravilo> TELO = spoji(ZAJEDNICKA, SAMO_TELO);

    private LatexPravila() {}

    /** Ime prve zabranjene komande u telu varijante (npr. {@code \write18}) ili prazno ako je tekst čist. */
    public static Optional<String> zabranjenaKomandaUTelu(String telo) {
        return prva(TELO, telo);
    }

    /** Isto za preambulu predmeta, koja sme pakete, katkodove i omot dokumenta. */
    public static Optional<String> zabranjenaKomandaUPreambuli(String preambula) {
        return prva(ZAJEDNICKA, preambula);
    }

    private static Optional<String> prva(List<Pravilo> pravila, String tekst) {
        if (tekst == null || tekst.isEmpty()) return Optional.empty();
        return pravila.stream().filter(p -> p.poklapa().test(tekst)).map(Pravilo::ime).findFirst();
    }

    /** Cela kontrolna reč: {@code \} + ime, a iza njega ne sme slovo. */
    private static Pravilo rec(String ime) {
        return pravilo(ime, Pattern.quote(ime) + "(?![A-Za-z])", 0);
    }

    private static Pravilo pravilo(String ime, String regex, int flags) {
        Pattern p = Pattern.compile(regex, flags);
        return new Pravilo(ime, s -> p.matcher(s).find());
    }

    /** Svako pojavljivanje {@code \includegraphics} mora biti u dozvoljenom obliku; jedno loše kvari ceo tekst. */
    private static boolean losIncludegraphics(String s) {
        Matcher m = INCLUDEGRAPHICS.matcher(s);
        Matcher dozvoljen = INCLUDEGRAPHICS_DOZVOLJEN.matcher(s);
        while (m.find()) {
            if (!dozvoljen.region(m.start(), s.length()).lookingAt()) return true;
        }
        return false;
    }

    private static List<Pravilo> spoji(List<Pravilo> a, List<Pravilo> b) {
        List<Pravilo> sve = new ArrayList<>(a);
        sve.addAll(b);
        return List.copyOf(sve);
    }
}
