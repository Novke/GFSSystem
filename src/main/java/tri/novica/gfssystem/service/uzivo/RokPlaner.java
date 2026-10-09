package tri.novica.gfssystem.service.uzivo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Zatvaranje pitanja po roku: {@code zatvoriPoRoku} se izvršava u {@code rok + 1 s} (tolerancija za mrežu). Najviše
 * jedno zakazivanje po rundi (novo zamenjuje staro). Unutar transakcije zakazivanje i otkazivanje važe tek posle
 * commit-a: komanda koja se poništi ne ostavlja otkazan tajmer otvorene runde.
 */
@Component
@Slf4j
public class RokPlaner {

    static final Duration PONOVO_POSLE_GRESKE = Duration.ofSeconds(5);

    private final TaskScheduler scheduler;
    private final ObjectProvider<IzvodjenjeService> izvodjenjeService;
    private final Clock clock;
    private final Map<Long, Zadatak> zakazano = new ConcurrentHashMap<>();

    public RokPlaner(@Qualifier("taskScheduler") TaskScheduler scheduler,
                     ObjectProvider<IzvodjenjeService> izvodjenjeService, Clock clock) {
        this.scheduler = scheduler;
        this.izvodjenjeService = izvodjenjeService;
        this.clock = clock;
    }

    /** Zakazuje {@code zatvoriPoRoku(izvodjenjeId, rundaId)} u {@code rok + 1 s}; zamenjuje ranije zakazivanje runde. */
    public void zakazi(Long izvodjenjeId, Long rundaId, LocalDateTime rok) {
        posleCommita(() -> zakaziUTrenutku(izvodjenjeId, rundaId,
                rok.atZone(clock.getZone()).toInstant().plus(IzvodjenjeService.TOLERANCIJA)));
    }

    public void otkazi(Long rundaId) {
        posleCommita(() -> {
            Zadatak z = zakazano.remove(rundaId);
            if (z != null) z.otkazi();
        });
    }

    /** Broj zakazanih rundi (za test i dijagnostiku). */
    int brojZakazanih() {
        return zakazano.size();
    }

    private void zakaziUTrenutku(Long izvodjenjeId, Long rundaId, Instant kada) {
        Zadatak z = new Zadatak(izvodjenjeId, rundaId);
        Zadatak stari = zakazano.put(rundaId, z);
        if (stari != null) stari.otkazi();
        z.buducnost = scheduler.schedule(z, kada);
    }

    private static void posleCommita(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    private final class Zadatak implements Runnable {
        final Long izvodjenjeId;
        final Long rundaId;
        volatile ScheduledFuture<?> buducnost;
        volatile boolean otkazan;

        Zadatak(Long izvodjenjeId, Long rundaId) {
            this.izvodjenjeId = izvodjenjeId;
            this.rundaId = rundaId;
        }

        void otkazi() {
            otkazan = true;
            ScheduledFuture<?> f = buducnost;
            if (f != null) f.cancel(false);
        }

        @Override
        public void run() {
            if (otkazan) return;
            zakazano.remove(rundaId, this);
            try {
                izvodjenjeService.getObject().zatvoriPoRoku(izvodjenjeId, rundaId);
            } catch (RuntimeException e) {
                // npr. baza nedostupna ili deadlock: pokušaj ponovo, runda ne sme ostati otvorena zauvek
                log.warn("Zatvaranje po roku nije uspelo (izvodjenje={}, runda={}), ponovo za {} s",
                        izvodjenjeId, rundaId, PONOVO_POSLE_GRESKE.toSeconds(), e);
                zakazano.computeIfAbsent(rundaId, id -> {
                    Zadatak z = new Zadatak(izvodjenjeId, rundaId);
                    z.buducnost = scheduler.schedule(z, clock.instant().plus(PONOVO_POSLE_GRESKE));
                    return z;
                });
            }
        }
    }
}
