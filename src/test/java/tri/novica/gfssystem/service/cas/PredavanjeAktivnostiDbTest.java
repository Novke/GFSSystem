package tri.novica.gfssystem.service.cas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.predavanje.UpdatePredavanjeCmd;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.service.PredavanjeService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ručne izmene aktivnosti na predavanju uz čekiranje (spec studentskog dela 4.5), nad pravom bazom: uklanjanje prisustva
 * briše čekiranje, ručno dodavanje ga ne pravi, ručno dodavanje već prisutnog je bez greške, završetak briše tajnu, a
 * izmene čekaju zaključan red predavanja.
 */
@SpringBootTest
class PredavanjeAktivnostiDbTest {

    @Autowired PredavanjeService predavanjeService;
    @Autowired CekiranjeService cekiranje;
    @Autowired PredavanjeRepository predavanjeRepository;
    @Autowired PlatformTransactionManager tm;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    CasPodaci d;
    Long predavanje;
    Long ana;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        Long grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now(clock));
        ana = d.student(grupa, "Ana");
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    @Test
    void uklanjanjePrisustvaBriseCekiranje() {
        cekiranje.skeniraj(cekiranje.zaNastavnika(predavanje).token(), Optional.of(ana), null);
        assertEquals(1, d.brojCekiranja(predavanje));
        predavanjeService.skloniPrisutnog(predavanje, ana);
        assertEquals(0, d.brojCekiranja(predavanje));
        assertTrue(d.aktivnosti(predavanje, ana).isEmpty());
        assertEquals(0, d.posecenost(predavanje));
    }

    @Test
    void rucnoDodavanjeNePraviCekiranje() {
        predavanjeService.dodajPrisutnog(predavanje, ana);
        assertEquals(0, d.brojCekiranja(predavanje));
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ana));
    }

    @Test
    void rucnoDodavanjeCekiranogBezGreske() {
        cekiranje.skeniraj(cekiranje.zaNastavnika(predavanje).token(), Optional.of(ana), null);
        assertDoesNotThrow(() -> predavanjeService.dodajPrisutnog(predavanje, ana));
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ana));
        assertEquals(1, d.posecenost(predavanje));
    }

    @Test
    void zavrsetakBriseTajnu() {
        cekiranje.zaNastavnika(predavanje);
        assertNotNull(d.tajna(predavanje));
        predavanjeService.zavrsiPredavanje(predavanje);
        assertNull(d.tajna(predavanje));
        assertNull(jdbc.queryForObject("select cekiranje_tajna_od from predavanja where id = ?", Object.class, predavanje));
    }

    @Test
    void zakljucavaRedPredavanja() throws Exception {
        cekaZakljucanRed(() -> predavanjeService.dodajPrisutnog(predavanje, ana));
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ana));
    }

    @Test
    void izmenaIPosecenostCekajuZakljucanRed() throws Exception {
        // bezbednosni pregled: ceo red bi inače vratio staro stanje (npr. otvorio završeno predavanje ili staru tajnu)
        UpdatePredavanjeCmd cmd = new UpdatePredavanjeCmd();
        cmd.setTema("Nova tema");
        cekaZakljucanRed(() -> predavanjeService.updatePredavanje(predavanje, cmd));
        cekaZakljucanRed(() -> predavanjeService.updatePosecenost(predavanje));
        assertEquals("Nova tema", jdbc.queryForObject("select tema from predavanja where id = ?", String.class, predavanje));
    }

    /** {@code radnja} u drugoj niti počinje dok jedna transakcija drži red predavanja i završava tek posle nje. */
    private void cekaZakljucanRed(Runnable radnja) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(2);
        AtomicLong pustioU = new AtomicLong();
        AtomicLong dodaoU = new AtomicLong();
        CountDownLatch zakljucano = new CountDownLatch(1);
        try {
            Future<?> drzi = ex.submit(() -> new TransactionTemplate(tm).executeWithoutResult(st -> {
                predavanjeRepository.findByIdForUpdate(predavanje).orElseThrow();
                zakljucano.countDown();
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                pustioU.set(System.nanoTime());
            }));
            assertTrue(zakljucano.await(30, TimeUnit.SECONDS));
            Future<?> dodaj = ex.submit(() -> {
                radnja.run();
                dodaoU.set(System.nanoTime());
            });
            drzi.get(30, TimeUnit.SECONDS);
            dodaj.get(30, TimeUnit.SECONDS);
        } finally {
            ex.shutdownNow();
        }
        assertTrue(dodaoU.get() > pustioU.get(), "izmena je završila pre nego što je zaključavanje pušteno");
    }
}
