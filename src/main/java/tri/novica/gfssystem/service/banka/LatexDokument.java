package tri.novica.gfssystem.service.banka;

/**
 * Samostalan LaTeX dokument za prikaz zadatka na telefonu (spec banke 3.3): {@code standalone} omot širine telefona,
 * preambula predmeta (ili podrazumevana) i tekst varijante. Čiste funkcije, bez baze. Ne proverava sadržaj; zabranjene
 * komande hvata {@link LatexPravila} pre slanja.
 */
public final class LatexDokument {
    /** Omot dokumenta; svaka izmena traži {@code RenderVarijante.RENDER_VERZIJA + 1}, inače se banka ne renderuje ponovo. */
    private static final String OMOT = "\\documentclass[preview,border=6pt,varwidth=84mm,11pt]{standalone}";

    /** Broj linija pre prve linije preambule (samo omot): linija preambule je {@code linijaUDokumentu - POMAK_PREAMBULE}. */
    public static final int POMAK_PREAMBULE = 1;

    /** Preambula kad je predmet nema: srpski tekst, matematika, TikZ za automate i grafove, tabele, liste, slike. */
    public static final String PODRAZUMEVANA_PREAMBULA = String.join("\n",
            "\\usepackage[T1]{fontenc}",
            "\\usepackage[utf8]{inputenc}",
            "\\usepackage{amsmath,amssymb}",
            "\\usepackage{tikz}",
            "\\usetikzlibrary{automata,positioning,arrows.meta}",
            "\\usepackage{graphicx}",
            "\\usepackage{booktabs}",
            "\\usepackage{enumitem}",
            "\\setlength{\\parindent}{0pt}");

    /**
     * Ceo dokument i pomak linija: broj linija pre prve linije tela, pa je linija u tekstu nastavnika
     * {@code linijaUDokumentu - pomakLinija}.
     */
    public record Dokument(String tex, int pomakLinija) {}

    private LatexDokument() {}

    /**
     * Sklapa dokument: omot, {@link #preambulaIli preambula}, {@code \begin{document}}, telo i {@code \end{document}}.
     * Završetke linija (CRLF i samostalni CR) pretvara u LF pre brojanja, jer pdflatex svaki broji kao jednu liniju.
     * Telo se ne menja (ni vodeće ni završne prazne linije), da brojevi linija ostanu tačni; {@code null} je prazno telo.
     */
    public static Dokument zaTelefon(String preambula, String telo) {
        String prefiks = OMOT + "\n" + lf(preambulaIli(preambula)) + "\n\\begin{document}\n";
        int pomak = (int) prefiks.chars().filter(c -> c == '\n').count();
        return new Dokument(prefiks + lf(telo) + "\n\\end{document}\n", pomak);
    }

    /** Broj linija preambule kako ulazi u dokument ({@link #preambulaIli}, CRLF i CR kao LF). */
    public static int brojLinijaPreambule(String preambula) {
        return (int) lf(preambulaIli(preambula)).lines().count();
    }

    /** Zadata preambula sa skinutim razmacima na krajevima, a podrazumevana kad je {@code null} ili prazna. */
    public static String preambulaIli(String preambula) {
        return preambula == null || preambula.isBlank() ? PODRAZUMEVANA_PREAMBULA : preambula.strip();
    }

    private static String lf(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").replace('\r', '\n');
    }
}
