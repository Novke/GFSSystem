package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tri.novica.gfssystem.repository.prijava.KljucAplikacijeRepository;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Ključ aplikacije se pravi pri prvoj upotrebi, jednom (UNIQUE), i posle se samo čita (spec 9). */
@SpringBootTest
@Import({PrijavaSatConfig.class, LazniMejlServis.class})
class KljuceviAplikacijeIT {

    @Autowired KljucAplikacijeRepository repository;
    @Autowired PlatformTransactionManager transakcije;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    final String naziv = "T7_" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void ocisti() {
        jdbc.update("delete from kljucevi_aplikacije where naziv = ?", naziv);
    }

    int redova() {
        return jdbc.queryForObject("select count(*) from kljucevi_aplikacije where naziv = ?", Integer.class, naziv);
    }

    @Test
    void praviSeJednomIPosleCita() {
        byte[] a = new KljuceviAplikacije(repository, transakcije, clock).kljuc(naziv);
        assertEquals(32, a.length);
        assertEquals(1, redova());
        String vrednost = jdbc.queryForObject("select vrednost from kljucevi_aplikacije where naziv = ?", String.class, naziv);
        assertArrayEquals(a, Base64.getDecoder().decode(vrednost));
        // druga instanca (npr. posle restarta) čita isti
        assertArrayEquals(a, new KljuceviAplikacije(repository, transakcije, clock).kljuc(naziv));
        assertEquals(1, redova());
    }

    /** Druga instanca je ključ upisala između našeg čitanja i upisa: UNIQUE odbija naš red, čita se njen. */
    @Test
    void trkaDajeDataIntegrityViolationPaPonovnoCitanje() {
        byte[] pobednik = new KljuceviAplikacije(repository, transakcije, clock).kljuc(naziv);
        AtomicBoolean prvoCitanje = new AtomicBoolean(true);
        KljucAplikacijeRepository zakasneli = (KljucAplikacijeRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{KljucAplikacijeRepository.class}, (p, m, args) -> {
                    if (m.getName().equals("findByNaziv") && prvoCitanje.getAndSet(false)) {
                        return Optional.empty();   // pročitao pre nego što je pobednik upisao
                    }
                    try {
                        return m.invoke(repository, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        byte[] gubitnik = new KljuceviAplikacije(zakasneli, transakcije, clock).kljuc(naziv);
        assertFalse(prvoCitanje.get());
        assertArrayEquals(pobednik, gubitnik);
        assertEquals(1, redova());
    }

    @Test
    void dveInstanceIstovremenoDobijajuIstiKljuc() throws Exception {
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> a = niti.submit(() -> {
                kreni.await();
                return new KljuceviAplikacije(repository, transakcije, clock).kljuc(naziv);
            });
            Future<byte[]> b = niti.submit(() -> {
                kreni.await();
                return new KljuceviAplikacije(repository, transakcije, clock).kljuc(naziv);
            });
            kreni.countDown();
            assertArrayEquals(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));
            assertEquals(1, redova());
        } finally {
            niti.shutdownNow();
        }
    }
}
