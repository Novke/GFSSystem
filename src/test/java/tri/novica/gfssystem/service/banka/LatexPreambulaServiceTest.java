package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link LatexPreambulaService} bez baze: pravila preambule, probni render pre čuvanja (pravi {@link RenderVarijante}, lažan
 * samo HTTP klijent {@link LatexRender}) i poništavanje keša rendera samo kad se vrednost menja.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LatexPreambulaServiceTest {

    static final String PREAMBULA = "\\usepackage{amsmath}\n\\usepackage{xcolor}";

    @Mock PredmetRepository predmetRepository;
    @Mock VarijantaRepository varijantaRepository;
    @Mock LatexRender latexRender;
    @Mock MedijService medijService;
    @Mock PlatformTransactionManager transakcije;
    @Mock EntityManager em;

    LatexPreambulaService service;
    Predmet predmet;

    @BeforeEach
    void setUp() {
        service = new LatexPreambulaService(predmetRepository, varijantaRepository, new RenderVarijante(latexRender, medijService),
                transakcije, em);
        predmet = new Predmet();
        predmet.setId(7L);
        predmet.setNaziv("Uvod u računarstvo");
        when(predmetRepository.findById(7L)).thenReturn(Optional.of(predmet));
        when(predmetRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(predmet));
        when(predmetRepository.save(any(Predmet.class))).thenAnswer(i -> i.getArgument(0));
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(USPEH);
    }

    static final RenderOdgovor USPEH = new RenderOdgovor(true,
            Base64.getEncoder().encodeToString("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()), null, null, null, 5L, false);

    /** Neuspeh kompilacije kako ga šalje servis: linija u dokumentu i kraj loga sa {@code dokument.tex:N:}. */
    static RenderOdgovor neuspeh(int linijaUDokumentu, String poruka) {
        return new RenderOdgovor(false, null, null, new RenderGreskaInfo(linijaUDokumentu, poruka),
                "(./dokument.tex\n./dokument.tex:" + linijaUDokumentu + ": " + poruka + "\n", null, false);
    }

    void nistaNijeUpisano() {
        verify(predmetRepository, never()).findByIdForUpdate(any());
        verify(predmetRepository, never()).save(any());
        verify(varijantaRepository, never()).ponistiHashPredmeta(any());
    }

    private static SystemException odbijeno(org.junit.jupiter.api.function.Executable e, int status) {
        SystemException x = assertThrows(SystemException.class, e);
        assertEquals(status, x.getCode());
        return x;
    }

    // ---------------------------------------------------------------- čitanje

    @Test
    void predmetBezPreambuleDajePodrazumevanu() {
        PreambulaInfo info = service.preambula(7L);
        assertNull(info.preambula());
        assertTrue(info.podrazumevana());
        assertEquals(LatexDokument.PODRAZUMEVANA_PREAMBULA, info.podrazumevanaPreambula());
    }

    @Test
    void predmetSaPreambulomDajeNjenTekst() {
        predmet.setLatexPreambula(PREAMBULA);
        PreambulaInfo info = service.preambula(7L);
        assertEquals(PREAMBULA, info.preambula());
        assertFalse(info.podrazumevana());
        assertEquals(LatexDokument.PODRAZUMEVANA_PREAMBULA, info.podrazumevanaPreambula());
    }

    @Test
    void nepoznatPredmetJe404() {
        assertEquals("Predmet nije pronađen.", odbijeno(() -> service.preambula(9L), 404).getMessage());
        assertEquals("Predmet nije pronađen.",
                odbijeno(() -> service.postavi(9L, new PreambulaCmd(PREAMBULA)), 404).getMessage());
        verify(varijantaRepository, never()).ponistiHashPredmeta(any());
    }

    // ---------------------------------------------------------------- postavljanje

    @Test
    void novaPreambulaSeCuvaIPonistavaKesCeleBankeJednom() {
        PreambulaInfo info = service.postavi(7L, new PreambulaCmd("  " + PREAMBULA + "\n\n"));

        assertEquals(PREAMBULA, predmet.getLatexPreambula());   // bez razmaka na krajevima
        assertEquals(PREAMBULA, info.preambula());
        assertFalse(info.podrazumevana());
        InOrder redom = inOrder(latexRender, em, predmetRepository, varijantaRepository);
        redom.verify(latexRender).renderuj("svg", LatexDokument.zaTelefon(PREAMBULA, RenderVarijante.PROBA_PREAMBULE).tex(), List.of());
        redom.verify(em).clear();   // open-in-view: ključ ne sme da vrati entitet pročitan pre rendera
        redom.verify(predmetRepository).findByIdForUpdate(7L);
        redom.verify(predmetRepository).save(predmet);
        redom.verify(varijantaRepository).ponistiHashPredmeta(7L);
        verifyNoMoreInteractions(varijantaRepository, latexRender);
    }

    @Test
    void preambulaKojaSeNeKompajliraJe400SaLinijomUPreambuliINistaSeNeCuva() {
        // dokument: omot (1), preambula od 3 linije (2-4), \begin{document} (5): greška u drugoj liniji preambule
        String preambula = "\\usepackage{amsmath}\n\\newcommand{\\kN}{\\,\\mathrm{kN}\n\\usepackage{xcolor}";
        when(latexRender.renderuj(eq("svg"), contains("xcolor"), anyList()))
                .thenReturn(neuspeh(LatexDokument.POMAK_PREAMBULE + 2, "Paragraph ended before \\newcommand was complete."));

        SystemException x = odbijeno(() -> service.postavi(7L, new PreambulaCmd(preambula)), 400);

        assertEquals("Preambula se ne kompajlira (linija 2): Paragraph ended before \\newcommand was complete.", x.getMessage());
        nistaNijeUpisano();
        assertNull(predmet.getLatexPreambula());
    }

    @Test
    void greskaPosleKrajaPreambuleJeBezLinije() {
        // greška koju pdflatex prijavi tek na \begin{document} ili kasnije nije linija preambule
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(neuspeh(LatexDokument.POMAK_PREAMBULE + 2 + 1, "Missing \\begin{document}."));

        SystemException x = odbijeno(() -> service.postavi(7L, new PreambulaCmd(PREAMBULA)), 400);

        assertEquals("Preambula se ne kompajlira: Missing \\begin{document}.", x.getMessage());
        nistaNijeUpisano();
    }

    @Test
    void odbijenZahtevRenderaJeOpstaPorukaBezSirovogTela() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(RenderOdgovor.odbijen("413 telo servisa"));

        SystemException x = odbijeno(() -> service.postavi(7L, new PreambulaCmd(PREAMBULA)), 400);

        assertEquals("Preambula se ne kompajlira: Render servis je odbio zahtev.", x.getMessage());
        nistaNijeUpisano();
    }

    @Test
    void nedostupanRenderJe503INistaSeNeCuva() {
        predmet.setLatexPreambula("\\usepackage{siunitx}");
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenThrow(new RenderNedostupan());

        SystemException x = odbijeno(() -> service.postavi(7L, new PreambulaCmd(PREAMBULA)), 503);

        assertEquals("Render servis nije dostupan.", x.getMessage());
        nistaNijeUpisano();
        assertEquals("\\usepackage{siunitx}", predmet.getLatexPreambula());
    }

    @Test
    void povratakNaPodrazumevanuSeNeRenderuje() {
        // podrazumevana preambula je poznata i ispravna: vraćanje na nju radi i kad render servis ne radi
        predmet.setLatexPreambula(PREAMBULA);
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenThrow(new RenderNedostupan());

        PreambulaInfo info = service.postavi(7L, new PreambulaCmd(null));

        assertTrue(info.podrazumevana());
        assertNull(predmet.getLatexPreambula());
        verify(varijantaRepository).ponistiHashPredmeta(7L);
        verifyNoInteractions(latexRender);
    }

    @Test
    void eksplicitnaPodrazumevanaSeCuvaKaoNullINePonistavaKes() {
        // ista kao podrazumevana (i sa CRLF i razmacima na krajevima): heš je isti, pa keš ne sme da padne
        String kaoPodrazumevana = "\r\n" + LatexDokument.PODRAZUMEVANA_PREAMBULA.replace("\n", "\r\n") + "  \n";

        PreambulaInfo info = service.postavi(7L, new PreambulaCmd(kaoPodrazumevana));

        assertTrue(info.podrazumevana());
        assertNull(info.preambula());
        assertNull(predmet.getLatexPreambula());
        verify(predmetRepository, never()).save(any());
        verify(varijantaRepository, never()).ponistiHashPredmeta(any());
        verifyNoInteractions(latexRender);

        // sa druge preambule: upisuje se null (ne tekst podrazumevane), keš se poništava jednom
        predmet.setLatexPreambula(PREAMBULA);
        assertTrue(service.postavi(7L, new PreambulaCmd(LatexDokument.PODRAZUMEVANA_PREAMBULA)).podrazumevana());
        assertNull(predmet.getLatexPreambula());
        verify(varijantaRepository).ponistiHashPredmeta(7L);
    }

    @Test
    void ponovljenaPreambulaPosleRenderaKojuJeNekoUMedjuvremenuUpisaoNeDiraKes() {
        // istovremena ista izmena: druga vidi pod ključem da je vrednost već upisana i ne poništava keš ponovo
        when(predmetRepository.findByIdForUpdate(7L)).thenAnswer(inv -> {
            predmet.setLatexPreambula(PREAMBULA);
            return Optional.of(predmet);
        });

        PreambulaInfo info = service.postavi(7L, new PreambulaCmd(PREAMBULA));

        assertEquals(PREAMBULA, info.preambula());
        verify(predmetRepository, never()).save(any());
        verify(varijantaRepository, never()).ponistiHashPredmeta(any());
    }

    @Test
    void praznoVracaPodrazumevanuIPonistavaKes() {
        predmet.setLatexPreambula(PREAMBULA);
        PreambulaInfo info = service.postavi(7L, new PreambulaCmd(" \n\t "));

        assertNull(predmet.getLatexPreambula());
        assertNull(info.preambula());
        assertTrue(info.podrazumevana());
        assertEquals(LatexDokument.PODRAZUMEVANA_PREAMBULA, info.podrazumevanaPreambula());
        verify(varijantaRepository).ponistiHashPredmeta(7L);
    }

    @Test
    void nullVracaPodrazumevanu() {
        predmet.setLatexPreambula(PREAMBULA);
        PreambulaInfo info = service.postavi(7L, new PreambulaCmd(null));
        assertNull(predmet.getLatexPreambula());
        assertTrue(info.podrazumevana());
        verify(varijantaRepository).ponistiHashPredmeta(7L);
    }

    @Test
    void istaVrednostNeDiraNistaIKesOstaje() {
        predmet.setLatexPreambula(PREAMBULA);
        PreambulaInfo info = service.postavi(7L, new PreambulaCmd(PREAMBULA + "  \n"));   // posle trim-a ista
        assertEquals(PREAMBULA, info.preambula());
        nistaNijeUpisano();
        verifyNoInteractions(latexRender);   // ni probni render
    }

    @Test
    void praznoNaPredmetuBezPreambuleNeMenjaNista() {
        PreambulaInfo info = service.postavi(7L, new PreambulaCmd("   "));
        assertTrue(info.podrazumevana());
        verify(predmetRepository, never()).save(any());
        verify(varijantaRepository, never()).ponistiHashPredmeta(any());
    }

    // ---------------------------------------------------------------- pravila

    @Test
    void zabranjenaKomandaJe400SaImenomKomande() {
        SystemException x = odbijeno(() -> service.postavi(7L, new PreambulaCmd("\\usepackage{x}\n\\immediate\\write18{ls}")), 400);
        assertEquals("Komanda \\write18 nije dozvoljena u preambuli.", x.getMessage());
        assertEquals("Komanda \\input nije dozvoljena u preambuli.",
                odbijeno(() -> service.postavi(7L, new PreambulaCmd("\\input{tajna}")), 400).getMessage());
        nistaNijeUpisano();
        verifyNoInteractions(latexRender);   // deny-lista pre probnog rendera
    }

    @Test
    void preambulaSmePaketeKatkodoveIOmot() {
        // pravila "samo telo" (paketi, katkodovi, omot dokumenta) ne važe za preambulu
        assertDoesNotThrow(() -> service.postavi(7L, new PreambulaCmd("\\usepackage{tikz}\n\\RequirePackage{xcolor}\n\\catcode`\\@=11")));
    }

    @Test
    void najvise50000ZnakovaBrojanoUCodePointima() {
        // 25 000 emodžija = 50 000 UTF-16 jedinica, ali 25 000 znakova: prolazi
        assertDoesNotThrow(() -> service.postavi(7L, new PreambulaCmd("%" + "\uD83D\uDE00".repeat(24_999))));
        assertDoesNotThrow(() -> service.postavi(7L, new PreambulaCmd("%".repeat(50_000))));
        SystemException x = odbijeno(() -> service.postavi(7L, new PreambulaCmd("%".repeat(50_001))), 400);
        assertEquals("Preambula može imati najviše 50 000 znakova.", x.getMessage());
    }

    @Test
    void duzinaSeProveravaPosleTrimaPreZabranjenihKomandi() {
        // 50 000 znakova + razmaci na krajevima: posle trim-a u granici
        assertDoesNotThrow(() -> service.postavi(7L, new PreambulaCmd("  " + "%".repeat(50_000) + "\n")));
    }
}
