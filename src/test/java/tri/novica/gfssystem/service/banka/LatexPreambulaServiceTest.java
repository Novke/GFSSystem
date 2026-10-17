package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** {@link LatexPreambulaService} bez baze: pravila preambule i poništavanje keša rendera samo kad se vrednost menja. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LatexPreambulaServiceTest {

    static final String PREAMBULA = "\\usepackage{amsmath}\n\\usepackage{xcolor}";

    @Mock PredmetRepository predmetRepository;
    @Mock VarijantaRepository varijantaRepository;

    LatexPreambulaService service;
    Predmet predmet;

    @BeforeEach
    void setUp() {
        service = new LatexPreambulaService(predmetRepository, varijantaRepository);
        predmet = new Predmet();
        predmet.setId(7L);
        predmet.setNaziv("Uvod u računarstvo");
        when(predmetRepository.findById(7L)).thenReturn(Optional.of(predmet));
        when(predmetRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(predmet));
        when(predmetRepository.save(any(Predmet.class))).thenAnswer(i -> i.getArgument(0));
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
        InOrder redom = inOrder(predmetRepository, varijantaRepository);
        redom.verify(predmetRepository).findByIdForUpdate(7L);
        redom.verify(predmetRepository).save(predmet);
        redom.verify(varijantaRepository).ponistiHashPredmeta(7L);
        verifyNoMoreInteractions(varijantaRepository);
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
        verify(predmetRepository, never()).save(any());
        verify(varijantaRepository, never()).ponistiHashPredmeta(any());
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
        verify(predmetRepository, never()).save(any());
        verify(varijantaRepository, never()).ponistiHashPredmeta(any());
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
