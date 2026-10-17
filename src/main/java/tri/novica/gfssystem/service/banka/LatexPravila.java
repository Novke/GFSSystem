package tri.novica.gfssystem.service.banka;

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
 * {@code \input{x}}, {@code \input x} i {@code \input} na kraju reda jesu zabranjeni. Imena sa {@code @} ({@code \@input},
 * {@code \input@path}) su cela imena sa {@code @} kao slovom: {@code \@inputx} i {@code \@input@x} su druge komande. Komentar
 * se ne skida: komanda iza {@code %} je i dalje zabranjena. Isto važi za {@code \\input} (prelom reda pa reč "input"), koji se
 * konzervativno odbija. Vraća se prva komanda po redosledu pravila, ne po položaju u tekstu; {@code \write18} je ispred
 * {@code \write}, a {@code \input@path} ispred {@code \input} (koji hvata i njega bez imena).
 * <p>
 * PDF prilog ide samo kroz {@code \includegraphics}; {@code \includepdf} i {@code \includepdfmerge} (pdfpages) su zabranjeni
 * i sa imenom priloga.
 * <p>
 * Lista ne zaustavlja namerno zamaskiranje komande (promenjeni katkodovi i slično); zato je zabranjen bar niz {@code ^^}
 * ({@code \inp^^75t} je {@code \input}), ali prava granica je kontejner.
 */
public final class LatexPravila {
    /** {@code samoTelo}: pravilo važi za telo varijante, ali ne i za preambulu predmeta. */
    private record Pravilo(String ime, Predicate<String> poklapa, boolean samoTelo) {}

    private static final Pattern INCLUDEGRAPHICS = Pattern.compile("\\\\includegraphics(?![A-Za-z])");
    /** Jedini dozvoljen oblik: samo ime priloga (mala slova, cifre, {@code _}, {@code -}), bez putanje i ekstenzije. */
    private static final Pattern INCLUDEGRAPHICS_DOZVOLJEN =
            Pattern.compile("\\\\includegraphics(\\[[^\\]]*\\])?\\{[a-z0-9_-]+\\}");

    /**
     * Pravila redom kojim se prijavljuju. Preambula sme pakete (učitavanje paketa), katkodove i svoj
     * omot; to su pravila samo za telo. Ime {@code \input@path} ima pravilo samo za telo, ali ga u preambuli hvata {@code \input}.
     */
    private static final List<Pravilo> PRAVILA = List.of(
            pravilo("\\write18", "\\\\write\\s*0*18(?!\\d)", 0, false),
            recSaAt("\\input@path", true),
            rec("\\input", false), rec("\\include", false), rec("\\openin", false), rec("\\openout", false),
            rec("\\read", false), rec("\\write", false), rec("\\immediate", false), rec("\\csname", false),
            rec("\\directlua", false),
            rec("\\InputIfFileExists", false), recSaAt("\\@input", false), recSaAt("\\@@input", false),
            recSaAt("\\@iinput", false),
            rec("\\lstinputlisting", false), rec("\\verbatiminput", false),
            rec("\\includepdf", false), rec("\\includepdfmerge", false),
            new Pravilo("\\includegraphics", LatexPravila::losIncludegraphics, false),
            pravilo("\\url{file:", "\\\\url\\s*\\{\\s*file:", Pattern.CASE_INSENSITIVE, false),
            pravilo("\\href{file:", "\\\\href\\s*\\{\\s*file:", Pattern.CASE_INSENSITIVE, false),
            pravilo("^^", Pattern.quote("^^"), 0, false),
            rec("\\usepackage", true), rec("\\RequirePackage", true), rec("\\documentclass", true),
            pravilo("\\begin{document}", "\\\\begin\\s*\\{\\s*document\\s*\\}", 0, true),
            pravilo("\\end{document}", "\\\\end\\s*\\{\\s*document\\s*\\}", 0, true),
            rec("\\catcode", true));

    private static final List<Pravilo> PRAVILA_PREAMBULE = PRAVILA.stream().filter(p -> !p.samoTelo()).toList();

    private LatexPravila() {}

    /** Ime prve zabranjene komande u telu varijante (npr. {@code \write18}) ili prazno ako je tekst čist. */
    public static Optional<String> zabranjenaKomandaUTelu(String telo) {
        return prva(PRAVILA, telo);
    }

    /** Isto za preambulu predmeta, koja sme pakete, katkodove i omot dokumenta. */
    public static Optional<String> zabranjenaKomandaUPreambuli(String preambula) {
        return prva(PRAVILA_PREAMBULE, preambula);
    }

    private static Optional<String> prva(List<Pravilo> pravila, String tekst) {
        if (tekst == null || tekst.isEmpty()) return Optional.empty();
        return pravila.stream().filter(p -> p.poklapa().test(tekst)).map(Pravilo::ime).findFirst();
    }

    /** Cela kontrolna reč: {@code \} + ime, a iza njega ne sme slovo. */
    private static Pravilo rec(String ime, boolean samoTelo) {
        return pravilo(ime, Pattern.quote(ime) + "(?![A-Za-z])", 0, samoTelo);
    }

    /** Ime koje sadrži {@code @}: kontrolno ime čine slova i {@code @}, pa iza njega ne sme ni jedno od njih. */
    private static Pravilo recSaAt(String ime, boolean samoTelo) {
        return pravilo(ime, Pattern.quote(ime) + "(?![A-Za-z@])", 0, samoTelo);
    }

    private static Pravilo pravilo(String ime, String regex, int flags, boolean samoTelo) {
        Pattern p = Pattern.compile(regex, flags);
        return new Pravilo(ime, s -> p.matcher(s).find(), samoTelo);
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
}
