package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import tri.novica.gfssystem.dto.banka.PrilogCmd;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.dto.banka.RenderPrilog;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RenderVarijante} bez mreže i diska: {@link LatexRender} i {@link MedijService} su lažni. Deny-lista pre
 * bilo kakvog poziva, tekst pa rešenje kao dva dokumenta, greška sa delom i linijom relativnom na taj deo, poruke koje
 * nastavnik vidi (bez sirovog tela 4xx odgovora), heš keša i zapis greške u koloni.
 */
@ExtendWith(MockitoExtension.class)
class RenderVarijanteTest {

    static final String PNG = "5b3e4d1c-8f90-4e3c-9d4b-2c3d4e5f6071";
    static final String JPG = "6c4f5e2d-9a01-4f4d-8e5c-3d4e5f607182";
    static final String PDF = "7d5a6f3e-0b12-4a5e-9f6d-4e5f60718293";
    static final String GIF = "8e6b7a4f-1c23-4b6f-8a7e-5f6071829304";
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>".getBytes(StandardCharsets.UTF_8);
    static final byte[] SVG_RESENJA = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M1 1h2\"/></svg>".getBytes(StandardCharsets.UTF_8);
    /** PNG iz render servisa (sadržaj nije bitan: {@code RenderVarijante} ga samo dekodira iz base64). */
    static final byte[] PNG_ZADATKA = {(byte) 0x89, 'P', 'N', 'G', 1};
    static final byte[] PNG_RESENJA = {(byte) 0x89, 'P', 'N', 'G', 2};
    static final List<String> SVG_I_PNG = List.of("svg", "png");
    /** Broj linija pre tela u dokumentu sa podrazumevanom preambulom. */
    static final int POMAK = LatexDokument.zaTelefon(null, "").pomakLinija();

    @Mock LatexRender latexRender;
    @Mock MedijService medijService;

    RenderVarijante render;

    @BeforeEach
    void setUp() {
        render = new RenderVarijante(latexRender, medijService);
    }

    static RenderOdgovor uspeh(byte[] svg) {
        return new RenderOdgovor(true, Base64.getEncoder().encodeToString(svg), null, null, null, 12L, false);
    }

    /** Uspeh sa više formata ({@code null} = format nije stigao). */
    static RenderOdgovor uspeh(byte[] svg, byte[] png) {
        Base64.Encoder b64 = Base64.getEncoder();
        return new RenderOdgovor(true, svg == null ? null : b64.encodeToString(svg), null, null, null, 12L, false,
                png == null ? null : b64.encodeToString(png));
    }

    /** Neuspeh kompilacije kako ga šalje servis: greška (linija u dokumentu) i kraj loga. */
    static RenderOdgovor neuspeh(Integer linija, String poruka, String log) {
        return new RenderOdgovor(false, null, null, new RenderGreskaInfo(linija, poruka), log, null, false);
    }

    void fajl(String id, String mime, VrstaMedija vrsta, byte[] bajtovi) {
        when(medijService.ucitaj(id)).thenReturn(new MedijService.Fajl(mime, bajtovi.length, new ByteArrayResource(bajtovi), vrsta));
    }

    // ---------------------------------------------------------------- deny-lista

    @Test
    void zabranjenaKomandaUTekstuIliResenjuJe400PreBiloKakvogPoziva() {
        SystemException e = assertThrows(SystemException.class,
                () -> render.renderuj(null, "Zadatak \\write18{ls}", null, List.of(new PrilogCmd("skica", PNG))));
        assertEquals(400, e.getCode());
        assertEquals("Komanda \\write18 nije dozvoljena.", e.getMessage());

        e = assertThrows(SystemException.class,
                () -> render.renderuj(null, "Ispravan tekst.", "Rešenje: \\input{/etc/passwd}", List.of()));
        assertEquals(400, e.getCode());
        assertEquals("Komanda \\input nije dozvoljena.", e.getMessage());

        e = assertThrows(SystemException.class, () -> RenderVarijante.proveriTelo("\\includegraphics{../x.png}", null));
        assertEquals("Komanda \\includegraphics nije dozvoljena.", e.getMessage());
        verifyNoInteractions(latexRender, medijService);
    }

    @Test
    void preambulaSeOvdeNeProveravaPonovo() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(uspeh(SVG));

        RenderVarijante.Ishod ishod = render.renderuj("\\usepackage{siunitx}", "Tekst.", null, List.of());

        assertTrue(ishod.ok());
        verify(latexRender).renderuj(eq("svg"), contains("\\usepackage{siunitx}"), anyList());
    }

    // ---------------------------------------------------------------- tok rendera

    @Test
    void tekstIResenjeSuDvaDokumentaISvgSeDekodiraIzBase64() {
        when(latexRender.renderuj(eq("svg"), contains("Pretvori $1011_2$"), anyList())).thenReturn(uspeh(SVG));
        when(latexRender.renderuj(eq("svg"), contains("$11$"), anyList())).thenReturn(uspeh(SVG_RESENJA));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Pretvori $1011_2$ u dekadni.", "$11$", List.of());

        assertTrue(ishod.ok());
        assertArrayEquals(SVG, ishod.svg());
        assertArrayEquals(SVG_RESENJA, ishod.resenjeSvg());
        assertNull(ishod.greska());
        assertNull(ishod.deo());
        verify(latexRender).renderuj("svg", LatexDokument.zaTelefon(null, "Pretvori $1011_2$ u dekadni.").tex(), List.of());
        verify(latexRender).renderuj("svg", LatexDokument.zaTelefon(null, "$11$").tex(), List.of());
    }

    @Test
    void bezResenjaJeJedanPoziv() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(uspeh(SVG));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Tekst.", "  \n ", List.of());

        assertTrue(ishod.ok());
        assertNull(ishod.resenjeSvg());
        verify(latexRender, times(1)).renderuj(anyString(), anyString(), anyList());
    }

    @Test
    void greskaURresenjuDajeDeoResenjeILinijuRelativnuNaResenjeISvgZadatka() {
        int linija = POMAK + 3;
        when(latexRender.renderuj(eq("svg"), contains("Tekst zadatka"), anyList())).thenReturn(uspeh(SVG));
        when(latexRender.renderuj(eq("svg"), contains("\\foo"), anyList())).thenReturn(neuspeh(linija,
                "Undefined control sequence.", "(./dokument.tex\n./dokument.tex:" + linija + ": Undefined control sequence.\nl." + linija + " Treća \\foo\n"));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Tekst zadatka.", "Prva\nDruga\nTreća \\foo", List.of());

        assertFalse(ishod.ok());
        assertEquals("resenje", ishod.deo());
        assertEquals(new RenderGreskaInfo(3, "Undefined control sequence."), ishod.greska());
        assertArrayEquals(SVG, ishod.svg());   // zadatak se preveo: proba ga prikazuje uz grešku rešenja
        assertNull(ishod.resenjeSvg());
    }

    @Test
    void greskaUTekstuDajeDeoTekstIResenjeSeNePrevodi() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(neuspeh(POMAK + 2, "Missing $ inserted.",
                "./dokument.tex:" + (POMAK + 2) + ": Missing $ inserted.\n"));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Prva\nx_1", "$11$", List.of());

        assertFalse(ishod.ok());
        assertEquals("tekst", ishod.deo());
        assertEquals(new RenderGreskaInfo(2, "Missing $ inserted."), ishod.greska());
        verify(latexRender, times(1)).renderuj(anyString(), anyString(), anyList());
    }

    @Test
    void bezLinijeULoguVaziPorukaServisaALinijaSeSvodiNaTelo() {
        // greška iz paketa (tikz.sty), istek vremena: log nema red dokument.tex, poruku je dao servis
        when(latexRender.renderuj(eq("svg"), contains("Prvi"), anyList()))
                .thenReturn(neuspeh(null, "Prekoračeno vreme kompajliranja (20 s).", "This is pdfTeX\n(./dokument.tex\n"));
        // red greške je ispao iz poslednjih 60 linija loga, ali servis ga je našao (linija u dokumentu)
        when(latexRender.renderuj(eq("svg"), contains("Drugi"), anyList()))
                .thenReturn(neuspeh(POMAK + 5, "Missing $ inserted.", "...\n"));

        assertEquals(new RenderGreskaInfo(null, "Prekoračeno vreme kompajliranja (20 s)."),
                render.renderuj(null, "Prvi", null, List.of()).greska());
        assertEquals(new RenderGreskaInfo(5, "Missing $ inserted."), render.renderuj(null, "Drugi", null, List.of()).greska());
    }

    @Test
    void bezIcegaULoguIBezPorukeServisaJeOpstaPoruka() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(neuspeh(null, " ", "This is pdfTeX\n"));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Tekst.", null, List.of());

        assertFalse(ishod.ok());
        assertEquals(new RenderGreskaInfo(null, "LaTeX greška, pogledaj log."), ishod.greska());
        assertEquals("tekst", ishod.deo());
    }

    @Test
    void odbijenZahtevServisa4xxNePrikazujeSirovoTelo() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(RenderOdgovor.odbijen("<html><body>413 Request Entity Too Large</body></html>"));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Tekst.", null, List.of());

        assertFalse(ishod.ok());
        assertEquals(new RenderGreskaInfo(null, "Render servis je odbio zahtev."), ishod.greska());
    }

    @Test
    void neuspehBezLogaKojiNijeOdbijanjeZadrzavaPorukuServisa() {
        // samo eksplicitni marker znači odbijen zahtev, ne odsustvo loga
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(new RenderOdgovor(false, null, null,
                new RenderGreskaInfo(null, "Dokument nema stranica."), null, null, false));

        assertEquals(new RenderGreskaInfo(null, "Dokument nema stranica."),
                render.renderuj(null, "Tekst.", null, List.of()).greska());
    }

    @Test
    void neispravanBase64UOdgovoruJeNedostupanServis() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, "nije base64 !!", null, null, null, 1L, false));

        SystemException e = assertThrows(SystemException.class, () -> render.renderuj(null, "Tekst.", null, List.of()));
        assertEquals(503, e.getCode());
        assertEquals(RenderNedostupan.PORUKA, e.getMessage());
    }

    // ---------------------------------------------------------------- PNG iz istog prevođenja

    @Test
    void svgIPngSuJedanPozivPoDeluZaTekstIResenje() {
        when(latexRender.renderuj(eq(SVG_I_PNG), contains("Pretvori $1011_2$"), anyList())).thenReturn(uspeh(SVG, PNG_ZADATKA));
        when(latexRender.renderuj(eq(SVG_I_PNG), contains("$11$"), anyList())).thenReturn(uspeh(SVG_RESENJA, PNG_RESENJA));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Pretvori $1011_2$ u dekadni.", "$11$", List.of(), true, true);

        assertTrue(ishod.ok());
        assertArrayEquals(SVG, ishod.svg());
        assertArrayEquals(SVG_RESENJA, ishod.resenjeSvg());
        assertArrayEquals(PNG_ZADATKA, ishod.png());
        assertArrayEquals(PNG_RESENJA, ishod.resenjePng());
        verify(latexRender).renderuj(SVG_I_PNG, LatexDokument.zaTelefon(null, "Pretvori $1011_2$ u dekadni.").tex(), List.of());
        verify(latexRender).renderuj(SVG_I_PNG, LatexDokument.zaTelefon(null, "$11$").tex(), List.of());
        verify(latexRender, never()).renderuj(anyString(), anyString(), anyList());
    }

    @Test
    void samoPngTraziSamoPngIBezSvga() {
        when(latexRender.renderuj(eq(List.of("png")), contains("Tekst."), anyList())).thenReturn(uspeh(null, PNG_ZADATKA));
        when(latexRender.renderuj(eq(List.of("png")), contains("$11$"), anyList())).thenReturn(uspeh(null, PNG_RESENJA));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Tekst.", "$11$", List.of(), false, true);

        assertTrue(ishod.ok());
        assertNull(ishod.svg());
        assertNull(ishod.resenjeSvg());
        assertArrayEquals(PNG_ZADATKA, ishod.png());
        assertArrayEquals(PNG_RESENJA, ishod.resenjePng());
        verify(latexRender, times(2)).renderuj(eq(List.of("png")), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyString(), anyString(), anyList());
    }

    @Test
    void bezPngaIdeStariPozivSaJednimFormatom() {
        // postojeći mokovi (renderuj(eq("svg"), ...)) važe i za 4-arg i za (svg=true, png=false)
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(uspeh(SVG));

        RenderVarijante.Ishod cetiri = render.renderuj(null, "Tekst.", "$11$", List.of());
        RenderVarijante.Ishod sest = render.renderuj(null, "Tekst.", "$11$", List.of(), true, false);

        for (RenderVarijante.Ishod ishod : List.of(cetiri, sest)) {
            assertTrue(ishod.ok());
            assertArrayEquals(SVG, ishod.svg());
            assertNull(ishod.png());
            assertNull(ishod.resenjePng());
        }
        verify(latexRender, times(4)).renderuj(eq("svg"), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyList(), anyString(), anyList());
    }

    @Test
    void greskaResenjaUzPngNosiSvgIPngZadatka() {
        when(latexRender.renderuj(eq(SVG_I_PNG), contains("Tekst zadatka"), anyList())).thenReturn(uspeh(SVG, PNG_ZADATKA));
        when(latexRender.renderuj(eq(SVG_I_PNG), contains("\\foo"), anyList()))
                .thenReturn(neuspeh(POMAK + 1, "Undefined control sequence.", "./dokument.tex:" + (POMAK + 1) + ": Undefined control sequence.\n"));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Tekst zadatka.", "\\foo", List.of(), true, true);

        assertFalse(ishod.ok());
        assertEquals("resenje", ishod.deo());
        assertEquals(new RenderGreskaInfo(1, "Undefined control sequence."), ishod.greska());
        assertArrayEquals(SVG, ishod.svg());
        assertArrayEquals(PNG_ZADATKA, ishod.png());
        assertNull(ishod.resenjeSvg());
        assertNull(ishod.resenjePng());
    }

    @Test
    void greskaTekstaUzPngNemaSlikuIResenjeSeNePrevodi() {
        when(latexRender.renderuj(eq(SVG_I_PNG), anyString(), anyList())).thenReturn(neuspeh(POMAK + 2, "Missing $ inserted.",
                "./dokument.tex:" + (POMAK + 2) + ": Missing $ inserted.\n"));

        RenderVarijante.Ishod ishod = render.renderuj(null, "Prva\nx_1", "$11$", List.of(), true, true);

        assertFalse(ishod.ok());
        assertEquals("tekst", ishod.deo());
        assertNull(ishod.png());
        assertNull(ishod.svg());
        verify(latexRender, times(1)).renderuj(anyList(), anyString(), anyList());
    }

    @Test
    void neispravanBase64PngaJeNedostupanServis() {
        when(latexRender.renderuj(eq(SVG_I_PNG), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 1L, false, "nije base64 !!"));

        SystemException e = assertThrows(SystemException.class,
                () -> render.renderuj(null, "Tekst.", null, List.of(), true, true));
        assertEquals(503, e.getCode());
        assertEquals(RenderNedostupan.PORUKA, e.getMessage());
    }

    @Test
    void renderBezIjednogFormataJeGreskaPrograma() {
        assertThrows(IllegalArgumentException.class, () -> render.renderuj(null, "Tekst.", null, List.of(), false, false));
        verifyNoInteractions(latexRender, medijService);
    }

    // ---------------------------------------------------------------- prilozi

    @Test
    void priloziIduSaEkstenzijomPoMimeuIUcitavajuSeJednomZaObaDela() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1};
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 2};
        byte[] pdf = "%PDF-1.5".getBytes(StandardCharsets.US_ASCII);
        fajl(PNG, "image/png", VrstaMedija.SLIKA, png);
        fajl(JPG, "image/jpeg", VrstaMedija.SLIKA, jpg);
        fajl(PDF, "application/pdf", VrstaMedija.PRILOG_PDF, pdf);
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(uspeh(SVG));

        render.renderuj(null, "\\includegraphics{skica}", "\\includegraphics{dijagram}",
                List.of(new PrilogCmd("skica", PNG), new PrilogCmd("foto", JPG), new PrilogCmd("dijagram", PDF)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RenderPrilog>> prilozi = ArgumentCaptor.forClass(List.class);
        verify(latexRender, times(2)).renderuj(eq("svg"), anyString(), prilozi.capture());
        Base64.Encoder b64 = Base64.getEncoder();
        List<RenderPrilog> ocekivano = List.of(new RenderPrilog("skica", "png", b64.encodeToString(png)),
                new RenderPrilog("foto", "jpg", b64.encodeToString(jpg)), new RenderPrilog("dijagram", "pdf", b64.encodeToString(pdf)));
        assertEquals(List.of(ocekivano, ocekivano), prilozi.getAllValues());
        verify(medijService, times(1)).ucitaj(PNG);
        verify(medijService, times(1)).ucitaj(PDF);
    }

    @Test
    void prilogKojiNijePngJpegIliPdfJe400PrePoziva() {
        fajl(GIF, "image/gif", VrstaMedija.SLIKA, new byte[]{'G', 'I', 'F'});

        SystemException e = assertThrows(SystemException.class,
                () -> render.renderuj(null, "Tekst.", null, List.of(new PrilogCmd("anim", GIF))));
        assertEquals(400, e.getCode());
        assertEquals("Prilog mora biti PNG, JPEG ili PDF.", e.getMessage());
        assertEquals("png", RenderVarijante.ekstenzija("image/png"));
        assertEquals("jpg", RenderVarijante.ekstenzija("image/jpeg"));
        assertEquals("pdf", RenderVarijante.ekstenzija("application/pdf"));
        assertThrows(SystemException.class, () -> RenderVarijante.ekstenzija("image/webp"));
        assertThrows(SystemException.class, () -> RenderVarijante.ekstenzija("image/svg+xml"));
        verifyNoInteractions(latexRender);
    }

    // ---------------------------------------------------------------- heš

    @Test
    void hesJeStabilanNaRedosledPrilogaIPratiSvakiUlaz() {
        List<PrilogCmd> ab = List.of(new PrilogCmd("a", PNG), new PrilogCmd("b", PDF));
        List<PrilogCmd> ba = List.of(new PrilogCmd("b", PDF), new PrilogCmd("a", PNG));
        String h = render.hash("\\usepackage{amsmath}", "Tekst", "Rešenje", ab);

        assertEquals(64, h.length());
        assertEquals(h, render.hash("\\usepackage{amsmath}", "Tekst", "Rešenje", ba));
        assertEquals(TokenHash.od(RenderVarijante.RENDER_VERZIJA + "\u0000\\usepackage{amsmath}\u0000Tekst\u0000Rešenje\u0000a:"
                + PNG + ",b:" + PDF), h);
        assertNotEquals(h, render.hash("\\usepackage{amssymb}", "Tekst", "Rešenje", ab));
        assertNotEquals(h, render.hash("\\usepackage{amsmath}", "Tekst.", "Rešenje", ab));
        assertNotEquals(h, render.hash("\\usepackage{amsmath}", "Tekst", "Rešenje.", ab));
        assertNotEquals(h, render.hash("\\usepackage{amsmath}", "Tekst", "Rešenje", List.of(new PrilogCmd("a", PNG))));
        assertNotEquals(h, render.hash("\\usepackage{amsmath}", "Tekst", "Rešenje",
                List.of(new PrilogCmd("a", JPG), new PrilogCmd("b", PDF))));
        assertNotEquals(h, render.hash("\\usepackage{amsmath}", "Tekst", "Rešenje",
                List.of(new PrilogCmd("c", PNG), new PrilogCmd("b", PDF))));
    }

    @Test
    void hesKoristiPreambuluKojaSeZaistaPrevodi() {
        String podrazumevana = render.hash(null, "Tekst", null, null);

        assertEquals(podrazumevana, render.hash("  ", "Tekst", null, List.of()));
        assertEquals(podrazumevana, render.hash(LatexDokument.PODRAZUMEVANA_PREAMBULA, "Tekst", "", List.of()));
        assertEquals(TokenHash.od(RenderVarijante.RENDER_VERZIJA + "\u0000" + LatexDokument.PODRAZUMEVANA_PREAMBULA
                + "\u0000Tekst\u0000\u0000"), podrazumevana);
    }

    @Test
    void verzijaRenderaJeDeoHesa() {
        // promena omota dokumenta (LatexDokument) podiže RENDER_VERZIJA, pa svaki stari heš zastari
        List<PrilogCmd> prilozi = List.of(new PrilogCmd("a", PNG));
        String sada = render.hash("\\usepackage{amsmath}", "Tekst", "Rešenje", prilozi);

        assertEquals(sada, RenderVarijante.hash(RenderVarijante.RENDER_VERZIJA, "\\usepackage{amsmath}", "Tekst", "Rešenje", prilozi));
        assertNotEquals(sada, RenderVarijante.hash(RenderVarijante.RENDER_VERZIJA + 1, "\\usepackage{amsmath}", "Tekst", "Rešenje", prilozi));
    }

    // ---------------------------------------------------------------- kolona render_greska

    @Test
    void greskaSeUKoloniCuvaSaDelomILinijomIVracaNazad() {
        String kolona = RenderVarijante.uKolonu("resenje", new RenderGreskaInfo(12, "Undefined control sequence."));
        assertEquals("resenje:12: Undefined control sequence.", kolona);
        assertEquals(new RenderVarijante.Greska("resenje", 12, "Undefined control sequence."), RenderVarijante.izKolone(kolona));

        String bezLinije = RenderVarijante.uKolonu("tekst", new RenderGreskaInfo(null, "Prevelik izlaz: 6 MB.\nkraj"));
        assertEquals("tekst:: Prevelik izlaz: 6 MB.\nkraj", bezLinije);
        RenderVarijante.Greska g = RenderVarijante.izKolone(bezLinije);
        assertEquals(new RenderVarijante.Greska("tekst", null, "Prevelik izlaz: 6 MB.\nkraj"), g);
        assertEquals(new RenderGreskaInfo(null, "Prevelik izlaz: 6 MB.\nkraj"), g.info());

        // greška pre prevođenja (render cele banke): bez dela, samo poruka
        assertEquals("Slika nije pronađena.", RenderVarijante.uKolonu(null, new RenderGreskaInfo(null, "Slika nije pronađena.")));
        assertEquals(new RenderVarijante.Greska(null, null, "Slika nije pronađena."), RenderVarijante.izKolone("Slika nije pronađena."));

        assertNull(RenderVarijante.izKolone(null));
        // zapis bez dela (stariji ili ručni red) ostaje cela poruka
        assertEquals(new RenderVarijante.Greska(null, null, "linija 3: Undefined control sequence"),
                RenderVarijante.izKolone("linija 3: Undefined control sequence"));
    }
}
