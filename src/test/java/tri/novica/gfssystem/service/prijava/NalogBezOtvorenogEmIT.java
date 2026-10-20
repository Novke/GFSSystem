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
        assertFalse(imaOtvorenEm("GET", "/public/nalog/ja"));
        // nastavničke i ostale javne putanje zadržavaju open-in-view (lenjo učitavanje u kontrolerima)
        assertTrue(imaOtvorenEm("GET", "/studenti"));
        assertTrue(imaOtvorenEm("GET", "/grupe/1/pregled"));
        assertTrue(imaOtvorenEm("GET", "/public/upis/x"));
        assertTrue(imaOtvorenEm("GET", "/auth/ja"));
    }

    /**
     * Navala od 20 zahteva ({@code kod} i {@code prijava}) sa granicom 3 s: dok zahtevi spavaju (prozor 1,5-2,8 s, posao u
     * bazi traje desetine ms), aktivnih veza je najviše 2 (pre ispravke svih 10 iz pula, ostali čekaju).
     */
    @Test
    void navalaUSpavanjuNeDrziVeze() throws Exception {
        HikariPoolMXBean pul = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        Student s = studenti.napravi("ana@example.test");
        kodServis.minimalnoTrajanje(Duration.ofSeconds(3));
        int n = 20;
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            List<Future<Integer>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ip = "2001:db8:b7::" + UUID.randomUUID().toString().substring(0, 4);
                kljucevi.add(ip);
                String indeks = i == 0 ? s.getIndeks() : TestStudenti.noviIndeks();
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
            int najvise = 0;
            int uzoraka = 0;
            while (true) {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                if (ms > 2800) break;
                if (ms >= 1500) {
                    najvise = Math.max(najvise, pul.getActiveConnections());
                    uzoraka++;
                }
                Thread.sleep(10);
            }
            List<Integer> statusi = new ArrayList<>();
            for (Future<Integer> x : f) statusi.add(x.get(60, TimeUnit.SECONDS));
            long ukupnoMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("Navala " + n + " zahteva: najviše aktivnih veza u spavanju " + najvise + " (" + uzoraka
                    + " uzoraka), ukupno " + ukupnoMs + " ms, statusi " + statusi);
            assertTrue(uzoraka > 20, "premalo uzoraka: " + uzoraka);
            assertTrue(najvise <= 2, "aktivnih veza dok zahtevi spavaju: " + najvise);
            assertTrue(ukupnoMs >= 3000, "granica nije važila: " + ukupnoMs + " ms");
            for (int i = 0; i < n; i++) assertEquals(i % 2 == 0 ? 200 : 401, statusi.get(i), statusi.toString());
        } finally {
            niti.shutdownNow();
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
        for (int i = 0; i < 10; i++) {
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
