package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Ishodi pojedinačnih brisanja u {@link SerijaService#ponisti} (Mockito, bez baze): 404 (obrisano u međuvremenu) se ne
 * pominje, 409 je preskočeno sa razlogom, svaka druga greška prekida poništavanje; bez potvrde se ništa ne briše.
 */
@ExtendWith(MockitoExtension.class)
class SerijaServiceTest {

    static final String SERIJA = "uvoz-kol1-2024";

    @Mock VarijantaRepository varijantaRepository;
    @Mock GrupaZadatakaRepository grupaRepository;
    @Mock PrezentacijaRepository prezentacijaRepository;
    @Mock SlajdRepository slajdRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock VarijantaService varijantaService;
    @Mock GrupaZadatakaService grupaZadatakaService;

    SerijaService service;

    @BeforeEach
    void setUp() {
        service = new SerijaService(varijantaRepository, grupaRepository, prezentacijaRepository, slajdRepository,
                predmetRepository, varijantaService, grupaZadatakaService);
    }

    static List<Object[]> redovi(Object[]... r) {
        return List.of(r);
    }

    /** Red {@code VarijantaRepository.zaPonistavanje}: Klodov neodobren nacrt u grupi "Konverzije". */
    static Object[] nacrt(long id, int rb, long grupaId) {
        return new Object[]{id, rb, null, grupaId, "Konverzije", StatusVarijante.NACRT, IzvorVarijante.CLAUDE, false};
    }

    /** Red {@code GrupaZadatakaRepository.zaPonistavanje}: CLAUDE koren. */
    static Object[] koren(long id) {
        return new Object[]{id, "Grupa " + id, IzvorVarijante.CLAUDE, null, null, null, null, null};
    }

    @Test
    void obrisanoUMedjuvremenuSeNePominje() {
        when(varijantaRepository.zaPonistavanje(SERIJA)).thenReturn(redovi(nacrt(1, 1, 10), nacrt(2, 2, 10)));
        doThrow(new SystemException("Varijanta nije pronađena.", HttpStatus.NOT_FOUND)).when(varijantaService)
                .obrisiSopstveniNacrt(1L);
        when(grupaRepository.zaPonistavanje(SERIJA)).thenReturn(redovi(koren(10), koren(11)));
        doThrow(new SystemException("Grupa nije pronađena.", HttpStatus.NOT_FOUND)).when(grupaZadatakaService)
                .obrisiPraznu(11L);
        // izričito: strogi Mockito inače poziv sa drugim argumentom metode sa stub-om vidi kao grešku u testu
        doNothing().when(varijantaService).obrisiSopstveniNacrt(2L);
        doNothing().when(grupaZadatakaService).obrisiPraznu(10L);

        PonistavanjeInfo info = service.ponisti(SERIJA, true);

        assertTrue(info.potvrdjeno());
        assertEquals(List.of(
                new StavkaPonistavanja(StavkaPonistavanja.VARIJANTA, 2L, "Konverzije, varijanta 2", null),
                new StavkaPonistavanja(StavkaPonistavanja.GRUPA, 10L, "Grupa 10", null)), info.obrisano());
        assertEquals(List.of(), info.preskoceno());
        verify(varijantaService).obrisiSopstveniNacrt(2L);
        verify(grupaZadatakaService).obrisiPraznu(10L);
    }

    @Test
    void konfliktJePreskocenoSaRazlogom() {
        when(varijantaRepository.zaPonistavanje(SERIJA)).thenReturn(redovi(nacrt(1, 1, 10)));
        // Novica je odobrio nacrt posle spiska: pod ključem više nije Klodov nacrt
        doThrow(new SystemException("Klod briše samo svoje neodobrene nacrte.", HttpStatus.CONFLICT)).when(varijantaService)
                .obrisiSopstveniNacrt(1L);
        when(grupaRepository.zaPonistavanje(SERIJA)).thenReturn(redovi(koren(10)));
        doThrow(new SystemException("Briše se samo prazna grupa.", HttpStatus.CONFLICT)).when(grupaZadatakaService)
                .obrisiPraznu(10L);

        PonistavanjeInfo info = service.ponisti(SERIJA, true);

        assertEquals(List.of(), info.obrisano());
        assertEquals(List.of(
                new StavkaPonistavanja(StavkaPonistavanja.VARIJANTA, 1L, "Konverzije, varijanta 1",
                        "Odobrena ili nije Klodov nacrt."),
                new StavkaPonistavanja(StavkaPonistavanja.GRUPA, 10L, "Grupa 10", "Grupa nije prazna.")), info.preskoceno());
    }

    @Test
    void drugaGreskaPrekidaPonistavanje() {
        when(varijantaRepository.zaPonistavanje(SERIJA)).thenReturn(redovi(nacrt(1, 1, 10), nacrt(2, 2, 10)));
        doThrow(new SystemException("Render servis nije dostupan.", HttpStatus.SERVICE_UNAVAILABLE)).when(varijantaService)
                .obrisiSopstveniNacrt(1L);

        SystemException e = assertThrows(SystemException.class, () -> service.ponisti(SERIJA, true));

        assertEquals(503, e.getCode());
        verify(varijantaService, never()).obrisiSopstveniNacrt(2L);
        verifyNoInteractions(grupaZadatakaService);
    }

    @Test
    void bezPotvrdeNistaNeBrise() {
        when(varijantaRepository.zaPonistavanje(SERIJA)).thenReturn(redovi(nacrt(1, 1, 10)));
        when(grupaRepository.zaPonistavanje(SERIJA)).thenReturn(redovi(koren(10)));
        when(varijantaRepository.brojaciPoGrupi(List.of(10L))).thenReturn(redovi(
                new Object[]{10L, StatusVarijante.NACRT, null, 1L}));
        when(grupaRepository.decaGrupa(List.of(10L))).thenReturn(redovi());

        PonistavanjeInfo info = service.ponisti(SERIJA, false);

        assertFalse(info.potvrdjeno());
        assertEquals(List.of(1L, 10L), info.obrisano().stream().map(StavkaPonistavanja::id).toList());
        verifyNoInteractions(varijantaService, grupaZadatakaService);
    }
}
