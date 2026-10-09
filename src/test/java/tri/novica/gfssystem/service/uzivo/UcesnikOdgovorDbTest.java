package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prijava i odgovor nad pravom MySQL bazom (kao CI), bez {@code @Transactional}: u bazi je samo heš tokena, kolačić
 * važi za "ja" i WebSocket, javno stanje ne nosi tekst pre dozvole, a istovremeni odgovori istog učesnika (dupli dodir)
 * prolaze redom kroz zaključavanje izvođenja: tačno jedan je primljen.
 */
@SpringBootTest
class UcesnikOdgovorDbTest {

    @Autowired UcesnikService ucesnikService;
    @Autowired OdgovorService odgovorService;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired StanjeService stanjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transakcije;

    Predmet predmet;
    final List<Long> prezentacije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Uživo odgovori DB " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
    }

    @AfterEach
    void tearDown() {
        for (Long id : prezentacije) {
            jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", id);
            try {
                prezentacijaService.obrisi(id);
            } catch (SystemException e) {
                // već obrisana
            }
        }
        predmetRepository.deleteById(predmet.getId());
    }

    /** Prezentacija sa jednim JEDAN_TACAN pitanjem bez vremenskog ograničenja (raspored zatvaranja ne učestvuje). */
    PrezentacijaDetails prezentacija() {
        PrezentacijaDetails p = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Statika uživo", null));
        prezentacije.add(p.id());
        prezentacijaService.dodajSlajd(p.id(), new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false,
                new PitanjeCmd(TipPitanja.JEDAN_TACAN, "Koja sila?", null, null,
                        List.of(new OpcijaCmd("Gravitacija", true), new OpcijaCmd("Trenje", false)),
                        null, null, null, null, null, null, null, null)), null);
        return prezentacijaService.detalji(p.id());
    }

    void k(Long id, TipKomande tip) {
        izvodjenjeService.komanda(id, new KomandaCmd(tip, null));
    }

    @Test
    void prijavaOdgovorIJavnoStanje() throws Exception {
        PrezentacijaDetails p = prezentacija();
        Long tacna = p.slajdovi().get(0).pitanje().opcije().get(0).id();
        Long netacna = p.slajdovi().get(0).pitanje().opcije().get(1).id();
        IzvodjenjeInfo info = izvodjenjeService.pokreni(p.id(), new PokreniCmd(false, null, null));
        Long id = info.id();
        String kod = info.kod();

        assertEquals(new JavnoIzvodjenjeInfo("Statika uživo"), ucesnikService.info(kod));

        // prijava: jedinstvena imena, u bazi samo heš tokena
        UcesnikService.Prijavljen ana = ucesnikService.prijavi(kod, "Ana");
        UcesnikService.Prijavljen ana2 = ucesnikService.prijavi(kod, " ana ");
        assertEquals("ana 2", ana2.info().ime());
        assertEquals(id, ana.info().izvodjenjeId());
        assertEquals(TokenHash.od(ana.token()),
                jdbc.queryForObject("select token_hash from ucesnici where id = ?", String.class, ana.info().ucesnikId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from ucesnici where token_hash = ?", Long.class, ana.token()));
        assertEquals(ana.info(), ucesnikService.ja(kod, ana.token()).orElseThrow());
        assertEquals(ana.info().ucesnikId(), ucesnikService.poTokenu(ana.token()).orElseThrow().getId());
        assertTrue(ucesnikService.ja("000000".equals(kod) ? "111111" : "000000", ana.token()).isEmpty());

        // na pitanju, pre otvaranja: samo tip i faza
        k(id, TipKomande.SLEDECI);
        JavnoStanje javno = stanjeService.javno(id);
        assertEquals(JavnoPitanje.samoTip(TipPitanja.JEDAN_TACAN, Faza.CEKA), javno.pitanje());
        assertEquals(2, javno.brojUcesnika());

        // otvoreno, DUGMAD bez Detalja (nova prezentacija ih dozvoljava, D ih gasi): id-jevi opcija bez teksta
        k(id, TipKomande.DETALJI);
        assertFalse(stanjeService.javno(id).detaljiDozvoljeni());
        k(id, TipKomande.OTVORI_ZATVORI);
        javno = stanjeService.javno(id);
        Long rundaId = javno.pitanje().rundaId();
        assertNotNull(rundaId);
        assertEquals(List.of(new JavnaOpcija(tacna, null), new JavnaOpcija(netacna, null)), javno.pitanje().opcije());
        assertNull(javno.pitanje().tekst());

        // dupli dodir: dva istovremena odgovora istog učesnika, tačno jedan primljen
        Long anaId = ana.info().ucesnikId();
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<String>> dodiri = new ArrayList<>();
        for (Long opcija : List.of(tacna, netacna)) {
            dodiri.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                    odgovorService.odgovori(id, anaId, new OdgovorCmd(rundaId, List.of(opcija), null, null, null));
                    return "primljen";
                } catch (OdgovorOdbijen e) {
                    return e.getMessage();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "prekinut";
                }
            }));
        }
        start.countDown();
        List<String> ishodi = new ArrayList<>();
        for (CompletableFuture<String> f : dodiri) ishodi.add(f.get(20, TimeUnit.SECONDS));
        assertEquals(1, ishodi.stream().filter("primljen"::equals).count(), ishodi.toString());
        assertEquals(1, ishodi.stream().filter("Već si odgovorio."::equals).count(), ishodi.toString());
        assertEquals(1, jdbc.queryForObject("select count(*) from odgovori where runda_id = ? and ucesnik_id = ?",
                Long.class, rundaId, anaId));

        LicnoStanje licno = stanjeService.licno(id, anaId);
        assertEquals(new LicniOdgovor(rundaId, true, null, null), licno.odgovor());

        // D uživo: tekst stiže tek sada
        k(id, TipKomande.DETALJI);
        assertEquals("Koja sila?", stanjeService.javno(id).pitanje().tekst());
        assertEquals("Gravitacija", stanjeService.javno(id).pitanje().opcije().get(0).tekst());

        // posle zatvaranja odgovor se odbija; tačan odgovor tek posle TACAN
        k(id, TipKomande.OTVORI_ZATVORI);
        OdgovorOdbijen e = assertThrows(OdgovorOdbijen.class, () -> odgovorService.odgovori(id,
                ana2.info().ucesnikId(), new OdgovorCmd(rundaId, List.of(tacna), null, null, null)));
        assertEquals("Pitanje je zatvoreno.", e.getMessage());
        assertNull(stanjeService.javno(id).pitanje().tacneOpcije());
        k(id, TipKomande.TACAN);
        assertEquals(List.of(tacna), stanjeService.javno(id).pitanje().tacneOpcije());
        LicniOdgovor odgovor = stanjeService.licno(id, anaId).odgovor();
        assertTrue(odgovor.primljen());
        assertNotNull(odgovor.tacno());
        assertEquals(2, stanjeService.licnaZaSve(id).size());

        // izbačen: kolačić više ne važi, odgovor se odbija
        izvodjenjeService.izbaci(id, anaId);
        assertTrue(ucesnikService.ja(kod, ana.token()).isEmpty());
        assertTrue(ucesnikService.poTokenu(ana.token()).isEmpty());
        assertTrue(stanjeService.licno(id, anaId).izbacen());

        // kraj: kod više ne postoji, prijava i odgovor odbijeni
        k(id, TipKomande.ZAVRSI);
        SystemException nema = assertThrows(SystemException.class, () -> ucesnikService.prijavi(kod, "Bojan"));
        assertEquals(404, nema.getCode());
        e = assertThrows(OdgovorOdbijen.class, () -> odgovorService.odgovori(id, ana2.info().ucesnikId(),
                new OdgovorCmd(rundaId, List.of(tacna), null, null, null)));
        assertEquals("Izvođenje je završeno.", e.getMessage());
    }

    /**
     * Nastavnik završi izvođenje dok prijava čeka na zaključavanje: prijava posle čekanja vidi završeno izvođenje (404)
     * i ne upisuje učesnika. Izvođenje se ne sme učitati pre zaključavanja, inače bi zaključani upit vratio entitet sa
     * stanjem od pre čekanja.
     */
    @Test
    void prijavaKojaCekaZakljucavanjeVidiZavrsenoIzvodjenje() throws Exception {
        PrezentacijaDetails p = prezentacija();
        IzvodjenjeInfo info = izvodjenjeService.pokreni(p.id(), new PokreniCmd(false, null, null));
        Long id = info.id();

        CountDownLatch zakljucano = new CountDownLatch(1);
        CountDownLatch zavrsi = new CountDownLatch(1);
        CompletableFuture<Void> nastavnik = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transakcije).executeWithoutResult(tx -> {
                    jdbc.queryForObject("select id from izvodjenja where id = ? for update", Long.class, id);
                    zakljucano.countDown();
                    try {
                        assertTrue(zavrsi.await(20, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null, kraj = now() where id = ?", id);
                }));
        assertTrue(zakljucano.await(20, TimeUnit.SECONDS));

        CompletableFuture<UcesnikService.Prijavljen> prijava =
                CompletableFuture.supplyAsync(() -> ucesnikService.prijavi(info.kod(), "Ana"));
        cekajNaZakljucavanje();
        zavrsi.countDown();
        nastavnik.get(20, TimeUnit.SECONDS);

        ExecutionException e = assertThrows(ExecutionException.class, () -> prijava.get(20, TimeUnit.SECONDS));
        SystemException se = assertInstanceOf(SystemException.class, e.getCause());
        assertEquals(404, se.getCode());
        assertEquals(0, jdbc.queryForObject("select count(*) from ucesnici where izvodjenje_id = ?", Long.class, id));
    }

    /** Čeka dok neka transakcija ne čeka na zaključan red (InnoDB), najviše 20 s. */
    void cekajNaZakljucavanje() throws InterruptedException {
        long kraj = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (jdbc.queryForObject("select count(*) from performance_schema.data_lock_waits", Long.class) == 0) {
            assertTrue(System.nanoTime() < kraj, "prijava nije stigla do zaključavanja");
            Thread.sleep(20);
        }
    }
}
