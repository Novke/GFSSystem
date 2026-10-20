package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.prijava.KodOdgovor;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.KodZaPrijavu;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.KodZaPrijavuRepository;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.mejl.MejlPoruka;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KodServis} nad pravom MySQL bazom (spec 5.1, 5.2), sa lažnim mejlom i pomerljivim satom. Bez
 * {@code @Transactional}: servis sam vodi transakcije (zaključan red studenta), a test ih posmatra spolja. Svaki test ima
 * svoje IP-ove i studente, pa se limiti ne mešaju.
 */
@SpringBootTest
@Import({PrijavaSatConfig.class, LazniMejlServis.class})
class KodServisIT {

    static final Pattern KOD = Pattern.compile("\\b(\\d{6})\\b");
    static final String PORUKA = "Ako nalog postoji, kod je poslat.";
    static final String NEISPRAVAN = "Kod nije ispravan ili je istekao.";
    static final String PREVISE = "Previše pokušaja. Pokušaj ponovo kasnije.";

    @Autowired KodServis servis;
    @Autowired MaskaMejla maska;
    @Autowired KodZaPrijavuRepository kodovi;
    @Autowired StudentRepository studentRepository;
    @Autowired PokusajiServis pokusaji;
    @Autowired LazniMejlServis mejl;
    @Autowired PomerljivSat sat;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    TestStudenti studenti;
    final List<String> kljucevi = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sat.postavi(Instant.now());
        mejl.ocisti();
        studenti = new TestStudenti(studentRepository, jdbc);
    }

    @AfterEach
    void ocisti() {
        mejl.ocisti();
        studenti.obrisi();
        for (String k : kljucevi) {
            jdbc.update("delete from pokusaji where kljuc = ?", k);
        }
    }

    String noviIp() {
        String ip = "t7-" + UUID.randomUUID().toString().substring(0, 8);
        kljucevi.add(ip);
        return ip;
    }

    String nepostojeci() {
        String i = TestStudenti.noviIndeks();
        kljucevi.add(KodServis.kljucIndeksa(i));
        return i;
    }

    static String kodIz(MejlPoruka p) {
        Matcher m = KOD.matcher(p.telo());
        assertTrue(m.find(), "mejl nema 6 cifara");
        return m.group(1);
    }

    List<KodZaPrijavu> aktivni(Student s) {
        return kodovi.findAktivne(s.getId(), LocalDateTime.now(clock));
    }

    long brojSlanja(Student s) {
        return pokusaji.broj(VrstaPokusaja.KOD_SLANJE_STUDENT, String.valueOf(s.getId()), Duration.ofHours(1));
    }

    static SystemException greska(Runnable r) {
        return assertThrows(SystemException.class, r::run);
    }

    @Test
    void postojeciStudentDobijaKodNaSvojMejl() {
        Student s = studenti.napravi("pera.peric@example.test");
        KodOdgovor o = servis.posalji(s.getIndeks(), noviIp());
        assertEquals(new KodOdgovor(PORUKA, "p***@example.test"), o);

        MejlPoruka p = mejl.cekaj(1).getFirst();
        assertEquals("pera.peric@example.test", p.za());
        assertEquals("GFS: kod za prijavu", p.naslov());
        assertTrue(p.telo().contains("važi 10 minuta"), p.telo());
        String kod = kodIz(p);

        KodZaPrijavu k = aktivni(s).getFirst();
        assertEquals(32, k.getSo().length());
        assertEquals(64, k.getKodHes().length());
        assertFalse(k.getKodHes().contains(kod));
        // datetime(3) zaokružuje: poredi se sa tolerancijom, ne jednakošću
        long razlikaMs = Duration.between(LocalDateTime.now(clock).plusMinutes(10), k.getIstice()).abs().toMillis();
        assertTrue(razlikaMs < 1000, "istice odstupa " + razlikaMs + " ms od 10 minuta");
        assertEquals(1, brojSlanja(s));
    }

    @Test
    void indeksSeNormalizuje() {
        Student s = studenti.napravi("ana@example.test");
        servis.posalji("  " + s.getIndeks().toLowerCase() + " ", noviIp());
        assertEquals(1, mejl.cekaj(1).size());
        assertEquals(1, aktivni(s).size());
    }

    @Test
    void nepostojeciIBezMejlaDajuIstoTeloBezMejla() throws Exception {
        String nema = nepostojeci();
        Student bezMejla = studenti.napravi(null);
        Student praznogMejla = studenti.napravi("  ");

        KodOdgovor o1 = servis.posalji(nema, noviIp());
        KodOdgovor o2 = servis.posalji(bezMejla.getIndeks(), noviIp());
        KodOdgovor o3 = servis.posalji(praznogMejla.getIndeks(), noviIp());

        assertEquals(new KodOdgovor(PORUKA, maska.lazna(nema)), o1);
        assertEquals(new KodOdgovor(PORUKA, maska.lazna(bezMejla.getIndeks())), o2);
        assertEquals(new KodOdgovor(PORUKA, maska.lazna(praznogMejla.getIndeks())), o3);
        // ista lažna maska svaki put
        assertEquals(o1, servis.posalji(nema, noviIp()));
        Thread.sleep(300);
        assertEquals(List.of(), mejl.poslate());
        assertEquals(0, mejl.pokusaja());
        assertEquals(List.of(), aktivni(bezMejla));
    }

    /** Indeks od samih neprekidivih razmaka prolazi {@code @NotBlank}, ali se normalizuje u prazan: ne sme pogoditi nikog. */
    @Test
    void prazanPosleNormalizacijeNePogadjaStudentaSaPraznimIndeksom() throws Exception {
        Student prazan = studenti.napravi("", 1990 + (int) (Math.random() * 30), "prazan@example.test");
        String nbsp = "\u00A0\u202F";
        kljucevi.add(KodServis.kljucIndeksa(""));
        KodOdgovor o = servis.posalji(nbsp, noviIp());
        assertEquals(new KodOdgovor(PORUKA, maska.lazna("")), o);
        assertEquals(401, greska(() -> servis.proveri(nbsp, "123456", noviIp())).getCode());
        Thread.sleep(300);
        assertEquals(List.of(), mejl.poslate());
        assertEquals(List.of(), aktivni(prazan));
    }

    @Test
    void cetvrtoSlanjeZaIstogStudentaUSatuJe429() {
        Student s = studenti.napravi("mika@example.test");
        for (int i = 0; i < 3; i++) {
            servis.posalji(s.getIndeks(), noviIp());
        }
        SystemException e = greska(() -> servis.posalji(s.getIndeks(), noviIp()));
        assertEquals(429, e.getCode());
        assertEquals(PREVISE, e.getMessage());
        assertEquals(3, brojSlanja(s));
        assertEquals(3, mejl.cekaj(3).size());
        assertEquals(1, aktivni(s).size());

        // posle sat vremena opet može
        sat.pomeri(Duration.ofMinutes(61));
        assertEquals(PORUKA, servis.posalji(s.getIndeks(), noviIp()).poruka());
    }

    @Test
    void limitPoIndeksuJeIstiZaNepostojeciIBezMejla() {
        String nema = nepostojeci();
        Student bezMejla = studenti.napravi(null);
        for (int i = 0; i < 3; i++) {
            servis.posalji(nema, noviIp());
            servis.posalji(bezMejla.getIndeks(), noviIp());
        }
        // isti odgovor kao za postojećeg studenta: 4. zahtev u satu je 429, pa se iz limita ne vidi da li nalog postoji
        SystemException e1 = greska(() -> servis.posalji(nema, noviIp()));
        SystemException e2 = greska(() -> servis.posalji(bezMejla.getIndeks(), noviIp()));
        assertEquals(429, e1.getCode());
        assertEquals(PREVISE, e1.getMessage());
        assertEquals(429, e2.getCode());
        assertEquals(PREVISE, e2.getMessage());
    }

    @Test
    void jedanaestiZahtevSaIstogIpaJe429IZaNepostojeci() {
        String ip = noviIp();
        Student s = studenti.napravi("zika@example.test");
        for (int i = 0; i < 10; i++) {
            servis.posalji(nepostojeci(), ip);
        }
        SystemException e1 = greska(() -> servis.posalji(nepostojeci(), ip));
        SystemException e2 = greska(() -> servis.posalji(s.getIndeks(), ip));
        assertEquals(429, e1.getCode());
        assertEquals(PREVISE, e1.getMessage());
        assertEquals(429, e2.getCode());
        assertEquals(PREVISE, e2.getMessage());
        assertEquals(10, pokusaji.broj(VrstaPokusaja.KOD_SLANJE_IP, ip, Duration.ofHours(1)));
        // odbijen zahtev ne pravi kod i ne troši studentov limit
        assertEquals(0, brojSlanja(s));
        assertEquals(List.of(), aktivni(s));
        // drugi IP prolazi
        assertEquals(PORUKA, servis.posalji(s.getIndeks(), noviIp()).poruka());
    }

    @Test
    void novKodPonistavaStari() {
        Student s = studenti.napravi("laza@example.test");
        servis.posalji(s.getIndeks(), noviIp());
        String prvi = kodIz(mejl.cekaj(1).get(0));
        servis.posalji(s.getIndeks(), noviIp());
        String drugi = kodIz(mejl.cekaj(2).get(1));

        assertEquals(1, aktivni(s).size());
        assertEquals(2L, (long) jdbc.queryForObject(
                "select count(*) from kodovi_za_prijavu where student_id = ?", Long.class, s.getId()));
        if (!prvi.equals(drugi)) {
            assertEquals(401, greska(() -> servis.proveri(s.getIndeks(), prvi, noviIp())).getCode());
        }
        assertEquals(s.getId(), servis.proveri(s.getIndeks(), drugi, noviIp()));
    }

    @Test
    void petPogresnihPonistavaKod() {
        Student s = studenti.napravi("pera@example.test");
        String ip = noviIp();
        servis.posalji(s.getIndeks(), ip);
        String kod = kodIz(mejl.cekaj(1).getFirst());
        String pogresan = kod.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            SystemException e = greska(() -> servis.proveri(s.getIndeks(), pogresan, ip));
            assertEquals(401, e.getCode());
            assertEquals(NEISPRAVAN, e.getMessage());
        }
        Integer pogresni = jdbc.queryForObject(
                "select pogresni from kodovi_za_prijavu where student_id = ?", Integer.class, s.getId());
        assertEquals(5, pogresni);
        assertNotNull(jdbc.queryForObject(
                "select ponisten from kodovi_za_prijavu where student_id = ?", LocalDateTime.class, s.getId()));
        // i tačan kod sada pada
        SystemException e = greska(() -> servis.proveri(s.getIndeks(), kod, ip));
        assertEquals(401, e.getCode());
        assertEquals(6, pokusaji.broj(VrstaPokusaja.KOD_GRESKA_IP, ip, Duration.ofHours(1)));
    }

    @Test
    void istekaoKodJe401() {
        Student s = studenti.napravi("pera@example.test");
        servis.posalji(s.getIndeks(), noviIp());
        String kod = kodIz(mejl.cekaj(1).getFirst());
        sat.pomeri(Duration.ofMinutes(11));
        SystemException e = greska(() -> servis.proveri(s.getIndeks(), kod, noviIp()));
        assertEquals(401, e.getCode());
        assertEquals(NEISPRAVAN, e.getMessage());
    }

    @Test
    void tacanKodProlaziSamoJednom() {
        Student s = studenti.napravi("pera@example.test");
        servis.posalji(s.getIndeks(), noviIp());
        String kod = kodIz(mejl.cekaj(1).getFirst());
        sat.pomeri(Duration.ofMinutes(9));
        assertEquals(s.getId(), servis.proveri(" " + s.getIndeks().toLowerCase(), kod, noviIp()));
        assertNotNull(jdbc.queryForObject(
                "select iskoriscen from kodovi_za_prijavu where student_id = ?", LocalDateTime.class, s.getId()));
        SystemException e = greska(() -> servis.proveri(s.getIndeks(), kod, noviIp()));
        assertEquals(401, e.getCode());
        assertEquals(NEISPRAVAN, e.getMessage());
    }

    @Test
    void nepostojeciIndeksIBezKodaDajuIsti401() {
        Student bezKoda = studenti.napravi("pera@example.test");
        SystemException e1 = greska(() -> servis.proveri(nepostojeci(), "123456", noviIp()));
        SystemException e2 = greska(() -> servis.proveri(bezKoda.getIndeks(), "123456", noviIp()));
        assertEquals(401, e1.getCode());
        assertEquals(NEISPRAVAN, e1.getMessage());
        assertEquals(401, e2.getCode());
        assertEquals(NEISPRAVAN, e2.getMessage());
    }

    @Test
    void dvadesetPogresnihSaIpaJe429() {
        String ip = noviIp();
        Student s = studenti.napravi("pera@example.test");
        servis.posalji(s.getIndeks(), noviIp());
        String kod = kodIz(mejl.cekaj(1).getFirst());
        for (int i = 0; i < 20; i++) {
            assertEquals(401, greska(() -> servis.proveri(nepostojeci(), "123456", ip)).getCode());
        }
        SystemException e = greska(() -> servis.proveri(s.getIndeks(), kod, ip));
        assertEquals(429, e.getCode());
        assertEquals(PREVISE, e.getMessage());
        // kod nije potrošen ni oštećen: sa drugog IP-a prolazi
        assertEquals(s.getId(), servis.proveri(s.getIndeks(), kod, noviIp()));
    }

    @Test
    void bezSmtpaJe503ZaSveIndekse() {
        mejl.dostupan(false);
        Student s = studenti.napravi("pera@example.test");
        String ip = noviIp();
        for (String indeks : List.of(s.getIndeks(), nepostojeci())) {
            SystemException e = greska(() -> servis.posalji(indeks, ip));
            assertEquals(503, e.getCode());
            assertEquals("Slanje koda trenutno nije moguće. Javi se nastavniku.", e.getMessage());
        }
        assertEquals(List.of(), aktivni(s));
        assertEquals(0, brojSlanja(s));
    }

    /** Review focus 2: dva istovremena zahteva za istog studenta: tačno jedan važeći kod, brojač 2. */
    @Test
    void dvaParalelnaSlanjaDajuJedanAktivanKodIBrojac2() throws Exception {
        Student s = studenti.napravi("pera@example.test");
        List<Object> ishodi = paralelno(s.getIndeks(), 2);
        assertEquals(List.of(PORUKA, PORUKA), ishodi);
        assertEquals(1, aktivni(s).size());
        assertEquals(2, brojSlanja(s));
        assertEquals(2, mejl.cekaj(2).size());
    }

    /** Pet istovremenih zahteva ne probija limit 3/h: tri prolaze, dva su 429, jedan kod važi. */
    @Test
    void paralelnaSlanjaNeProbijajuLimit() throws Exception {
        Student s = studenti.napravi("pera@example.test");
        List<Object> ishodi = paralelno(s.getIndeks(), 5);
        assertEquals(3, Collections.frequency(ishodi, PORUKA), ishodi.toString());
        assertEquals(2, Collections.frequency(ishodi, 429), ishodi.toString());
        assertEquals(1, aktivni(s).size());
        assertEquals(3, brojSlanja(s));
    }

    /** {@code n} niti kreće istovremeno (latch); ishod je poruka odgovora ili HTTP kod greške. */
    private List<Object> paralelno(String indeks, int n) throws Exception {
        CountDownLatch spremni = new CountDownLatch(n);
        CountDownLatch kreni = new CountDownLatch(1);
        ExecutorService niti = Executors.newFixedThreadPool(n);
        try {
            List<Future<Object>> f = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ip = noviIp();
                f.add(niti.submit(() -> {
                    spremni.countDown();
                    kreni.await();
                    try {
                        return servis.posalji(indeks, ip).poruka();
                    } catch (SystemException e) {
                        return e.getCode();
                    }
                }));
            }
            assertTrue(spremni.await(10, TimeUnit.SECONDS));
            kreni.countDown();
            List<Object> ishodi = new ArrayList<>();
            for (Future<Object> x : f) ishodi.add(x.get(60, TimeUnit.SECONDS));
            return ishodi;
        } finally {
            niti.shutdownNow();
        }
    }

    @Test
    void kodSaKarticeBezLimitaPoStudentuAliSaUkupnim() {
        jdbc.update("delete from pokusaji where vrsta = 'KOD_SA_KARTICE'");
        Student s = studenti.napravi("pera@example.test");
        for (int i = 0; i < 3; i++) servis.posalji(s.getIndeks(), noviIp());
        // studentov limit je potrošen, nastavnik ipak šalje
        servis.posaljiOdNastavnika(s.getId());
        List<MejlPoruka> p = mejl.cekaj(4);
        assertEquals("pera@example.test", p.get(3).za());
        assertEquals(1, aktivni(s).size());
        assertEquals(s.getId(), servis.proveri(s.getIndeks(), kodIz(p.get(3)), noviIp()));
        assertEquals(3, brojSlanja(s));

        for (int i = 1; i < 20; i++) servis.posaljiOdNastavnika(s.getId());
        SystemException e = greska(() -> servis.posaljiOdNastavnika(s.getId()));
        assertEquals(429, e.getCode());
        assertEquals(PREVISE, e.getMessage());
        jdbc.update("delete from pokusaji where vrsta = 'KOD_SA_KARTICE'");
    }

    @Test
    void kodSaKarticeZaStudentaBezMejlaJe409() {
        Student s = studenti.napravi(null);
        SystemException e = greska(() -> servis.posaljiOdNastavnika(s.getId()));
        assertEquals(409, e.getCode());
        assertEquals("Student nema mejl.", e.getMessage());
        assertEquals(404, greska(() -> servis.posaljiOdNastavnika(-1L)).getCode());
    }
}
