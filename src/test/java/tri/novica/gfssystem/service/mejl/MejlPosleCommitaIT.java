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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mejl ide tek posle commit-a i van pozivaočeve niti. Pauze ponavljanja su u testu 20 ms ({@code gfs.mail.pauze-ms}).
 */
@SpringBootTest
@Import(LazniMejlServis.class)
@ExtendWith(OutputCaptureExtension.class)
class MejlPosleCommitaIT {

    static final MejlPoruka PORUKA = new MejlPoruka("student@example.test", "Kod za prijavu", "Tvoj kod je 654321.");

    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager tm;
    @Autowired LazniMejlServis lazni;
    @Autowired MejlServis mejl;
    @Autowired SmtpMejlServis smtp;

    @BeforeEach
    void ocisti() {
        lazni.ocisti();
    }

    @Test
    void laznaImplementacijaJePodrazumevana() {
        assertSame(lazni, mejl);
    }

    @Test
    void poruka_objavljenaUTransakcijiSeSaljeTekPosleCommita() {
        new TransactionTemplate(tm).executeWithoutResult(status -> {
            publisher.publishEvent(PORUKA);
            pauza(150);
            assertTrue(lazni.poslate().isEmpty(), "ne sme da ode pre commit-a");
        });

        assertEquals(List.of(PORUKA), lazni.cekaj(1));
    }

    @Test
    void poruka_objavljenaUTransakcijiKojaSeVraca_neSaljeSe() {
        new TransactionTemplate(tm).executeWithoutResult(status -> {
            publisher.publishEvent(PORUKA);
            status.setRollbackOnly();
        });

        pauza(300);
        assertTrue(lazni.poslate().isEmpty());
        assertEquals(0, lazni.pokusaja());
    }

    @Test
    void poruka_objavljenaBezTransakcije_saljeSe() {
        publisher.publishEvent(PORUKA);

        assertEquals(List.of(PORUKA), lazni.cekaj(1));
    }

    @Test
    void slanjeJeAsinhrono_pozivalacNeCekaMejl() {
        lazni.kasnjenje(Duration.ofMillis(600));

        long pocetak = System.nanoTime();
        new TransactionTemplate(tm).executeWithoutResult(status -> publisher.publishEvent(PORUKA));
        long trajanjeMs = (System.nanoTime() - pocetak) / 1_000_000;

        assertTrue(trajanjeMs < 500, "commit je čekao mejl: " + trajanjeMs + " ms");
        assertTrue(lazni.poslate().isEmpty(), "mejl još ne sme da bude poslat");
        assertEquals(1, lazni.cekaj(1).size());
    }

    @Test
    void neuspehSePonavlja_drugiPokusajProlazi(CapturedOutput izlaz) {
        lazni.neuspeh(1);

        publisher.publishEvent(PORUKA);

        assertEquals(1, lazni.cekaj(1).size());
        assertEquals(2, lazni.pokusaja());
        assertTrue(izlaz.getAll().contains("Mejl nije poslat (pokušaj 1/3)"));
    }

    @Test
    void posleTriNeuspelaPokusajaOdustaje_logBezAdreseINaslovaITela(CapturedOutput izlaz) {
        lazni.neuspeh(5);

        publisher.publishEvent(PORUKA);

        long kraj = System.currentTimeMillis() + 5000;
        while (lazni.pokusaja() < 3 && System.currentTimeMillis() < kraj) {
            pauza(10);
        }
        pauza(150);
        assertEquals(3, lazni.pokusaja(), "tačno tri pokušaja");
        assertTrue(lazni.poslate().isEmpty());
        String log = izlaz.getAll();
        assertTrue(log.contains("Mejl nije poslat (pokušaj 3/3)"));
        assertFalse(log.contains("student@example.test"));
        assertFalse(log.contains("654321"));
        assertFalse(log.contains("Kod za prijavu"));
    }

    @Test
    void kadMejlNijeDostupan_nistaSeNeSalje() {
        lazni.dostupan(false);

        publisher.publishEvent(PORUKA);

        pauza(300);
        assertEquals(0, lazni.pokusaja());
    }

    @Test
    void prazanHostUTestuJeIskljucenSmtp() {
        // pravi SmtpMejlServis iz testnog konteksta (spring.mail.host i gfs.mail.from prazni) nije dostupan
        assertFalse(smtp.dostupan());
    }

    static void pauza(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
