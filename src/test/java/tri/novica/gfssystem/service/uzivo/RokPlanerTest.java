package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class RokPlanerTest {

    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final LocalDateTime ROK = LocalDateTime.of(2026, 10, 7, 12, 0, 20);

    final TaskScheduler scheduler = mock(TaskScheduler.class);
    final IzvodjenjeService servis = mock(IzvodjenjeService.class);
    final ObjectProvider<IzvodjenjeService> provider = mock(ObjectProvider.class);
    final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 10, 7, 12, 0).atZone(ZONA).toInstant(), ZONA);
    final RokPlaner planer;

    RokPlanerTest() {
        when(provider.getObject()).thenReturn(servis);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(i -> mock(ScheduledFuture.class));
        planer = new RokPlaner(scheduler, provider, clock);
    }

    Runnable zakazano(int puta, Instant kada) {
        ArgumentCaptor<Runnable> c = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(puta)).schedule(c.capture(), eq(kada));
        return c.getValue();
    }

    static <T> T eq(T v) {
        return org.mockito.ArgumentMatchers.eq(v);
    }

    @Test
    void zakaziUJednojSekundiPosleRokaIZatvara() {
        planer.zakazi(5L, 7L, ROK);
        Runnable r = zakazano(1, ROK.plusSeconds(1).atZone(ZONA).toInstant());
        assertEquals(1, planer.brojZakazanih());
        r.run();
        verify(servis).zatvoriPoRoku(5L, 7L);
        assertEquals(0, planer.brojZakazanih());
    }

    @Test
    void novoZakazivanjeOtkazujeStaro() {
        ScheduledFuture<?> prvi = mock(ScheduledFuture.class);
        doReturn(prvi).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        planer.zakazi(5L, 7L, ROK);
        ArgumentCaptor<Runnable> c = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(c.capture(), any(Instant.class));
        Runnable stari = c.getValue();

        planer.zakazi(5L, 7L, ROK.plusSeconds(10));
        verify(prvi).cancel(false);
        assertEquals(1, planer.brojZakazanih());
        stari.run();   // otkazan zadatak koji je ipak krenuo ne radi ništa
        verifyNoInteractions(servis);
    }

    @Test
    void otkazi() {
        ScheduledFuture<?> f = mock(ScheduledFuture.class);
        doReturn(f).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        planer.zakazi(5L, 7L, ROK);
        planer.otkazi(7L);
        verify(f).cancel(false);
        assertEquals(0, planer.brojZakazanih());
        planer.otkazi(99L);   // nepoznata runda: ništa
    }

    @Test
    void greskaSePonavljaPosle5s() {
        doThrow(new RuntimeException("baza")).when(servis).zatvoriPoRoku(5L, 7L);
        planer.zakazi(5L, 7L, ROK);
        Runnable r = zakazano(1, ROK.plusSeconds(1).atZone(ZONA).toInstant());
        r.run();
        verify(scheduler).schedule(any(Runnable.class), eq(clock.instant().plusSeconds(5)));
        assertEquals(1, planer.brojZakazanih());
    }

    @Test
    void uTransakcijiTekPosleCommita() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            planer.zakazi(5L, 7L, ROK);
            verifyNoInteractions(scheduler);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
