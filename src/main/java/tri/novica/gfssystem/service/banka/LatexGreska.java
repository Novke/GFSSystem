package tri.novica.gfssystem.service.banka;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Greška iz loga {@code pdflatex -file-line-error} svedena na liniju u tekstu nastavnika i kratku poruku (spec banke 3.1).
 * Čiste funkcije.
 */
public final class LatexGreska {
    private static final int NAJVISE_ZNAKOVA = 300;
    private static final Pattern LINIJA = Pattern.compile("^(?:\\./)?dokument\\.tex:(\\d+): (.*)$", Pattern.MULTILINE);
    private static final Pattern UZVICNIK = Pattern.compile("^! (.*)$", Pattern.MULTILINE);

    /** {@code linija} je relativna na telo (od 1) ili {@code null} kad se ne zna. */
    public record Greska(Integer linija, String poruka) {}

    private LatexGreska() {}

    /**
     * Prvi red oblika {@code ./dokument.tex:12: poruka}: linija je {@code 12 - pomakLinija} (najmanje 1, pa greška u
     * preambuli ili omotu pada na prvu liniju tela). Bez takvog reda poruka je prvi red koji počinje sa {@code ! }, bez
     * linije; bez njega prazno. Poruka se skraćuje na 300 kodnih tačaka.
     *
     * @param pomakLinija {@link LatexDokument.Dokument#pomakLinija()} dokumenta koji je preveden
     */
    public static Optional<Greska> izLoga(String log, int pomakLinija) {
        if (log == null) return Optional.empty();
        Matcher m = LINIJA.matcher(log);
        if (m.find()) {
            String poruka = skrati(m.group(2));
            try {
                return Optional.of(new Greska(Math.max(1, Integer.parseInt(m.group(1)) - pomakLinija), poruka));
            } catch (NumberFormatException e) {
                return Optional.of(new Greska(null, poruka));
            }
        }
        Matcher u = UZVICNIK.matcher(log);
        return u.find() ? Optional.of(new Greska(null, skrati(u.group(1)))) : Optional.empty();
    }

    private static String skrati(String s) {
        String t = s.strip();
        return t.codePointCount(0, t.length()) <= NAJVISE_ZNAKOVA ? t : t.substring(0, t.offsetByCodePoints(0, NAJVISE_ZNAKOVA));
    }
}
