package tri.novica.gfssystem.service.mejl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pun izvršilac (jedna nit, bez reda, sporo slanje) ne sme da baci izuzetak u pozivaoca {@code commit()}: transakcija je
 * tada već potvrđena, pa bi zahtev dobio 500 uz sačuvan kod, a mejl bi nestao.
 */
@SpringBootTest
@Import(LazniMejlServis.class)
@TestPropertySource(properties = {"gfs.mail.izvrsilac.niti=1", "gfs.mail.izvrsilac.red=0"})
@ExtendWith(OutputCaptureExtension.class)
class MejlPreopterecenIzvrsilacIT {

    static final MejlPoruka PORUKA = new MejlPoruka("student@example.test", "Kod za prijavu", "Tvoj kod je 654321.");

    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager tm;
    @Autowired LazniMejlServis lazni;

    @BeforeEach
    void ocisti() {
        lazni.ocisti();
    }

    @Test
    void punIzvrsilac_commitNeBacaIzuzetak_poruka_seOdbacujeBezAdrese(CapturedOutput izlaz) {
        lazni.kasnjenje(Duration.ofSeconds(2));
        TransactionTemplate tx = new TransactionTemplate(tm);

        // prva poruka zauzme jedinu nit, ostale ne mogu u red
        assertDoesNotThrow(() -> tx.executeWithoutResult(s -> publisher.publishEvent(PORUKA)));
        pauza(200);
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> tx.executeWithoutResult(s -> publisher.publishEvent(PORUKA)), "commit je bacio izuzetak");
        }
        assertDoesNotThrow(() -> publisher.publishEvent(PORUKA), "publishEvent bez transakcije je bacio izuzetak");

        assertEquals(1, lazni.cekaj(1, Duration.ofSeconds(10)).size());
        pauza(300);
        assertEquals(1, lazni.poslate().size(), "odbačene poruke se ne šalju");
        String log = izlaz.getAll();
        assertTrue(log.contains("red za slanje je pun"));
        assertFalse(log.contains("student@example.test"));
        assertFalse(log.contains("654321"));
    }

    static void pauza(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
