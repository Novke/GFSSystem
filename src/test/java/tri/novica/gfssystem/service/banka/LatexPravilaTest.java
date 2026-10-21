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

    // --- proširenje: komande koje čitaju fajlove ili uključuju PDF ---

    @Test
    void requirePackageJeZabranjenUTeluAliNeUPreambuli() {
        assertThat(telo("\\RequirePackage{tikz}")).contains("\\RequirePackage");
        assertThat(preambula("\\RequirePackage{tikz}")).isEmpty();
        assertThat(telo("\\RequirePackages")).isEmpty();
    }

    @Test
    void inputIfFileExistsJeZabranjenUTeluIPreambuli() {
        assertThat(telo("\\InputIfFileExists{/etc/passwd}{}{}")).contains("\\InputIfFileExists");
        assertThat(preambula("\\InputIfFileExists{x.cfg}{}{}")).contains("\\InputIfFileExists");
    }

    @Test
    void lstinputlistingJeZabranjenUTeluIPreambuli() {
        assertThat(telo("\\lstinputlisting{/etc/passwd}")).contains("\\lstinputlisting");
        assertThat(telo("\\lstinputlisting[language=C]{x.c}")).contains("\\lstinputlisting");
        assertThat(preambula("\\lstinputlisting{x}")).contains("\\lstinputlisting");
    }

    @Test
    void verbatiminputJeZabranjenUTeluIPreambuli() {
        assertThat(telo("\\verbatiminput{/etc/passwd}")).contains("\\verbatiminput");
        assertThat(telo("\\verbatiminput*{x}")).contains("\\verbatiminput");
        assertThat(preambula("\\verbatiminput{x}")).contains("\\verbatiminput");
    }

    @Test
    void hrefSaFileSemomJeZabranjenUTeluIPreambuli() {
        assertThat(telo("\\href{file:///etc/passwd}{tekst}")).contains("\\href{file:");
        assertThat(telo("\\href{FILE:x}{tekst}")).contains("\\href{file:");
        assertThat(preambula("\\href{file:x}{t}")).contains("\\href{file:");
        assertThat(telo("\\href{https://trif.rs}{tekst}")).isEmpty();
        assertThat(telo("\\href{https://trif.rs/file:x}{tekst}")).isEmpty();
    }

    @Test
    void atInputJeCeoImeSaAt() {
        assertThat(telo("\\makeatletter\\@input{x}")).contains("\\@input");
        assertThat(preambula("\\makeatletter\\@input{x}")).contains("\\@input");
        assertThat(telo("\\makeatletter\\@inputx{x}")).isEmpty();
        assertThat(telo("\\makeatletter\\@input@x{x}")).isEmpty();
        assertThat(telo("\\@ input")).isEmpty();
    }

    @Test
    void inputAtPathJeZabranjenUTeluSaSvojimImenom() {
        assertThat(telo("\\makeatletter\\def\\input@path{{/etc/}}")).contains("\\input@path");
        assertThat(telo("\\input@path")).contains("\\input@path");
    }

    @Test
    void inputAtPathUPreambuliHvataObicanInput() {
        assertThat(preambula("\\makeatletter\\def\\input@path{{/etc/}}")).contains("\\input");
    }

    @Test
    void includepdfJeZabranjenIZaImePrilogaUTeluIPreambuli() {
        assertThat(telo("\\includepdf{dijagram}")).contains("\\includepdf");
        assertThat(telo("\\includepdf[pages=-]{/etc/x}")).contains("\\includepdf");
        assertThat(preambula("\\includepdf{dijagram}")).contains("\\includepdf");
        assertThat(telo("\\includepdfmerge{x.pdf,1}")).contains("\\includepdfmerge");
        assertThat(preambula("\\includepdfmerge{x.pdf,1}")).contains("\\includepdfmerge");
        assertThat(telo("\\includepdfx{dijagram}")).isEmpty();
        assertThat(telo("\\includegraphics{dijagram}")).isEmpty();
    }

    // --- @@input, @iinput i ^^ notacija ---

    @Test
    void dvostrukiAtInputJeCeoImeSaAt() {
        assertThat(telo("\\makeatletter\\@@input{/etc/passwd}")).contains("\\@@input");
        assertThat(preambula("\\makeatletter\\@@input{/etc/passwd}")).contains("\\@@input");
        assertThat(telo("\\makeatletter\\@@inputx{x}")).isEmpty();
        assertThat(telo("\\makeatletter\\@@input@x{x}")).isEmpty();
    }

    @Test
    void atIinputJeCeoImeSaAt() {
        assertThat(telo("\\makeatletter\\@iinput{/etc/passwd}")).contains("\\@iinput");
        assertThat(preambula("\\makeatletter\\@iinput{/etc/passwd}")).contains("\\@iinput");
        assertThat(telo("\\makeatletter\\@iinputx{x}")).isEmpty();
        assertThat(telo("\\makeatletter\\@iinput@x{x}")).isEmpty();
    }

    @Test
    void nizKapaKapaJeZabranjenUTeluIPreambuli() {
        assertThat(telo("\\inp^^75t{/etc/passwd}")).contains("^^");
        assertThat(preambula("\\inp^^75t{/etc/passwd}")).contains("^^");
        assertThat(telo("tekst ^^M")).contains("^^");
        assertThat(telo("$x^2 + y^{2}$ i $z^\\alpha$")).isEmpty();
        assertThat(telo("$x^ ^2$")).isEmpty();
    }
}
