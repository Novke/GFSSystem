package tri.novica.gfssystem.service.prijava;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.security.PrijavaTestUtil;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Review B7 (krug 2): donja granica trajanja ne sme da drži vezu iz pula. Za {@code /public/nalog/**} (i kasnije
 * {@code /public/lti/**}) nema open-in-view, pa zahtev u spavanju ne drži vezu; za sve ostale putanje open-in-view ostaje.
 * Ista konfiguracija konteksta kao {@code NalogPublicRestIT} (deli keširan kontekst).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LazniMejlServis.class)
class NalogBezOtvorenogEmIT {

    @Autowired MockMvc mvc;
    @Autowired KodServis kodServis;
    @Autowired LazniMejlServis mejl;
    @Autowired DataSource dataSource;
    @Autowired RequestMappingHandlerMapping mapiranja;
    @Autowired StudentRepository studentRepository;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    final List<String> kljucevi = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mejl.ocisti();
        studenti = new TestStudenti(studentRepository, jdbc);
    }

    @AfterEach
    void ocisti() {
        kodServis.minimalnoTrajanje(Duration.ZERO);
        mejl.ocisti();
        studenti.obrisi();
        for (String k : kljucevi) jdbc.update("delete from pokusaji where kljuc = ?", k);
    }

    /** Da li lanac handlera za ovaj zahtev ima open-in-view presretač. */
    boolean imaOtvorenEm(String metod, String putanja) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metod, putanja);
        HandlerExecutionChain lanac = mapiranja.getHandler(req);
        assertNotNull(lanac, putanja);
        for (HandlerInterceptor i : lanac.getInterceptorList()) {
            if (i instanceof WebRequestHandlerInterceptorAdapter a && omotan(a) instanceof OpenEntityManagerInViewInterceptor) {
                return true;
            }
        }
        return false;
    }

    static WebRequestInterceptor omotan(WebRequestHandlerInterceptorAdapter a) throws Exception {
        Field f = WebRequestHandlerInterceptorAdapter.class.getDeclaredField("requestInterceptor");
        f.setAccessible(true);
        return (WebRequestInterceptor) f.get(a);
    }

    @Test
    void otvorenEmSamoVanNalogaILti() throws Exception {
        assertFalse(imaOtvorenEm("POST", "/public/nalog/kod"));
        assertFalse(imaOtvorenEm("POST", "/public/nalog/prijava"));
        assertFalse(imaOtvorenEm("POST", "/public/nalog/pomoc"));
        assertFalse(imaOtvorenEm("GET", "/public/nalog/ja"));
        // nastavničke i ostale javne putanje zadržavaju open-in-view (lenjo učitavanje u kontrolerima)
        assertTrue(imaOtvorenEm("GET", "/studenti"));
        assertTrue(imaOtvorenEm("GET", "/obavestenja"));
        assertTrue(imaOtvorenEm("GET", "/grupe/1/pregled"));
        assertTrue(imaOtvorenEm("GET", "/public/upis/x"));
        assertTrue(imaOtvorenEm("GET", "/auth/ja"));
    }

    /** Donja granica trajanja u navalama: dovoljno dugo da i spor izvršilac stigne da svi zahtevi završe posao u bazi. */
    static final Duration GRANICA = Duration.ofSeconds(6);

    /**
     * Uzorkuje aktivne veze iz pula samo dok <b>svih n</b> zahteva spava do donje granice ({@code KodServis#uSpavanju},
     * proveren pre i posle očitavanja), pa prozor ne zavisi od brzine runnera: ni od toga kad zahtevi počnu, ni od
     * toga koliko traje njihov posao u bazi. Vraća {najviše aktivnih veza, broj uzoraka}; petlja se završava kad svi
     * zahtevi odgovore.
     */
    int[] uzorkujDokSviSpavaju(HikariPoolMXBean pul, int n, List<? extends Future<?>> zahtevi, long t0) throws Exception {
        int najvise = 0;
        int uzoraka = 0;
        long rok = t0 + Duration.ofSeconds(90).toNanos();
        while (zahtevi.stream().anyMatch(x -> !x.isDone())) {
            assertTrue(System.nanoTime() < rok, "zahtevi nisu završeni za 90 s");
            if (kodServis.uSpavanju() == n) {
                int aktivnih = pul.getActiveConnections();
                if (kodServis.uSpavanju() == n) {
                    najvise = Math.max(najvise, aktivnih);
                    uzoraka++;
                }
            }
            Thread.sleep(10);
        }
        return new int[]{najvise, uzoraka};
    }

    /**
     * Navala od 20 zahteva ({@code kod} i {@code prijava}) sa granicom {@link #GRANICA}: dok svi zahtevi spavaju (posao u
     * bazi traje desetine ms), aktivnih veza je najviše 2 (pre ispravke svih 10 iz pula, ostali čekaju).
     */
    @Test
    void navalaUSpavanjuNeDrziVeze() throws Exception {
        HikariPoolMXBean pul = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        Student s = studenti.napravi("ana@example.test");
        kodServis.minimalnoTrajanje(GRANICA);
        int n = 20;
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            List<Future<Integer>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ip = "2001:db8:b7::" + UUID.randomUUID().toString().substring(0, 4);
                kljucevi.add(ip);
                String indeks = i == 0 ? s.getIndeks() : TestStudenti.noviIndeks();
                // nepostojeći indeks ostavlja red KOD_SLANJE_STUDENT pod HMAC ključem (kao u bezGraniceZa503I429PoIpu)
                kljucevi.add(kodServis.kljucIndeksa(indeks));
                boolean kod = i % 2 == 0;
                String telo = kod ? "{\"indeks\":\"" + indeks + "\"}" : "{\"indeks\":\"" + indeks + "\",\"kod\":\"123456\"}";
                String putanja = kod ? "/public/nalog/kod" : "/public/nalog/prijava";
                f.add(niti.submit(() -> {
                    kreni.await();
                    return mvc.perform(post(putanja).with(r -> { r.setRemoteAddr(ip); return r; })
                                    .header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN)
                                    .contentType(MediaType.APPLICATION_JSON).content(telo))
                            .andReturn().getResponse().getStatus();
                }));
            }
            long t0 = System.nanoTime();
            kreni.countDown();
            int[] uzorak = uzorkujDokSviSpavaju(pul, n, f, t0);
            int najvise = uzorak[0];
            int uzoraka = uzorak[1];
            List<Integer> statusi = new ArrayList<>();
            for (Future<Integer> x : f) statusi.add(x.get(60, TimeUnit.SECONDS));
            long ukupnoMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("Navala " + n + " zahteva: najviše aktivnih veza u spavanju " + najvise + " (" + uzoraka
                    + " uzoraka), ukupno " + ukupnoMs + " ms, statusi " + statusi);
            assertTrue(uzoraka > 20, "premalo uzoraka: " + uzoraka);
            assertTrue(najvise <= 2, "aktivnih veza dok zahtevi spavaju: " + najvise);
            assertTrue(ukupnoMs >= GRANICA.toMillis(), "granica nije važila: " + ukupnoMs + " ms");
            for (int i = 0; i < n; i++) assertEquals(i % 2 == 0 ? 200 : 401, statusi.get(i), statusi.toString());
        } finally {
            niti.shutdownNow();
        }
    }

    /** Isto za "Ne stiže mi kod" (B8): 20 istovremenih zahteva sa granicom 6 s ne drže vezu iz pula dok spavaju. */
    @Test
    void navalaPomociNeDrziVeze() throws Exception {
        HikariPoolMXBean pul = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        Student s = studenti.napravi("ana@example.test");
        kodServis.minimalnoTrajanje(GRANICA);
        int n = 20;
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            List<Future<Integer>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ip = "2001:db8:b8::" + UUID.randomUUID().toString().substring(0, 4);
                kljucevi.add(ip);
                String indeks = i % 2 == 0 ? s.getIndeks() : TestStudenti.noviIndeks();
                String telo = "{\"indeks\":\"" + indeks + "\"}";
                f.add(niti.submit(() -> {
                    kreni.await();
                    return mvc.perform(post("/public/nalog/pomoc").with(r -> { r.setRemoteAddr(ip); return r; })
                                    .header(HttpHeaders.ORIGIN, PrijavaTestUtil.ORIGIN)
                                    .contentType(MediaType.APPLICATION_JSON).content(telo))
                            .andReturn().getResponse().getStatus();
                }));
            }
            long t0 = System.nanoTime();
            kreni.countDown();
            int[] uzorak = uzorkujDokSviSpavaju(pul, n, f, t0);
            int najvise = uzorak[0];
            int uzoraka = uzorak[1];
            for (Future<Integer> x : f) assertEquals(200, x.get(60, TimeUnit.SECONDS));
            long ukupnoMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("Navala pomoći " + n + " zahteva: najviše aktivnih veza u spavanju " + najvise + " ("
                    + uzoraka + " uzoraka), ukupno " + ukupnoMs + " ms");
            assertTrue(uzoraka > 20, "premalo uzoraka: " + uzoraka);
            assertTrue(najvise <= 2, "aktivnih veza dok zahtevi spavaju: " + najvise);
            assertTrue(ukupnoMs >= GRANICA.toMillis(), "granica nije važila: " + ukupnoMs + " ms");
        } finally {
            niti.shutdownNow();
            jdbc.update("delete from obavestenja");
            jdbc.update("delete from pokusaji where vrsta = 'POMOC_INDEKS'");
        }
    }

    /** 503 (slanje nije podešeno) i 429 po IP-u ne zavise od indeksa: bez granice, odgovor odmah. */
    @Test
    void bezGraniceZa503I429PoIpu() throws Exception {
        kodServis.minimalnoTrajanje(Duration.ofSeconds(2));
        mejl.dostupan(false);
        long t0 = System.nanoTime();
        assertEquals(503, assertThrows(tri.novica.gfssystem.exceptions.SystemException.class,
                () -> kodServis.posalji(TestStudenti.noviIndeks(), "t7-x")).getCode());
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1500);

        mejl.dostupan(true);
        kodServis.minimalnoTrajanje(Duration.ZERO);
        String ip = "t7-" + UUID.randomUUID().toString().substring(0, 8);
        kljucevi.add(ip);
        for (int i = 0; i < KodServis.LIMIT_SLANJA_IP; i++) {
            String indeks = TestStudenti.noviIndeks();
            kljucevi.add(kodServis.kljucIndeksa(indeks));
            kodServis.posalji(indeks, ip);
        }
        kodServis.minimalnoTrajanje(Duration.ofSeconds(2));
        long t1 = System.nanoTime();
        assertEquals(429, assertThrows(tri.novica.gfssystem.exceptions.SystemException.class,
                () -> kodServis.posalji(TestStudenti.noviIndeks(), ip)).getCode());
        assertTrue((System.nanoTime() - t1) / 1_000_000 < 1500);
    }
}
