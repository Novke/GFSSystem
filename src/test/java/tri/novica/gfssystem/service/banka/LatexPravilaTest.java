package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LatexPravilaTest {

    private static Optional<String> telo(String s) {
        return LatexPravila.zabranjenaKomandaUTelu(s);
    }

    private static Optional<String> preambula(String s) {
        return LatexPravila.zabranjenaKomandaUPreambuli(s);
    }

    @Test
    void obicanTekstINaredbeSuDozvoljeni() {
        assertThat(telo("Zadatak 1. Izračunaj $x^2 + 1$ \\\\ \\textbf{a}\n\\begin{enumerate}\\item b\\end{enumerate}"))
                .isEmpty();
        assertThat(telo("\\begin{tikzpicture}\\node (a) {A};\\end{tikzpicture}")).isEmpty();
        assertThat(telo(null)).isEmpty();
        assertThat(telo("")).isEmpty();
        assertThat(preambula(null)).isEmpty();
    }

    @Test
    void write18JePrijavljenKaoWrite18() {
        assertThat(telo("x \\write18{ls}")).contains("\\write18");
        assertThat(telo("\\immediate\\write 18{ls}")).contains("\\write18");
        assertThat(telo("\\write018{ls}")).contains("\\write18");
        assertThat(preambula("\\write18{ls}")).contains("\\write18");
    }

    @Test
    void write18IdePrePlainWriteBezObziraNaRedosledUTekstu() {
        assertThat(telo("\\write{a} \\write18{ls}")).contains("\\write18");
    }

    @Test
    void plainWriteJePrijavljenKaoWrite() {
        assertThat(telo("\\write{3}{x}")).contains("\\write");
        assertThat(telo("\\write16{x}")).contains("\\write");
        assertThat(telo("\\write185{x}")).contains("\\write");
    }

    @Test
    void inputUSvimOblicimaAliSamoKaoCelaKontrolnaRec() {
        assertThat(telo("\\input{/etc/passwd}")).contains("\\input");
        assertThat(telo("\\input x")).contains("\\input");
        assertThat(telo("a \\input\nx")).contains("\\input");
        assertThat(telo("\\inputx{y}")).isEmpty();
        assertThat(telo("\\inputenc")).isEmpty();
        assertThat(telo("{inputenc} input \\ input")).isEmpty();
    }

    @Test
    void ostaleZabranjeneKomandeUTelu() {
        assertThat(telo("\\include{x}")).contains("\\include");
        assertThat(telo("\\openin5=x")).contains("\\openin");
        assertThat(telo("\\openout5=x")).contains("\\openout");
        assertThat(telo("\\read5 to \\x")).contains("\\read");
        assertThat(telo("\\immediate\\x")).contains("\\immediate");
        assertThat(telo("\\usepackage{x}")).contains("\\usepackage");
        assertThat(telo("\\documentclass{article}")).contains("\\documentclass");
        assertThat(telo("\\catcode`\\@=11")).contains("\\catcode");
        assertThat(telo("\\csname input\\endcsname")).contains("\\csname");
        assertThat(telo("\\directlua{os.execute('ls')}")).contains("\\directlua");
    }

    @Test
    void srodneKontrolneReciNisuZabranjene() {
        assertThat(telo("\\includeonly{a}")).isEmpty();
        assertThat(telo("\\readline")).isEmpty();
        assertThat(telo("\\writefoo")).isEmpty();
        assertThat(telo("\\immediately")).isEmpty();
        assertThat(telo("\\usepackages")).isEmpty();
    }

    @Test
    void beginIEndDocumentSuZabranjeniUTelu() {
        assertThat(telo("\\begin{document}")).contains("\\begin{document}");
        assertThat(telo("\\begin {document}")).contains("\\begin{document}");
        assertThat(telo("x\\end{document}")).contains("\\end{document}");
        assertThat(telo("\\end { document }")).contains("\\end{document}");
    }

    @Test
    void includegraphicsSamoSaImenomPriloga() {
        assertThat(telo("\\includegraphics[width=3cm]{dijagram}")).isEmpty();
        assertThat(telo("\\includegraphics{dijagram_1-a}")).isEmpty();
        assertThat(telo("\\includegraphics[width=.5\\linewidth,clip]{slika2}")).isEmpty();

        assertThat(telo("\\includegraphics{../x}")).contains("\\includegraphics");
        assertThat(telo("\\includegraphics{/etc/passwd}")).contains("\\includegraphics");
        assertThat(telo("\\includegraphics{dijagram.png}")).contains("\\includegraphics");
        assertThat(telo("\\includegraphics{Dijagram}")).contains("\\includegraphics");
        assertThat(telo("\\includegraphics*{dijagram}")).contains("\\includegraphics");
        assertThat(telo("\\includegraphics")).contains("\\includegraphics");
        assertThat(telo("\\includegraphics[width=3cm]")).contains("\\includegraphics");
    }

    @Test
    void jedanLosIncludegraphicsKvariCeoTekstIAkoJeDrugiDobar() {
        assertThat(telo("\\includegraphics{a} \\includegraphics{../b} \\includegraphics{c}"))
                .contains("\\includegraphics");
    }

    @Test
    void includegraphicsSaDuzomKontrolnomReciNijeIncludegraphics() {
        assertThat(telo("\\includegraphicsx{../x}")).isEmpty();
    }

    @Test
    void urlSaFileSemomJeZabranjen() {
        assertThat(telo("\\url{file:///etc/passwd}")).contains("\\url{file:");
        assertThat(telo("\\url{FILE:/x}")).contains("\\url{file:");
        assertThat(telo("\\url{ file:x}")).contains("\\url{file:");
        assertThat(telo("\\url{https://trif.rs/file:x}")).isEmpty();
        assertThat(telo("\\url{https://example.org}")).isEmpty();
    }

    @Test
    void komentarNeNeutralisePrijavljenuKomandu() {
        assertThat(telo("% \\input{x}")).contains("\\input");
        assertThat(telo("tekst % \\write18{ls}")).contains("\\write18");
    }

    @Test
    void preambulaSmePaketeIKatkodeAliNeOstaleOpasneKomande() {
        assertThat(preambula("\\usepackage{pgfplots}")).isEmpty();
        assertThat(preambula("\\usepackage[utf8]{inputenc}")).isEmpty();
        assertThat(preambula("\\catcode`\\@=11")).isEmpty();
        assertThat(preambula("\\documentclass{article}")).isEmpty();
        assertThat(preambula("\\begin{document}\\end{document}")).isEmpty();

        assertThat(preambula("\\input{x}")).contains("\\input");
        assertThat(preambula("\\include{x}")).contains("\\include");
        assertThat(preambula("\\openin5=x")).contains("\\openin");
        assertThat(preambula("\\openout5=x")).contains("\\openout");
        assertThat(preambula("\\read5 to \\x")).contains("\\read");
        assertThat(preambula("\\write3{x}")).contains("\\write");
        assertThat(preambula("\\immediate\\x")).contains("\\immediate");
        assertThat(preambula("\\csname x\\endcsname")).contains("\\csname");
        assertThat(preambula("\\directlua{x}")).contains("\\directlua");
        assertThat(preambula("\\url{file:x}")).contains("\\url{file:");
        assertThat(preambula("\\includegraphics{../x}")).contains("\\includegraphics");
    }

    @Test
    void podrazumevanaPreambulaProlaziProveruPreambule() {
        assertThat(preambula(LatexDokument.PODRAZUMEVANA_PREAMBULA)).isEmpty();
    }
}
