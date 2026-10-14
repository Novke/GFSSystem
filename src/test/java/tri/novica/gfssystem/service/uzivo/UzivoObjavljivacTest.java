package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import tri.novica.gfssystem.dto.uzivo.JavnoStanje;
import tri.novica.gfssystem.dto.uzivo.LicnoStanje;
import tri.novica.gfssystem.dto.uzivo.NastavnickoStanje;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Objavljivanje (bez Springa). Slušaoci (nit zahteva posle commit-a) nikad ne čitaju bazu: samo zaprljaju i, za
 * komandu, zakažu obradu na niti objavljivača. Prijave i odgovori se skupljaju u flush (jedno čitanje po izvođenju,
 * bez obzira na broj prijava); greška jednog izvođenja ne zaustavlja ostala.
 */
class UzivoObjavljivacTest {

    SimpMessageSendingOperations poruke;
    StanjeService stanje;
    PovezanostRegistar povezanost;
    final List<Runnable> zakazano = new ArrayList<>();
    Executor izvrsilac = zakazano::add;
    UzivoObjavljivac objavljivac;

    @BeforeEach
    void setUp() {
        poruke = mock(SimpMessageSendingOperations.class);
        stanje = mock(StanjeService.class);
        povezanost = new PovezanostRegistar();
        objavljivac = new UzivoObjavljivac(poruke, stanje, povezanost, r -> izvrsilac.execute(r));
    }

    static LicnoStanje licno(long ucesnikId, boolean izbacen) {
        return new LicnoStanje(3, ucesnikId, "U" + ucesnikId, 0, null, 3, izbacen, null);
    }

    StanjeService.Snimci snimci(long... ucesnici) {
        Map<Long, LicnoStanje> licna = new LinkedHashMap<>();
        for (long u : ucesnici) licna.put(u, licno(u, false));
        return new StanjeService.Snimci(mock(JavnoStanje.class), licna, mock(NastavnickoStanje.class));
    }

    void pokreniZakazano() {
        List<Runnable> sada = List.copyOf(zakazano);
        zakazano.clear();
        sada.forEach(Runnable::run);
    }

    @Test
    void komandaSeObjavljujeOdmahNaNitiObjavljivacaIzJednogCitanja() {
        StanjeService.Snimci s = snimci(1, 2, 3);
        when(stanje.snimci(7L)).thenReturn(s);

        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        // nit zahteva: ni čitanja ni slanja, samo zakazana obrada
        verifyNoInteractions(stanje, poruke);
        assertEquals(1, zakazano.size());

        pokreniZakazano();

        verify(stanje, times(1)).snimci(7L);
        verify(poruke).convertAndSend("/topic/izvodjenja/7/javno", s.javno());
        verify(poruke).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
        for (long u : new long[]{1, 2, 3}) {
            verify(poruke).convertAndSendToUser("u-" + u, "/queue/licno", s.licna().get(u));
        }
        assertTrue(objavljivac.zaprljana().isEmpty());
    }

    @Test
    void talasPrijavaJeJednoCitanjePoFlushu() {
        long[] ucesnici = new long[300];
        for (int i = 0; i < ucesnici.length; i++) ucesnici[i] = i + 1;
        StanjeService.Snimci s = snimci(ucesnici);
        when(stanje.snimci(7L)).thenReturn(s);

        for (int i = 0; i < 300; i++) {
            objavljivac.onPrijava(new UcesnikPrijavljen(7L));
        }
        verifyNoInteractions(stanje, poruke);
        assertTrue(zakazano.isEmpty(), "prijava ne zakazuje odmah, čeka flush");

        objavljivac.flush();

        verify(stanje, times(1)).snimci(7L);
        verify(poruke, times(1)).convertAndSend("/topic/izvodjenja/7/javno", s.javno());
        verify(poruke, times(1)).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
        verify(poruke, times(300)).convertAndSendToUser(anyString(), eq("/queue/licno"), any());

        objavljivac.flush();
        verify(stanje, times(1)).snimci(7L);
    }

    @Test
    void odgovoriSeSkupljajuUJedanFlush() {
        StanjeService.Snimci s = snimci(1, 2, 3, 4);
        when(stanje.snimci(7L)).thenReturn(s);

        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 1L));
        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 3L));
        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 3L));
        verifyNoInteractions(stanje, poruke);
        assertTrue(zakazano.isEmpty());

        objavljivac.flush();

        verify(stanje, times(1)).snimci(7L);
        verify(poruke, times(1)).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
        verify(poruke).convertAndSendToUser("u-1", "/queue/licno", s.licna().get(1L));
        verify(poruke).convertAndSendToUser("u-3", "/queue/licno", s.licna().get(3L));
        verify(poruke, times(2)).convertAndSendToUser(anyString(), anyString(), any());
        verify(poruke, never()).convertAndSend(eq("/topic/izvodjenja/7/javno"), any(Object.class));
        assertTrue(objavljivac.zaprljana().isEmpty());

        objavljivac.flush();
        verify(stanje, times(1)).snimci(7L);
    }

    @Test
    void obradaPokrivaSveZaprljanoDoTada() {
        when(stanje.snimci(7L)).thenReturn(snimci(1));
        objavljivac.onOdgovor(new OdgovorPrimljen(7L, 1L));
        objavljivac.onPrijava(new UcesnikPrijavljen(7L));
        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));

        pokreniZakazano();
        objavljivac.flush();

        verify(stanje, times(1)).snimci(7L);
    }

    @Test
    void neuspeloZakazivanjeOstajeZaFlush() {
        izvrsilac = r -> {
            throw new RejectedExecutionException("ugašen");
        };
        when(stanje.snimci(7L)).thenReturn(snimci(1));

        assertDoesNotThrow(() -> objavljivac.onPromena(new IzvodjenjePromenjeno(7L)));
        objavljivac.flush();

        verify(stanje, times(1)).snimci(7L);
        verify(poruke).convertAndSend(eq("/topic/izvodjenja/7/javno"), any(Object.class));
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
        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        assertDoesNotThrow(this::pokreniZakazano);
    }

    @Test
    void neuspelaPorukaJednomUcesnikuNeZaustavljaOstale() {
        StanjeService.Snimci s = snimci(1, 2);
        when(stanje.snimci(7L)).thenReturn(s);
        doThrow(new IllegalStateException("kanal")).when(poruke)
                .convertAndSendToUser(eq("u-1"), anyString(), any());

        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        pokreniZakazano();

        verify(poruke).convertAndSendToUser("u-2", "/queue/licno", s.licna().get(2L));
        verify(poruke).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
    }

    @Test
    void izbacenomIdeLicnoStanjeSaIzbacenOdmah() {
        LicnoStanje izbacen = licno(4, true);
        when(stanje.licno(7L, 4L)).thenReturn(izbacen);
        when(stanje.snimci(7L)).thenReturn(snimci(1));

        objavljivac.onIzbacen(new UcesnikIzbacen(7L, 4L));
        verifyNoInteractions(stanje, poruke);

        pokreniZakazano();
        verify(poruke).convertAndSendToUser("u-4", "/queue/licno", izbacen);
    }

    @Test
    void nepoznatIzbacenNijeGreska() {
        when(stanje.licno(7L, 4L)).thenThrow(new SystemException("Učesnik nije pronađen.", HttpStatus.NOT_FOUND));
        StanjeService.Snimci s = snimci(1);
        when(stanje.snimci(7L)).thenReturn(s);

        objavljivac.onIzbacen(new UcesnikIzbacen(7L, 4L));
        assertDoesNotThrow(this::pokreniZakazano);
        verify(poruke, never()).convertAndSendToUser(eq("u-4"), anyString(), any());
        verify(poruke).convertAndSend("/topic/izvodjenja/7/nastavnik", s.nastavnicko());
    }

    @Test
    void brzeKomandeZakazujuNajviseJedanZadatakPoIzvodjenju() {
        StanjeService.Snimci s = snimci(1);
        when(stanje.snimci(7L)).thenReturn(s);
        when(stanje.snimci(8L)).thenReturn(snimci(2));

        for (int i = 0; i < 20; i++) {
            objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
            objavljivac.onIzbacen(new UcesnikIzbacen(7L, 100L + i));
        }
        objavljivac.onPromena(new IzvodjenjePromenjeno(8L));
        assertEquals(2, zakazano.size(), "jedan zadatak po izvođenju");
        assertEquals(2, objavljivac.brojZakazanih());

        pokreniZakazano();

        // poslednje stanje je objavljeno, jednim čitanjem
        verify(stanje, times(1)).snimci(7L);
        verify(poruke).convertAndSend("/topic/izvodjenja/7/javno", s.javno());
        verify(stanje, times(20)).licno(eq(7L), anyLong());
        assertEquals(0, objavljivac.brojZakazanih());

        // posle obrade nova komanda opet zakazuje
        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        assertEquals(1, zakazano.size());
    }

    @Test
    void okidacTokomObradeNijeIzgubljen() {
        StanjeService.Snimci s = snimci(1);
        // dok zakazani zadatak čita stanje, stiže nova komanda
        when(stanje.snimci(7L)).thenAnswer(inv -> {
            if (zakazano.isEmpty()) objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
            return s;
        }).thenReturn(s);

        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        pokreniZakazano();
        assertEquals(1, zakazano.size(), "komanda tokom obrade zakazuje novi zadatak");
        pokreniZakazano();

        verify(stanje, times(2)).snimci(7L);
        assertTrue(objavljivac.zaprljana().isEmpty());
        assertEquals(0, objavljivac.brojZakazanih());
    }

    @Test
    void flushNeSkidaOznakuZakazanogZadatka() {
        when(stanje.snimci(7L)).thenReturn(snimci(1));
        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        objavljivac.flush();
        objavljivac.onPromena(new IzvodjenjePromenjeno(7L));
        assertEquals(1, zakazano.size(), "zadatak je još na čekanju i pokupiće novu komandu");
        pokreniZakazano();
        verify(stanje, times(2)).snimci(7L);
        assertTrue(objavljivac.zaprljana().isEmpty());
    }
}
