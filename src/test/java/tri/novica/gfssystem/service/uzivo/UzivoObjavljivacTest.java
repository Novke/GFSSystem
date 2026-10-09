package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import tri.novica.gfssystem.dto.uzivo.JavnoStanje;
import tri.novica.gfssystem.dto.uzivo.LicnoStanje;
import tri.novica.gfssystem.dto.uzivo.NastavnickoStanje;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Objavljivanje (bez Springa): promena šalje sve iz jednog čitanja; odgovori se skupljaju i šalju u flush-u jednim
 * čitanjem po izvođenju, lično stanje samo onima koji su odgovorili; greška jednog izvođenja ne zaustavlja ostala.
 */
class UzivoObjavljivacTest {

    SimpMessageSendingOperations poruke;
    StanjeService stanje;
    PovezanostRegistar povezanost;
    UzivoObjavljivac objavljivac;

    @BeforeEach
    void setUp() {
        poruke = mock(SimpMessageSendingOperations.class);
        stanje = mock(StanjeService.class);
        povezanost = new PovezanostRegistar();
        objavljivac = new UzivoObjavljivac(poruke, stanje, povezanost);
    }

    static LicnoStanje licno(long ucesnikId, boolean izbacen) {
        return new LicnoStanje(3, ucesnikId, "U" + ucesnikId, 0, null, 3, izbacen, null);
    }

    StanjeService.Snimci snimci(long... ucesnici) {
        Map<Long, LicnoStanje> licna = new LinkedHashMap<>();
        for (long u : ucesnici) licna.put(u, licno(u, false));
        return new StanjeService.Snimci(mock(JavnoStanje.class), licna, mock(NastavnickoStanje.class));
    }

    @Test
    void promenaSaljeJavnoSvaLicnaINastavnickoIzJednogCitanja() {
        StanjeService.Snimci s = snimci(1, 2, 3);
        when(stanje.snimci(7L)).thenReturn(s);

        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));

        verify(stanje, times(1)).snimci(7L);
        verify(poruke).convertAndSend("/topic/izvodjenja/7/javno", s.javno());
        verify(poruke).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
        for (long u : new long[]{1, 2, 3}) {
            verify(poruke).convertAndSendToUser("u-" + u, "/queue/licno", s.licna().get(u));
        }
    }

    @Test
    void odgovoriSeSkupljajuUJedanFlush() {
        StanjeService.Snimci s = snimci(1, 2, 3, 4);
        when(stanje.snimci(7L)).thenReturn(s);

        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 1L));
        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 3L));
        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 3L));
        verifyNoInteractions(stanje, poruke);

        objavljivac.flush();

        verify(stanje, times(1)).snimci(7L);
        verify(poruke, times(1)).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
        verify(poruke).convertAndSendToUser("u-1", "/queue/licno", s.licna().get(1L));
        verify(poruke).convertAndSendToUser("u-3", "/queue/licno", s.licna().get(3L));
        verify(poruke, times(2)).convertAndSendToUser(anyString(), anyString(), any());
        verify(poruke, never()).convertAndSend(eq("/topic/izvodjenja/7/javno"), any(Object.class));
        assertTrue(objavljivac.zaprljana().isEmpty());

        // sledeći flush bez novih odgovora ne čita ništa
        objavljivac.flush();
        verify(stanje, times(1)).snimci(7L);
    }

    @Test
    void promenaPokrivaZaprljanoDoTada() {
        when(stanje.snimci(7L)).thenReturn(snimci(1));
        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 1L));

        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        objavljivac.flush();

        verify(stanje, times(1)).snimci(7L);
    }

    @Test
    void greskaJednogIzvodjenjaNeZaustavljaOstala() {
        when(stanje.snimci(7L)).thenThrow(new IllegalStateException("baza"));
        when(stanje.snimci(8L)).thenThrow(new SystemException("nema", HttpStatus.NOT_FOUND));
        StanjeService.Snimci s9 = snimci(5);
        when(stanje.snimci(9L)).thenReturn(s9);
        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 1L));
        objavljivac.onOdgovor(new OdgovorPrimljen(8L, 2L));
        objavljivac.onOdgovor(new OdgovorPrimljen(9L, 5L));

        assertDoesNotThrow(objavljivac::flush);

        verify(poruke).convertAndSendToUser("u-5", "/queue/licno", s9.licna().get(5L));
        verify(poruke).convertAndSend("/topic/izvodjenja/9/nastavnik", s9.nastavnicko());
        assertTrue(objavljivac.zaprljana().isEmpty());
        assertDoesNotThrow(() -> objavljivac.onPromena(new IzvodjenjePromenjeno(7L)));
    }

    @Test
    void neuspelaPorukaJednomUcesnikuNeZaustavljaOstale() {
        StanjeService.Snimci s = snimci(1, 2);
        when(stanje.snimci(7L)).thenReturn(s);
        doThrow(new IllegalStateException("kanal")).when(poruke)
                .convertAndSendToUser(eq("u-1"), anyString(), any());

        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));

        verify(poruke).convertAndSendToUser("u-2", "/queue/licno", s.licna().get(2L));
        verify(poruke).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
    }

    @Test
    void izbacenomIdeLicnoStanjeSaIzbacen() {
        LicnoStanje izbacen = licno(4, true);
        when(stanje.licno(7L, 4L)).thenReturn(izbacen);

        objavljivac.onIzbacen(new UcesnikIzbacen(7L, 4L));

        verify(poruke).convertAndSendToUser("u-4", "/queue/licno", izbacen);
    }

    @Test
    void nepoznatIzbacenNijeGreska() {
        when(stanje.licno(7L, 4L)).thenThrow(new SystemException("Učesnik nije pronađen.", HttpStatus.NOT_FOUND));

        assertDoesNotThrow(() -> objavljivac.onIzbacen(new UcesnikIzbacen(7L, 4L)));
        verifyNoInteractions(poruke);
    }
}
