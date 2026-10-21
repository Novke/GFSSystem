package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LatexGreskaTest {

    private static final String LOG = """
            This is pdfTeX, Version 3.141592653-2.6-1.40.26 (TeX Live 2024) (preloaded format=pdflatex)
            entering extended mode
            (./dokument.tex
            LaTeX2e <2024-06-01>
            ./dokument.tex:12: Undefined control sequence.
            l.12 \\foo
                      bar
            The control sequence at the end of the top line
            ./dokument.tex:15: Missing $ inserted.
            """;

    @Test
    void prviRedFileLineErrorOduzimaPomak() {
        Optional<LatexGreska.Greska> g = LatexGreska.izLoga(LOG, 4);

        assertThat(g).contains(new LatexGreska.Greska(8, "Undefined control sequence."));
    }

    @Test
    void linijaJeNajmanje1() {
        assertThat(LatexGreska.izLoga("./dokument.tex:3: Undefined control sequence.\n", 4))
                .contains(new LatexGreska.Greska(1, "Undefined control sequence."));
        assertThat(LatexGreska.izLoga("./dokument.tex:4: x\n", 4)).map(LatexGreska.Greska::linija).contains(1);
        assertThat(LatexGreska.izLoga("./dokument.tex:5: x\n", 4)).map(LatexGreska.Greska::linija).contains(1);
        assertThat(LatexGreska.izLoga("./dokument.tex:6: x\n", 4)).map(LatexGreska.Greska::linija).contains(2);
    }

    @Test
    void bezPomakaLinijaOstajeKakvaJe() {
        assertThat(LatexGreska.izLoga("./dokument.tex:12: Oops.", 0)).contains(new LatexGreska.Greska(12, "Oops."));
    }

    @Test
    void prihvataIBezTackeISlasa() {
        assertThat(LatexGreska.izLoga("dokument.tex:9: Oops.", 2)).contains(new LatexGreska.Greska(7, "Oops."));
    }

    @Test
    void ignorisePorukeDrugihFajlova() {
        String log = "./tikz.sty:44: Package tikz Error: x.\n./dokument.tex:20: Real.\n";

        assertThat(LatexGreska.izLoga(log, 4)).contains(new LatexGreska.Greska(16, "Real."));
    }

    @Test
    void redKojiNijePocetakLinijeNijeGreska() {
        assertThat(LatexGreska.izLoga("see (./dokument.tex:7: x\n", 0)).isEmpty();
    }

    @Test
    void bezFileLineRedaUzimaPrviRedSaUzvicnikom() {
        String log = "entering extended mode\n! Emergency stop.\n<*> dokument.tex\n! Druga.\n";

        assertThat(LatexGreska.izLoga(log, 4)).contains(new LatexGreska.Greska(null, "Emergency stop."));
    }

    @Test
    void fileLineRedImaPrednostNadUzvicnikom() {
        String log = "! LaTeX Error: Something.\n./dokument.tex:10: Real.\n";

        assertThat(LatexGreska.izLoga(log, 4)).contains(new LatexGreska.Greska(6, "Real."));
    }

    @Test
    void bezGreskeJePrazno() {
        assertThat(LatexGreska.izLoga("Output written on dokument.pdf (1 page).\n", 4)).isEmpty();
        assertThat(LatexGreska.izLoga("", 4)).isEmpty();
        assertThat(LatexGreska.izLoga(null, 4)).isEmpty();
    }

    @Test
    void duzinaPorukeJeOgranicenaNa300KodnihTacaka() {
        String duga = "x".repeat(1000);

        assertThat(LatexGreska.izLoga("./dokument.tex:5: " + duga + "\n", 0).orElseThrow().poruka()).hasSize(300);
        assertThat(LatexGreska.izLoga("! " + duga + "\n", 0).orElseThrow().poruka()).hasSize(300);
    }

    @Test
    void skracivanjeNeSecePoSrediSurogatniPar() {
        String emoji = "\uD83D\uDE00";
        String poruka = emoji.repeat(400);

        String rez = LatexGreska.izLoga("./dokument.tex:5: " + poruka + "\n", 0).orElseThrow().poruka();

        assertThat(rez.codePointCount(0, rez.length())).isEqualTo(300);
        assertThat(rez).isEqualTo(emoji.repeat(300));
    }

    @Test
    void porukaDo300ZnakovaNijeDirana() {
        String tacno = "y".repeat(300);

        assertThat(LatexGreska.izLoga("./dokument.tex:5: " + tacno + "\n", 0).orElseThrow().poruka()).isEqualTo(tacno);
    }

    @Test
    void crlfULoguNeOstavljaCrUPoruci() {
        assertThat(LatexGreska.izLoga("./dokument.tex:12: Undefined control sequence.\r\nl.12 \\foo\r\n", 4))
                .contains(new LatexGreska.Greska(8, "Undefined control sequence."));
    }

    @Test
    void preveliBrojLinijeNePuca() {
        assertThat(LatexGreska.izLoga("./dokument.tex:99999999999999999999: Oops.\n", 4))
                .contains(new LatexGreska.Greska(null, "Oops."));
    }

    @Test
    void radiSaDokumentomIzLatexDokumentaGreskaUTeluDobijaRelativnuLiniju() {
        LatexDokument.Dokument d = LatexDokument.zaTelefon("\\usepackage{a}\n\\usepackage{b}", "ok\nok\n\\foo\nok");
        int linijaUDokumentu = d.tex().substring(0, d.tex().indexOf("\\foo")).split("\n", -1).length;
        String log = "./dokument.tex:" + linijaUDokumentu + ": Undefined control sequence.\n";

        assertThat(LatexGreska.izLoga(log, d.pomakLinija())).contains(new LatexGreska.Greska(3, "Undefined control sequence."));
    }
}
