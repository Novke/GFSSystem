package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LatexDokumentTest {

    private static List<String> linije(String tex) {
        return Arrays.asList(tex.split("\n", -1));
    }

    @Test
    void pomakJeBrojLinijaPrePrveLinijeTela() {
        // documentclass (1), preambula (2 linije), \begin{document}: telo počinje na liniji 5, pomak 4
        LatexDokument.Dokument d = LatexDokument.zaTelefon("\\usepackage{a}\n\\usepackage{b}", "PRVA\nDRUGA");

        assertThat(d.pomakLinija()).isEqualTo(4);
        assertThat(linije(d.tex()).get(d.pomakLinija())).isEqualTo("PRVA");
        assertThat(linije(d.tex()).get(d.pomakLinija() + 1)).isEqualTo("DRUGA");
    }

    @Test
    void pomakSePodudaraSaStvarnimPolozajemTelaZaPreambuleRazneDuzine() {
        for (int n = 1; n <= 6; n++) {
            String preambula = String.join("\n", java.util.Collections.nCopies(n, "\\relax"));
            LatexDokument.Dokument d = LatexDokument.zaTelefon(preambula, "TELO");

            assertThat(d.pomakLinija()).as("preambula od %d linija", n).isEqualTo(n + 2);
            assertThat(linije(d.tex()).get(d.pomakLinija())).as("preambula od %d linija", n).isEqualTo("TELO");
        }
    }

    @Test
    void preambulaPocinjePosleOmotaIBrojLinijaJeKaoUDokumentu() {
        // probni render preambule svodi liniju greške na liniju preambule: linija u dokumentu - POMAK_PREAMBULE
        String preambula = "\\usepackage{a}\r\n\\usepackage{b}\n\\relax";
        LatexDokument.Dokument d = LatexDokument.zaTelefon(preambula, "x");

        assertThat(linije(d.tex()).get(LatexDokument.POMAK_PREAMBULE)).isEqualTo("\\usepackage{a}");
        assertThat(LatexDokument.brojLinijaPreambule(preambula)).isEqualTo(3);
        assertThat(d.pomakLinija()).isEqualTo(LatexDokument.POMAK_PREAMBULE + 3 + 1);   // + \begin{document}
        assertThat(LatexDokument.brojLinijaPreambule(null)).isEqualTo((int) LatexDokument.PODRAZUMEVANA_PREAMBULA.lines().count());
    }

    @Test
    void omotJeStandaloneSaPreambulomIBegindocument() {
        LatexDokument.Dokument d = LatexDokument.zaTelefon("\\usepackage{a}", "tekst $x$");

        assertThat(d.tex()).startsWith("\\documentclass[preview,border=6pt,varwidth=84mm,11pt]{standalone}\n\\usepackage{a}\n"
                + "\\begin{document}\ntekst $x$\n\\end{document}");
        assertThat(d.tex()).endsWith("\\end{document}\n");
    }

    @Test
    void teloSePrenosiBezIzmeneUklonjenSamoCrlf() {
        String telo = "\n  prva  \n\n\tdruga\n";
        LatexDokument.Dokument d = LatexDokument.zaTelefon("\\relax", telo);

        assertThat(d.tex()).contains("\\begin{document}\n" + telo + "\n\\end{document}");
    }

    @Test
    void praznaIliNullPreambulaKoristiPodrazumevanu() {
        for (String prazna : new String[] {null, "", "   \n\t "}) {
            LatexDokument.Dokument d = LatexDokument.zaTelefon(prazna, "x");

            assertThat(d.tex()).contains(LatexDokument.PODRAZUMEVANA_PREAMBULA).contains("automata");
            int linijaPreambule = (int) LatexDokument.PODRAZUMEVANA_PREAMBULA.lines().count();
            assertThat(d.pomakLinija()).isEqualTo(linijaPreambule + 2);
            assertThat(linije(d.tex()).get(d.pomakLinija())).isEqualTo("x");
        }
    }

    @Test
    void podrazumevanaPreambulaImaPaketeIzSpecifikacije() {
        assertThat(LatexDokument.PODRAZUMEVANA_PREAMBULA)
                .contains("\\usepackage[T1]{fontenc}", "\\usepackage[utf8]{inputenc}", "amsmath", "amssymb", "{tikz}",
                        "\\usetikzlibrary{automata,positioning,arrows.meta}", "graphicx", "booktabs", "enumitem",
                        "\\setlength{\\parindent}{0pt}")
                .doesNotEndWith("\n");
    }

    @Test
    void crlfIJedanCrSePretvarajuULfPaPomakOstajeTacan() {
        LatexDokument.Dokument d = LatexDokument.zaTelefon("\\relax\r\n\\relax\r\\relax", "A\r\nB\rC");

        assertThat(d.tex()).doesNotContain("\r");
        assertThat(d.pomakLinija()).isEqualTo(5);
        assertThat(linije(d.tex()).subList(5, 8)).containsExactly("A", "B", "C");
    }

    @Test
    void nullTeloJePrazno() {
        LatexDokument.Dokument d = LatexDokument.zaTelefon("\\relax", null);

        assertThat(d.tex()).contains("\\begin{document}\n\n\\end{document}");
        assertThat(d.pomakLinija()).isEqualTo(3);
    }

    @Test
    void preambulaIliVracaPodrazumevanuKadJePrazna() {
        assertThat(LatexDokument.preambulaIli(null)).isEqualTo(LatexDokument.PODRAZUMEVANA_PREAMBULA);
        assertThat(LatexDokument.preambulaIli("")).isEqualTo(LatexDokument.PODRAZUMEVANA_PREAMBULA);
        assertThat(LatexDokument.preambulaIli(" \n\t")).isEqualTo(LatexDokument.PODRAZUMEVANA_PREAMBULA);
    }

    @Test
    void preambulaIliSkracujeZadatuPreambulu() {
        assertThat(LatexDokument.preambulaIli("\n  \\usepackage{x}\n\n ")).isEqualTo("\\usepackage{x}");
    }
}
