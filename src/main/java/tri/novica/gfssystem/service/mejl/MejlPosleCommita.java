package tri.novica.gfssystem.service.mejl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Šalje {@link MejlPoruka} tek posle commit-a transakcije u kojoj je objavljena ({@code publishEvent}), u posebnoj niti
 * ({@code mejlIzvrsilac}): zahtev ne čeka SMTP, pa odgovor za nepostojeći i postojeći nalog traje isto, a vraćena
 * (rollback) transakcija ne šalje ništa. Bez transakcije ({@code fallbackExecution}) šalje se odmah (asinhrono).
 * Do 3 pokušaja ukupno, pauze 2 s i 10 s ({@code gfs.mail.pauze-ms}, u testu kratke). Log nikad ne nosi adresu, naslov ni telo.
 */
@Component
@Slf4j
public class MejlPosleCommita {

    private final MejlServis mejl;
    private final long[] pauzeMs;

    public MejlPosleCommita(MejlServis mejl, @Value("${gfs.mail.pauze-ms:2000,10000}") long[] pauzeMs) {
        this.mejl = mejl;
        this.pauzeMs = pauzeMs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("mejlIzvrsilac")
    public void posalji(MejlPoruka poruka) {
        posaljiSaPokusajima(poruka);
    }

    /**
     * Isto slanje (do 3 pokušaja), sinhrono u niti pozivaoca: za izvršioce koji poruke primaju direktno
     * ({@code mejlStudentimaIzvrsilac}). Bez {@code @Async}, pa poziv kroz proksi ne menja nit.
     */
    public void posaljiSaPokusajima(MejlPoruka poruka) {
        if (!mejl.dostupan()) {
            log.warn("Mejl nije poslat: slanje nije podešeno");
            return;
        }
        int ukupno = pauzeMs.length + 1;
        for (int pokusaj = 1; pokusaj <= ukupno; pokusaj++) {
            try {
                mejl.posalji(poruka);
                return;
            } catch (RuntimeException e) {
                // samo klasa izuzetka: poruka izuzetka može da nosi adresu
                log.warn("Mejl nije poslat (pokušaj {}/{}): {}", pokusaj, ukupno, e.getClass().getSimpleName());
                if (pokusaj < ukupno && !pauziraj(pauzeMs[pokusaj - 1])) {
                    return;
                }
            }
        }
    }

    private boolean pauziraj(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
