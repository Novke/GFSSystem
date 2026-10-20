package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tri.novica.gfssystem.dto.prijava.BrojObavestenjaInfo;
import tri.novica.gfssystem.dto.prijava.ObavestenjeInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ObavestenjeServis} nad pravom MySQL bazom (spec 6): "Ne stiže mi kod", "Previše kodova", lista/brojač/čitanje.
 * Bez adrese nastavnika u podešavanju (mejl nastavniku: {@code ObavestenjeMejlIT}). Svaki test ima svoje IP-ove i
 * studente; tabela {@code obavestenja} se čisti pre i posle (privremena baza testova).
 */
@SpringBootTest
@Import({PrijavaSatConfig.class, LazniMejlServis.class})
class ObavestenjeServisIT {

    static final String PREVISE = "Previše pokušaja. Pokušaj ponovo kasnije.";

    @Autowired ObavestenjeServis servis;
    @Autowired KodServis kodServis;
    @Autowired StudentskaPrijava prijava;
    @Autowired StudentRepository studentRepository;
    @Autowired LazniMejlServis mejl;
    @Autowired PomerljivSat sat;
    @Autowired JdbcTemplate jdbc;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("obavestenjaIzvrsilac")
    org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor izvrsilac;

    TestStudenti studenti;
    final List<String> kljucevi = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sat.postavi(Instant.now());
        mejl.ocisti();
        jdbc.update("delete from obavestenja");
        jdbc.update("delete from pokusaji where vrsta in ('MEJL_NASTAVNIKU', 'MEJL_NASTAVNIKU_UKUPNO')");
        studenti = new TestStudenti(studentRepository, jdbc);
    }

    @AfterEach
    void ocisti() {
        pustiPozadinu();
        kodServis.minimalnoTrajanje(Duration.ZERO);
        mejl.ocisti();
        studenti.obrisi();
        jdbc.update("delete from obavestenja");
        for (String k : kljucevi) {
            jdbc.update("delete from pokusaji where kljuc = ?", k);
        }
    }

    String noviIp() {
        String ip = "t8-" + UUID.randomUUID().toString().substring(0, 8);
        kljucevi.add(ip);
        return ip;
    }

    /** Nepostojeći indeks; njegov brojač "Ne stiže mi kod" se čisti na kraju. */
    String nepostojeci() {
        String i = TestStudenti.noviIndeks();
        kljucevi.add(kodServis.kljucIndeksa(i));
        return i;
    }

    /** Student čiji se brojač po indeksu čisti na kraju (prazan student-id ključ čisti {@link TestStudenti}). */
    Student student(String email) {
        Student s = studenti.napravi(email);
        kljucevi.add(kodServis.kljucIndeksa(s.getIndeks()));
        return s;
    }

    void pomoc(String indeks, String ip) {
        servis.neStizeKod(indeks, ip, Optional.empty());
    }

    List<ObavestenjeInfo> sva() {
        return servis.lista(false, PageRequest.of(0, 100)).getContent();
    }

    /** Prijavljuje studenta i vraća token iz kolačića. */
    String prijavi(Student s) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/120.0 Mobile Safari/537.36");
        MockHttpServletResponse res = new MockHttpServletResponse();
        prijava.prijavi(s.getId(), NacinPrijave.KOD, req, res);
        String h = res.getHeader("Set-Cookie");
        return h.substring("gfs_student=".length(), h.indexOf(';'));
    }

    static SystemException greska(Runnable r) {
        return assertThrows(SystemException.class, r::run);
    }

    // ---------------------------------------------------------------- "Ne stiže mi kod"

    @Test
    void pomocZaPostojecegSaMejlom() {
        Student s = student("ana@example.test");
        String ip = noviIp();
        pomoc("  " + s.getIndeks().toLowerCase() + " ", ip);

        List<ObavestenjeInfo> lista = sva();
        assertEquals(1, lista.size());
        ObavestenjeInfo o = lista.getFirst();
        assertEquals(VrstaObavestenja.NE_STIZE_KOD, o.vrsta());
        assertEquals(s.getId(), o.studentId());
        assertEquals("Test Studentić", o.studentIme());
        assertEquals(s.getIndeks(), o.indeks());
        assertEquals(s.getIndeks(), o.podaci().get("indeks"));
        assertEquals(Boolean.TRUE, o.podaci().get("imaMejl"));
        assertEquals(ip, o.podaci().get("ip"));
        assertEquals("NEPOZNAT", o.podaci().get("uredjaj"));
        assertNull(o.procitano());
        assertNull(o.reseno());
        assertNotNull(o.kreirano());
    }

    @Test
    void podaciSuUBaziJsonAVracajuSeKaoObjekat() {
        Student s = student("ana@example.test");
        pomoc(s.getIndeks(), noviIp());
        String json = jdbc.queryForObject("select podaci from obavestenja where student_id = ?", String.class, s.getId());
        assertTrue(json.startsWith("{") && json.contains("\"imaMejl\":true"), json);
    }

    @Test
    void pomocZaStudentaBezMejla() {
        for (String email : new String[]{null, "  "}) {
            Student s = student(email);
            pomoc(s.getIndeks(), noviIp());
            ObavestenjeInfo o = sva().stream().filter(x -> s.getId().equals(x.studentId())).findFirst().orElseThrow();
            assertEquals(Boolean.FALSE, o.podaci().get("imaMejl"));
        }
    }

    @Test
    void pomocZaNepostojeciIndeksPraviObavestenjeBezStudenta() {
        String indeks = nepostojeci();
        String ip = noviIp();
        pomoc(indeks, ip);

        ObavestenjeInfo o = sva().getFirst();
        assertEquals(VrstaObavestenja.NE_STIZE_KOD, o.vrsta());
        assertNull(o.studentId());
        assertNull(o.studentIme());
        assertEquals(indeks, o.indeks());
        assertEquals(indeks, o.podaci().get("indeks"));
        assertEquals(Boolean.FALSE, o.podaci().get("imaMejl"));
        assertEquals("NEPOZNAT", o.podaci().get("uredjaj"));
    }

    @Test
    void varijantaIndeksaSePonasaKaoNepostojeci() {
        Student s = student("ana@example.test");
        // akcenat u prvom slovu: ai_ci kolacija bi ga izjednačila sa pravim indeksom, bela lista ne
        String varijanta = "Ť" + s.getIndeks().substring(1);
        kljucevi.add(kodServis.kljucIndeksa(tri.novica.gfssystem.utility.IndeksUtil.normalizuj(varijanta)));
        pomoc(varijanta, noviIp());

        ObavestenjeInfo o = sva().getFirst();
        assertNull(o.studentId());
        assertEquals(Boolean.FALSE, o.podaci().get("imaMejl"));
    }

    @Test
    void drugiPutUSatuNemaNovogObavestenjaAliJeOdgovorIsti() {
        Student s = student("ana@example.test");
        String ip = noviIp();
        pomoc(s.getIndeks(), ip);
        assertDoesNotThrow(() -> pomoc(s.getIndeks(), ip));
        assertDoesNotThrow(() -> pomoc(s.getIndeks().toLowerCase(), noviIp()));
        assertEquals(1, sva().size());

        sat.pomeri(Duration.ofMinutes(61));
        pomoc(s.getIndeks(), noviIp());
        assertEquals(2, sva().size());
    }

    @Test
    void limitPoIpuJeDesetNaSatIDajeCetristoDvadesetDevet() {
        String ip = noviIp();
        for (int i = 0; i < 10; i++) {
            pomoc(nepostojeci(), ip);
        }
        SystemException e = greska(() -> pomoc(nepostojeci(), ip));
        assertEquals(429, e.getCode());
        assertEquals(PREVISE, e.getMessage());
        assertEquals(10, sva().size());
        // drugi IP prolazi
        assertDoesNotThrow(() -> pomoc(nepostojeci(), noviIp()));
        // i tihi zahtevi (isti indeks) se broje u IP limit
        String ip2 = noviIp();
        String indeks = nepostojeci();
        for (int i = 0; i < 10; i++) {
            pomoc(indeks, ip2);
        }
        assertEquals(429, greska(() -> pomoc(indeks, ip2)).getCode());
        // posle sata ponovo
        sat.pomeri(Duration.ofMinutes(61));
        assertDoesNotThrow(() -> pomoc(indeks, ip2));
    }

    // ---------------------------------------------------------------- uređaj

    @Test
    void uredjajJeZapamcenKadTokenPripadaStudentu() {
        Student s = student("ana@example.test");
        String token = prijavi(s);
        servis.neStizeKod(s.getIndeks(), noviIp(), Optional.of(token));
        assertEquals("ZAPAMCEN", sva().getFirst().podaci().get("uredjaj"));
    }

    @Test
    void uredjajJeZapamcenIKadJeOpozvan() {
        Student s = student("ana@example.test");
        String token = prijavi(s);
        prijava.odjavi(token);
        servis.neStizeKod(s.getIndeks(), noviIp(), Optional.of(token));
        assertEquals("ZAPAMCEN", sva().getFirst().podaci().get("uredjaj"));
    }

    @Test
    void uredjajJeNepoznatZaTudjIliNevazeciToken() {
        Student s = student("ana@example.test");
        Student drugi = student("pera@example.test");
        String tudj = prijavi(drugi);
        servis.neStizeKod(s.getIndeks(), noviIp(), Optional.of(tudj));
        assertEquals("NEPOZNAT", sva().getFirst().podaci().get("uredjaj"));

        sat.pomeri(Duration.ofMinutes(61));
        servis.neStizeKod(s.getIndeks(), noviIp(), Optional.of("smece"));
        sat.pomeri(Duration.ofMinutes(61));
        servis.neStizeKod(s.getIndeks(), noviIp(), Optional.of("A".repeat(32)));
        assertEquals(3, sva().size());
        assertTrue(sva().stream().allMatch(o -> "NEPOZNAT".equals(o.podaci().get("uredjaj"))));
    }

    @Test
    void viseTokenaDovoljanJeJedanSvoj() {
        Student s = student("ana@example.test");
        String svoj = prijavi(s);
        servis.neStizeKod(s.getIndeks(), noviIp(), List.of("smece", svoj));
        assertEquals("ZAPAMCEN", sva().getFirst().podaci().get("uredjaj"));
    }

    @Test
    void tokenNepostojecegIndeksaJeNepoznat() {
        Student s = student("ana@example.test");
        String token = prijavi(s);
        servis.neStizeKod(nepostojeci(), noviIp(), Optional.of(token));
        assertEquals("NEPOZNAT", sva().getFirst().podaci().get("uredjaj"));
    }

    // ---------------------------------------------------------------- bez mejla nastavniku, bez zastoja

    @Test
    void bezAdreseNastavnikaNemaMejla() throws Exception {
        Student s = student("ana@example.test");
        pomoc(s.getIndeks(), noviIp());
        Thread.sleep(300);
        assertTrue(mejl.poslate().isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from pokusaji where vrsta = 'MEJL_NASTAVNIKU_UKUPNO'", Integer.class));
    }

    @Test
    void istovremeniZahteviZaIstiIndeksDajuJednoObavestenje() throws Exception {
        Student s = student("ana@example.test");
        int n = 5;
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            List<Future<?>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ip = noviIp();
                f.add(niti.submit(() -> {
                    kreni.await();
                    pomoc(s.getIndeks(), ip);
                    return null;
                }));
            }
            kreni.countDown();
            for (Future<?> x : f) x.get(60, TimeUnit.SECONDS);
        } finally {
            niti.shutdownNow();
        }
        assertEquals(1, sva().size());
    }

    @Test
    void istovremeniZahteviZaNepostojeciIndeksDajuJednoObavestenje() throws Exception {
        String indeks = nepostojeci();
        int n = 5;
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            List<Future<?>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ip = noviIp();
                f.add(niti.submit(() -> {
                    kreni.await();
                    pomoc(indeks, ip);
                    return null;
                }));
            }
            kreni.countDown();
            for (Future<?> x : f) x.get(60, TimeUnit.SECONDS);
        } finally {
            niti.shutdownNow();
        }
        assertEquals(1, sva().size());
        // brava indeksa je otpuštena
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from performance_schema.metadata_locks
                where object_type = 'USER LEVEL LOCK' and object_name like 'gfs-pomoc:%'""", Integer.class));
    }

    // ---------------------------------------------------------------- donja granica trajanja

    @Test
    void donjaGranicaVaziZaSveIshodeOsim429PoIpu() {
        long granicaMs = 250;
        kodServis.minimalnoTrajanje(Duration.ofMillis(granicaMs));
        Student s = student("ana@example.test");
        Student bezMejla = student(null);
        String ip = noviIp();

        assertTrajeNajmanje(granicaMs, () -> pomoc(s.getIndeks(), noviIp()));
        assertTrajeNajmanje(granicaMs, () -> pomoc(bezMejla.getIndeks(), noviIp()));
        assertTrajeNajmanje(granicaMs, () -> pomoc(nepostojeci(), noviIp()));
        // tiho preskočen (limit po indeksu) traje isto
        assertTrajeNajmanje(granicaMs, () -> pomoc(s.getIndeks(), noviIp()));

        kodServis.minimalnoTrajanje(Duration.ZERO);
        for (int i = 0; i < 10; i++) {
            pomoc(nepostojeci(), ip);
        }
        kodServis.minimalnoTrajanje(Duration.ofSeconds(2));
        long t0 = System.nanoTime();
        assertEquals(429, greska(() -> pomoc(nepostojeci(), ip)).getCode());
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1500, "429 po IP-u ne čeka granicu");
    }

    static void assertTrajeNajmanje(long ms, Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        long trajalo = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(trajalo >= ms, "trajalo " + trajalo + " ms, granica " + ms);
    }

    // ---------------------------------------------------------------- previše kodova

    @Test
    void previseKodovaNajviseJedanDnevnoPoStudentu() {
        Student s = student("ana@example.test");
        Student drugi = student("pera@example.test");
        String ip = noviIp();
        servis.previseKodova(s.getId(), ip);
        servis.previseKodova(s.getId(), ip);
        sat.pomeri(Duration.ofHours(23));
        servis.previseKodova(s.getId(), ip);
        servis.previseKodova(drugi.getId(), ip);

        List<ObavestenjeInfo> lista = sva();
        assertEquals(2, lista.size());
        ObavestenjeInfo o = lista.stream().filter(x -> s.getId().equals(x.studentId())).findFirst().orElseThrow();
        assertEquals(VrstaObavestenja.PREVISE_KODOVA, o.vrsta());
        assertEquals(ip, o.podaci().get("ip"));
        assertEquals(s.getIndeks(), o.indeks());
        assertEquals("Test Studentić", o.studentIme());

        sat.pomeri(Duration.ofHours(2));
        servis.previseKodova(s.getId(), ip);
        assertEquals(3, sva().size());
    }

    @Test
    void previseKodovaIstovremenoDajeJedno() throws Exception {
        Student s = student("ana@example.test");
        int n = 5;
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            List<Future<?>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                f.add(niti.submit(() -> {
                    kreni.await();
                    servis.previseKodova(s.getId(), "t8-x");
                    return null;
                }));
            }
            kreni.countDown();
            for (Future<?> x : f) x.get(60, TimeUnit.SECONDS);
        } finally {
            niti.shutdownNow();
        }
        assertEquals(1, sva().size());
    }

    @Test
    void cetvrtoSlanjeKodaPraviObavestenjeAOdgovorJeIsti() {
        Student s = student("ana@example.test");
        String ip = noviIp();
        for (int i = 0; i < 3; i++) {
            kodServis.posalji(s.getIndeks(), ip);
        }
        assertTrue(sva().isEmpty());
        SystemException e = greska(() -> kodServis.posalji(s.getIndeks(), ip));
        assertEquals(429, e.getCode());
        assertEquals(PREVISE, e.getMessage());
        // obaveštenje se upisuje u pozadini, posle odgovora
        saceka(() -> sva().size() == 1);
        assertEquals(VrstaObavestenja.PREVISE_KODOVA, sva().getFirst().vrsta());
        assertEquals(s.getId(), sva().getFirst().studentId());
        // peto i šesto: i dalje 429, i dalje jedno obaveštenje (najviše 1 dnevno)
        assertEquals(429, greska(() -> kodServis.posalji(s.getIndeks(), ip)).getCode());
        assertEquals(429, greska(() -> kodServis.posalji(s.getIndeks(), ip)).getCode());
        pustiPozadinu();
        assertEquals(1, sva().size());
    }

    /** Odgovor 429 ne čeka obaveštenje: dok su niti izvršioca zauzete, 429 stiže, a obaveštenje tek posle oslobađanja. */
    @Test
    void odgovor429NeCekaUpisObavestenja() throws Exception {
        Student s = student("ana@example.test");
        String ip = noviIp();
        for (int i = 0; i < 3; i++) {
            kodServis.posalji(s.getIndeks(), ip);
        }
        CountDownLatch drzi = new CountDownLatch(1);
        int niti = izvrsilac.getCorePoolSize();
        CountDownLatch zauzete = new CountDownLatch(niti);
        for (int i = 0; i < niti; i++) {
            izvrsilac.execute(() -> {
                zauzete.countDown();
                try {
                    drzi.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        try {
            assertTrue(zauzete.await(10, TimeUnit.SECONDS));
            assertEquals(429, greska(() -> kodServis.posalji(s.getIndeks(), ip)).getCode());
            Thread.sleep(300);
            assertTrue(sva().isEmpty(), "obaveštenje ne sme da se upiše pre nego što izvršilac stigne do zadatka");
        } finally {
            drzi.countDown();
        }
        saceka(() -> sva().size() == 1);
        assertEquals(s.getId(), sva().getFirst().studentId());
    }

    @Test
    void greskaUPozadiniNeMenjaOdgovor() {
        // student ne postoji: obrada u pozadini ne baca (ćutke ništa), a 429 je ostao isti
        servis.naDostignutLimitKodova(new DostignutLimitKodova(-77L, "t8-x"));
        assertTrue(sva().isEmpty());
    }

    /** Čeka (najviše 15 s, uzorak na 20 ms) da uslov postane tačan; inače pada. */
    static void saceka(java.util.function.BooleanSupplier uslov) {
        long kraj = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!uslov.getAsBoolean()) {
            if (System.nanoTime() > kraj) {
                fail("uslov nije ispunjen za 15 s");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("prekinuto");
            }
        }
    }

    /** Čeka da izvršilac obavesti pozadinu do kraja (red prazan i nema aktivnih niti). */
    void pustiPozadinu() {
        saceka(() -> izvrsilac.getThreadPoolExecutor().getQueue().isEmpty() && izvrsilac.getActiveCount() == 0);
    }

    @Test
    void nepostojeciIndeksNePraviObavestenjeZaPreviseKodova() {
        String indeks = nepostojeci();
        String ip = noviIp();
        for (int i = 0; i < 3; i++) {
            kodServis.posalji(indeks, ip);
        }
        assertEquals(429, greska(() -> kodServis.posalji(indeks, ip)).getCode());
        pustiPozadinu();
        assertTrue(sva().isEmpty());
    }

    // ---------------------------------------------------------------- lista, brojač, čitanje

    @Test
    void listaOtvorenaISvaNajnovijePrvo() {
        Student a = student("a@example.test");
        Student b = student("b@example.test");
        pomoc(a.getIndeks(), noviIp());
        sat.pomeri(Duration.ofSeconds(1));
        pomoc(b.getIndeks(), noviIp());
        Long idA = sva().stream().filter(o -> a.getId().equals(o.studentId())).findFirst().orElseThrow().id();

        List<ObavestenjeInfo> sve = sva();
        assertEquals(2, sve.size());
        assertEquals(b.getId(), sve.getFirst().studentId());

        servis.reseno(idA);
        assertEquals(1, servis.lista(true, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(b.getId(), servis.lista(true, PageRequest.of(0, 10)).getContent().getFirst().studentId());
        assertEquals(2, servis.lista(false, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, servis.lista(false, PageRequest.of(1, 1)).getContent().size());
    }

    @Test
    void brojProcitanoReseno() {
        Student a = student("a@example.test");
        Student b = student("b@example.test");
        Student c = student("c@example.test");
        pomoc(a.getIndeks(), noviIp());
        pomoc(b.getIndeks(), noviIp());
        pomoc(c.getIndeks(), noviIp());
        assertEquals(new BrojObavestenjaInfo(3, 3), servis.broj());

        Long idA = sva().stream().filter(o -> a.getId().equals(o.studentId())).findFirst().orElseThrow().id();
        Long idB = sva().stream().filter(o -> b.getId().equals(o.studentId())).findFirst().orElseThrow().id();
        servis.procitano(idA);
        servis.procitano(idA); // ponavljanje ne menja vreme
        assertEquals(new BrojObavestenjaInfo(2, 3), servis.broj());
        var prvo = sva().stream().filter(o -> o.id().equals(idA)).findFirst().orElseThrow().procitano();
        assertNotNull(prvo);
        sat.pomeri(Duration.ofMinutes(5));
        servis.procitano(idA);
        assertEquals(prvo, sva().stream().filter(o -> o.id().equals(idA)).findFirst().orElseThrow().procitano());

        servis.reseno(idB); // rešeno je i pročitano
        assertEquals(new BrojObavestenjaInfo(1, 2), servis.broj());
        ObavestenjeInfo rb = sva().stream().filter(o -> o.id().equals(idB)).findFirst().orElseThrow();
        assertNotNull(rb.reseno());
        assertNotNull(rb.procitano());

        assertEquals(1, servis.procitajSve());
        assertEquals(new BrojObavestenjaInfo(0, 2), servis.broj());
        assertEquals(0, servis.procitajSve());
    }

    @Test
    void nepoznatId404() {
        assertEquals(404, greska(() -> servis.procitano(-5L)).getCode());
        assertEquals(404, greska(() -> servis.reseno(-5L)).getCode());
    }

    @Test
    void resiZaStudentaResavaSamoOtvorenaTogStudenta() {
        Student a = student("a@example.test");
        Student b = student("b@example.test");
        pomoc(a.getIndeks(), noviIp());
        servis.previseKodova(a.getId(), "t8-x");
        pomoc(b.getIndeks(), noviIp());
        pomoc(nepostojeci(), noviIp());
        assertEquals(4, servis.broj().otvorena());

        assertEquals(2, servis.resiZaStudenta(a.getId()));
        assertEquals(2, servis.broj().otvorena());
        assertEquals(0, servis.resiZaStudenta(a.getId()));
        assertEquals(0, servis.broj().neprocitana() - 2);
        assertTrue(servis.lista(true, PageRequest.of(0, 10)).getContent().stream()
                .noneMatch(o -> a.getId().equals(o.studentId())));
    }
}
