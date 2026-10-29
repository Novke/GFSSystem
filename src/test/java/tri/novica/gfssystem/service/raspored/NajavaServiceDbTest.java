package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tri.novica.gfssystem.dto.raspored.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.mejl.MejlPoruka;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Najava odsustva nad pravom MySQL bazom (spec najave odsustva, odeljci 4 i 5): naredni časovi studenta, kome stavka
 * važi (svoja grupa, starija generacija po aktivnosti ili čekiranju), najava i povlačenje, provere (tuđ čas, datum koji
 * nije termin, počeo, otkazan, predug razlog), prag i jedno obaveštenje i kad najave stižu paralelno.
 */
@SpringBootTest
@TestPropertySource(properties = "gfs.nastavnik.mejl=nastavnik@example.test")
@Import({RasporedSatConfig.class, LazniMejlServis.class})
class NajavaServiceDbTest {

    /** Sreda 6. 11. 2030, 8:00; čas sredom 10-13. */
    static final LocalDateTime SADA = LocalDateTime.of(2030, 11, 6, 8, 0);
    static final LocalDate DANAS = SADA.toLocalDate();
    static final LocalDate OD = LocalDate.of(2030, 10, 1);
    static final LocalDate DO = LocalDate.of(2031, 1, 31);

    @Autowired RasporedService rasporedi;
    @Autowired NajavaService najave;
    @Autowired TerminService termini;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired LazniMejlServis mejl;
    @Autowired JdbcTemplate jdbc;

    RasporedPodaci d;
    Long grupa;
    Long starija;
    Long druga;
    Long stavka;
    Long ana;

    @BeforeEach
    void setUp() {
        sat.postavi(SADA);
        mejl.ocisti();
        jdbc.update("delete from pokusaji where vrsta in ('MEJL_NASTAVNIKU', 'MEJL_NASTAVNIKU_UKUPNO')");
        d = new RasporedPodaci(jdbc, rasporedi);
        grupa = d.cas.grupa(2030);
        starija = d.cas.grupa(2029);
        druga = d.cas.grupa(2030);
        stavka = d.period(OD, DO, 2, d.stavka(grupa, 3, "10:00", "13:00")).stavke().get(0).id();
        ana = d.cas.student(grupa, "Ana");
    }

    @AfterEach
    void tearDown() {
        mejl.ocisti();
        d.obrisi();
        jdbc.update("delete from pokusaji where vrsta in ('MEJL_NASTAVNIKU', 'MEJL_NASTAVNIKU_UKUPNO')");
    }

    static void ocekuj(int kod, String poruka, Supplier<?> s) {
        SystemException ex = assertThrows(SystemException.class, s::get);
        assertEquals(kod, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    NajavaCmd cmd(LocalDate datum, String razlog) {
        return new NajavaCmd(stavka, datum, razlog);
    }

    // ---------------------------------------------------------------- naredni časovi

    @Test
    void naredniCetiriTerminaDanasnjiDokNePocne() {
        List<StudentskiTermin> t = najave.naredni(ana).termini();
        assertEquals(List.of(DANAS, DANAS.plusDays(7), DANAS.plusDays(14), DANAS.plusDays(21)),
                t.stream().map(StudentskiTermin::datum).toList());
        StudentskiTermin prvi = t.get(0);
        assertEquals("Čas " + d.cas.oznaka, prvi.predmet());
        assertTrue(prvi.svojaGrupa());
        assertFalse(prvi.otkazan());
        assertFalse(prvi.najavljeno());

        sat.postavi(SADA.withHour(10));
        assertEquals(DANAS.plusDays(7), najave.naredni(ana).termini().get(0).datum(), "današnji je počeo");

        // kraj perioda: samo termini do vaziDo
        sat.postavi(LocalDateTime.of(2031, 1, 20, 8, 0));
        assertEquals(List.of(LocalDate.of(2031, 1, 22), LocalDate.of(2031, 1, 29)),
                najave.naredni(ana).termini().stream().map(StudentskiTermin::datum).toList());
        sat.postavi(LocalDateTime.of(2031, 2, 1, 8, 0));
        assertTrue(najave.naredni(ana).termini().isEmpty());
    }

    @Test
    void tudjaGrupaNeVidiINeMozeDaNajavi() {
        Long bora = d.cas.student(druga, "Bora");
        assertTrue(najave.naredni(bora).termini().isEmpty());
        ocekuj(404, NajavaService.NIJE_TVOJ, () -> najave.najavi(bora, cmd(DANAS, null)));
        ocekuj(404, NajavaService.NIJE_TVOJ, () -> najave.povuci(bora, cmd(DANAS, null)));
        ocekuj(404, NajavaService.NIJE_TVOJ, () -> najave.najavi(ana, new NajavaCmd(-1L, DANAS, null)));
    }

    @Test
    void starijaGeneracijaPoAktivnostiIliCekiranju() {
        Long ceda = d.cas.student(starija, "Ceda");
        Long dara = d.cas.student(starija, "Dara");
        Long pred = d.cas.predavanje(grupa, LocalDate.of(2030, 10, 30));
        assertTrue(najave.naredni(ceda).termini().isEmpty());
        d.cas.aktivnost(pred, ceda, "PRISUSTVO");
        d.cekiranje(pred, dara);
        for (Long s : List.of(ceda, dara)) {
            List<StudentskiTermin> t = najave.naredni(s).termini();
            assertEquals(4, t.size());
            assertFalse(t.get(0).svojaGrupa());
            assertTrue(najave.najavi(s, cmd(DANAS, null)).najavljeno());
        }
        // aktivnost na predavanju druge grupe istog predmeta ne daje stavku grupe "grupa"
        Long eva = d.cas.student(starija, "Eva");
        Long tudje = d.cas.predavanje(druga, LocalDate.of(2030, 10, 30));
        d.cas.aktivnost(tudje, eva, "PRISUSTVO");
        assertTrue(najave.naredni(eva).termini().isEmpty());
    }

    // ---------------------------------------------------------------- najava i povlačenje

    @Test
    void najavaPovlacenjeIRazlog() {
        StudentskiTermin t = najave.najavi(ana, cmd(DANAS.plusDays(7), "  idem\u0000 kod\n\nlekara  "));
        assertTrue(t.najavljeno());
        assertEquals("idem kod lekara", t.razlog());
        // ponovljeno: samo novi razlog, i dalje jedna najava
        assertNull(najave.najavi(ana, cmd(DANAS.plusDays(7), " ")).razlog());
        assertEquals(1, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ?", Integer.class,
                stavka));
        StudentskiTermin u = najave.naredni(ana).termini().get(1);
        assertTrue(u.najavljeno());
        assertFalse(najave.naredni(ana).termini().get(0).najavljeno());

        assertFalse(najave.povuci(ana, cmd(DANAS.plusDays(7), null)).najavljeno());
        assertFalse(najave.povuci(ana, cmd(DANAS.plusDays(7), null)).najavljeno(), "bez najave nije greška");
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ?", Integer.class,
                stavka));
        ocekuj(400, NajavaService.PREDUG_RAZLOG, () -> najave.najavi(ana, cmd(DANAS, "x".repeat(201))));
        assertEquals("x".repeat(200), najave.najavi(ana, cmd(DANAS, "x".repeat(200))).razlog());
    }

    @Test
    void datumKojiNijeTerminPoceoIOtkazan() {
        ocekuj(404, NajavaService.NIJE_TVOJ, () -> najave.najavi(ana, cmd(DANAS.plusDays(1), null)));
        ocekuj(404, NajavaService.NIJE_TVOJ, () -> najave.najavi(ana, cmd(LocalDate.of(2031, 2, 5), null)));
        ocekuj(404, NajavaService.NIJE_TVOJ, () -> najave.najavi(ana, cmd(null, null)));

        najave.najavi(ana, cmd(DANAS, null));
        sat.postavi(SADA.withHour(10));
        ocekuj(409, NajavaService.POCEO, () -> najave.najavi(ana, cmd(DANAS, null)));
        ocekuj(409, NajavaService.POCEO, () -> najave.povuci(ana, cmd(DANAS, null)));
        ocekuj(409, NajavaService.POCEO, () -> najave.najavi(ana, cmd(DANAS.minusDays(7), null)));

        termini.otkazi(new TerminCmd(stavka, DANAS.plusDays(7), "sednica"));
        ocekuj(409, NajavaService.OTKAZAN, () -> najave.najavi(ana, cmd(DANAS.plusDays(7), null)));
        StudentskiTermin otk = najave.naredni(ana).termini().get(0);
        assertTrue(otk.otkazan());
        assertEquals("sednica", otk.napomena());
    }

    // ---------------------------------------------------------------- prag i obaveštenje

    @Test
    void pragJednoObavestenjeIMejlNastavniku() {
        String kljuc = "MNOGO_NAJAVA:" + stavka + ":" + DANAS;
        Long bora = d.cas.student(grupa, "Bora");
        Long ceca = d.cas.student(grupa, "Ceca");
        Long dule = d.cas.student(grupa, "Dule");
        najave.najavi(ana, cmd(DANAS, "tajni razlog"));
        najave.najavi(bora, cmd(DANAS, null));
        assertEquals(0, d.brojObavestenja(kljuc), "2 nije više od praga 2");
        najave.najavi(ceca, cmd(DANAS, null));
        assertEquals(1, d.brojObavestenja(kljuc));
        String podaci = jdbc.queryForObject("select podaci from obavestenja where kljuc = ?", String.class, kljuc);
        assertTrue(podaci.contains("\"broj\":3"), podaci);
        assertTrue(podaci.contains("\"prag\":2"), podaci);
        assertEquals("MNOGO_NAJAVA", jdbc.queryForObject("select vrsta from obavestenja where kljuc = ?", String.class, kljuc));

        List<MejlPoruka> poslate = mejl.cekaj(1);
        assertEquals("nastavnik@example.test", poslate.get(0).za());
        assertEquals("GFS: mnogo najava odsustva", poslate.get(0).naslov());
        assertTrue(poslate.get(0).telo().contains("3 studenta"), poslate.get(0).telo());
        assertTrue(poslate.get(0).telo().contains("raspored/termini?od=" + DANAS), poslate.get(0).telo());
        assertFalse(poslate.get(0).telo().contains("tajni razlog"));
        assertFalse(poslate.get(0).telo().contains("Ana"));

        // povlačenje pa ponovna najava i četvrta najava: nikad drugo obaveštenje
        najave.povuci(ceca, cmd(DANAS, null));
        najave.najavi(ceca, cmd(DANAS, null));
        najave.najavi(dule, cmd(DANAS, null));
        assertEquals(1, d.brojObavestenja(kljuc));
        assertEquals(1, mejl.poslate().size());

        // otkazivanje rešava obaveštenje
        termini.otkazi(new TerminCmd(stavka, DANAS, null));
        assertTrue(d.reseno(kljuc));
    }

    @Test
    void paralelneNajaveJednoObavestenje() throws Exception {
        String kljuc = "MNOGO_NAJAVA:" + stavka + ":" + DANAS;
        List<Long> studenti = new ArrayList<>();
        for (int i = 0; i < 8; i++) studenti.add(d.cas.student(grupa, "S" + i));
        ExecutorService ex = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<StudentskiTermin>> f = new ArrayList<>();
            for (Long s : studenti) {
                f.add(ex.submit(() -> {
                    start.await();
                    return najave.najavi(s, cmd(DANAS, null));
                }));
            }
            start.countDown();
            for (Future<StudentskiTermin> x : f) assertTrue(x.get(30, TimeUnit.SECONDS).najavljeno());
        } finally {
            ex.shutdownNow();
        }
        assertEquals(8, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ? and datum = ?",
                Integer.class, stavka, DANAS));
        assertEquals(1, d.brojObavestenja(kljuc));
        mejl.cekaj(1, Duration.ofSeconds(5));
        Thread.sleep(300);
        assertEquals(1, mejl.poslate().size());
    }
}
