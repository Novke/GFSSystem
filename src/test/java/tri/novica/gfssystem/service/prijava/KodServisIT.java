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
        kljucevi.add(servis.kljucIndeksa(i));
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
        assertEquals("Kod za prijavu u GFS", p.naslov());
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

    /** Akcenat umesto slova ("Ť7-...") i pun širina ("Ｔ７－..."): MySQL ai_ci ih izjednačava sa pravim indeksom. */
    static String saAkcentom(String indeks) {
        return "Ť" + indeks.substring(1);
    }

    static String punaSirina(String indeks) {
        StringBuilder b = new StringBuilder();
        for (char c : indeks.toCharArray()) {
            b.append(c >= '!' && c <= '~' ? (char) (c - '!' + '\uFF01') : c);
        }
        return b.toString();
    }

    /**
     * Review B7 #1: varijanta indeksa koju baza poredi kao isti (akcenat, puna širina) ponaša se kao nepostojeći indeks:
     * lažna maska te varijante, bez mejla i koda, sopstveni brojač; ni tačan kod ne prolazi.
     */
    @Test
    void varijanteIndeksaSePonasajuKaoNepostojeci() throws Exception {
        Student s = studenti.napravi("pera@example.test");
        servis.posalji(s.getIndeks(), noviIp());
        String kod = kodIz(mejl.cekaj(1).getFirst());
        for (String varijanta : List.of(saAkcentom(s.getIndeks()), punaSirina(s.getIndeks()))) {
            String n = tri.novica.gfssystem.utility.IndeksUtil.normalizuj(varijanta);
            kljucevi.add(servis.kljucIndeksa(n));
            // provera da je test smislen: baza ovu varijantu zaista izjednačava sa pravim indeksom
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from studenti where id = ? and UPPER(REPLACE(indeks,' ','')) = ?",
                    Integer.class, s.getId(), n), varijanta);
            assertEquals(new KodOdgovor(PORUKA, maska.lazna(n)), servis.posalji(varijanta, noviIp()), varijanta);
            assertEquals(401, greska(() -> servis.proveri(varijanta, kod, noviIp())).getCode(), varijanta);
            assertEquals(1L, pokusaji.broj(VrstaPokusaja.KOD_SLANJE_STUDENT, servis.kljucIndeksa(n), Duration.ofHours(1)));
        }
        Thread.sleep(300);
        assertEquals(1, mejl.poslate().size());
        assertEquals(1, brojSlanja(s));
        assertEquals(1, aktivni(s).size());
        // pravi indeks i dalje radi, kod nije potrošen
        assertEquals(s.getId(), servis.proveri(s.getIndeks(), kod, noviIp()));
    }

    /** Review B7 #6: ključ brojača za indeks je HMAC ključem iz baze, ne goli SHA-256 (koji se lako obrne). */
    @Test
    void kljucBrojacaZaIndeksJeHmac() {
        String nema = nepostojeci();
        String k = servis.kljucIndeksa(nema);
        assertTrue(k.matches("^i:[0-9a-f]{64}$"), k);
        assertNotEquals("i:" + tri.novica.gfssystem.service.uzivo.TokenHash.od(nema), k);
        assertEquals(k, servis.kljucIndeksa(nema));
        servis.posalji(nema, noviIp());
        assertEquals(1L, pokusaji.broj(VrstaPokusaja.KOD_SLANJE_STUDENT, k, Duration.ofHours(1)));
    }

    /** Review B7 #2: nepostojeći indeks se serijalizuje isto kao postojeći, pa navala ne otkriva da indeks ne postoji. */
    @Test
    void paralelnaSlanjaZaNepostojeciIndeksDaju3Od5() throws Exception {
        List<Object> ishodi = paralelno(nepostojeci(), 5);
        assertEquals(3, Collections.frequency(ishodi, PORUKA), ishodi.toString());
        assertEquals(2, Collections.frequency(ishodi, 429), ishodi.toString());
        assertEquals(0, drzaneBrave());
    }

    /** Brava po indeksu ({@code GET_LOCK}) se otpušta posle svakog ishoda: uspeh, bez naloga, limit (429). */
    @Test
    void bravaSeOtpustaPosleSvakogIshoda() {
        Student s = studenti.napravi("pera@example.test");
        for (int i = 0; i < 3; i++) servis.posalji(s.getIndeks(), noviIp());
        assertEquals(0, drzaneBrave());
        assertEquals(429, greska(() -> servis.posalji(s.getIndeks(), noviIp())).getCode());
        assertEquals(0, drzaneBrave());
        servis.posalji(nepostojeci(), noviIp());
        assertEquals(0, drzaneBrave());
    }

    /** Korisničke brave {@code gfs-kod:*} koje sada drži bilo koja veza (performance_schema). */
    int drzaneBrave() {
        return jdbc.queryForObject("""
                select count(*) from performance_schema.metadata_locks
                where OBJECT_TYPE = 'USER LEVEL LOCK' and OBJECT_NAME like '%gfs-kod:%'""", Integer.class);
    }

    /** Review B7 #3: posalji i proveri ne odgovaraju pre donje granice trajanja, ni za jedan ishod. */
    @Test
    void minimalnoTrajanjeVaziZaSveIshode() {
        Student s = studenti.napravi("pera@example.test");
        servis.minimalnoTrajanje(Duration.ofMillis(250));
        try {
            String ip = noviIp();
            assertTrajeBar(250, () -> servis.posalji(s.getIndeks(), ip));
            mejl.cekaj(1);
            assertTrajeBar(250, () -> servis.posalji(nepostojeci(), ip));
            servis.posalji(s.getIndeks(), ip);
            servis.posalji(s.getIndeks(), ip);
            assertTrajeBar(250, () -> greska(() -> servis.posalji(s.getIndeks(), ip)));   // 429
            assertTrajeBar(250, () -> greska(() -> servis.proveri(nepostojeci(), "123456", ip)));   // 401
            String aktivan = aktivanKod(s, mejl.cekaj(3));
            String pogresan = aktivan.equals("000000") ? "111111" : "000000";
            assertTrajeBar(250, () -> greska(() -> servis.proveri(s.getIndeks(), pogresan, ip)));   // 401
            assertTrajeBar(250, () -> assertEquals(s.getId(), servis.proveri(s.getIndeks(), aktivan, ip)));
        } finally {
            servis.minimalnoTrajanje(Duration.ZERO);
        }
    }

    /** Kod iz mejlova koji odgovara jedinom aktivnom kodu studenta (mejlovi mogu stići drugim redom, 2 niti). */
    String aktivanKod(Student s, List<MejlPoruka> poruke) {
        KodZaPrijavu k = aktivni(s).getFirst();
        return poruke.stream().map(KodServisIT::kodIz)
                .filter(kod -> tri.novica.gfssystem.service.uzivo.TokenHash.od(k.getSo() + kod).equals(k.getKodHes()))
                .findFirst().orElseThrow();
    }

    static void assertTrajeBar(long ms, Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        long trajalo = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(trajalo >= ms, "trajalo " + trajalo + " ms, granica " + ms);
    }

    /** Indeks od samih neprekidivih razmaka prolazi {@code @NotBlank}, ali se normalizuje u prazan: ne sme pogoditi nikog. */
    @Test
    void prazanPosleNormalizacijeNePogadjaStudentaSaPraznimIndeksom() throws Exception {
        Student prazan = studenti.napravi("", 1990 + (int) (Math.random() * 30), "prazan@example.test");
        String nbsp = "\u00A0\u202F";
        kljucevi.add(servis.kljucIndeksa(""));
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
    void zahtevPrekoLimitaSaIstogIpaJe429IZaNepostojeci() {
        String ip = noviIp();
        Student s = studenti.napravi("zika@example.test");
        for (int i = 0; i < KodServis.LIMIT_SLANJA_IP; i++) {
            servis.posalji(nepostojeci(), ip);
        }
        SystemException e1 = greska(() -> servis.posalji(nepostojeci(), ip));
        SystemException e2 = greska(() -> servis.posalji(s.getIndeks(), ip));
        assertEquals(429, e1.getCode());
        assertEquals(PREVISE, e1.getMessage());
        assertEquals(429, e2.getCode());
        assertEquals(PREVISE, e2.getMessage());
        assertEquals(KodServis.LIMIT_SLANJA_IP, pokusaji.broj(VrstaPokusaja.KOD_SLANJE_IP, ip, Duration.ofHours(1)));
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
        assertTrue(p.stream().allMatch(m -> m.za().equals("pera@example.test")));
        assertEquals(1, aktivni(s).size());
        assertEquals(s.getId(), servis.proveri(s.getIndeks(), aktivanKod(s, p), noviIp()));
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

    // ---------------------------------------------------------------- indeks u tri dela: (indeks, godina)

    /** Slučajan broj indeksa i par godina koje test koristi samo za sebe (sintetički podaci). */
    int slucajanBroj() {
        return 1 + new java.util.Random().nextInt(999);
    }

    @Test
    void parIndeksGodinaNalaziStudentaTacnoTeGeneracije() {
        int n = slucajanBroj();
        // isti broj u dve generacije; jedan zapisan sa vodećom nulom, drugi sa sufiksom godine (stari oblik)
        Student stariji = studenti.napravi(String.format("GD%03d", n), 2003, "stariji@example.test");
        Student mladji = studenti.napravi("GD" + n + "/2004", 2004, "mladji@example.test");
        kljucevi.add(servis.kljucIndeksa("GD" + n + "/2005"));

        servis.posalji("GD" + n, 2003, noviIp());
        assertEquals("stariji@example.test", mejl.cekaj(1).getFirst().za());
        servis.posalji("gd 0" + n, 2004, noviIp());
        assertEquals("mladji@example.test", mejl.cekaj(2).get(1).za());
        // nepoznata generacija: isti odgovor kao za nepostojeći indeks, bez mejla
        KodOdgovor o = servis.posalji("GD" + n, 2005, noviIp());
        assertEquals(PORUKA, o.poruka());
        assertEquals(2, mejl.poslate().size());

        String kodMladjeg = kodIz(mejl.poslate().get(1));
        String kodStarijeg = kodIz(mejl.poslate().get(0));
        if (!kodMladjeg.equals(kodStarijeg)) {
            assertEquals(401, greska(() -> servis.proveri("GD" + n, 2003, kodMladjeg, noviIp())).getCode());
        }
        assertEquals(mladji.getId(), servis.proveri("GD" + n, 2004, kodMladjeg, noviIp()));
        assertEquals(stariji.getId(), servis.proveri("GD" + n, 2003, kodStarijeg, noviIp()));
    }

    @Test
    void noviOblikPrihvataSamoSmerIBroj() {
        Student s = studenti.napravi("pera@example.test"); // "T7-.../2025": nije javni oblik GD + broj
        String bezGodine = s.getIndeks().replace("/2025", "");
        kljucevi.add(servis.kljucIndeksa(bezGodine + "/2025"));
        servis.posalji(bezGodine, 2025, noviIp());
        assertEquals(0, mejl.poslate().size());
        // stari oblik (jedno polje sa godinom) i dalje radi, privremeno
        servis.posalji(s.getIndeks(), noviIp());
        assertEquals("pera@example.test", mejl.cekaj(1).getFirst().za());
    }

    // ---------------------------------------------------------------- kod za diktiranje (plan C)

    @Test
    void kodZaDiktiranjeRadiBezMejlaIBezSmtpa() {
        mejl.dostupan(false);
        Student s = studenti.napravi(null);
        KodServis.DiktiranKod d = servis.kodZaDiktiranje(s.getId());
        assertTrue(d.kod().matches("\\d{6}"));
        assertEquals(LocalDateTime.now(clock).plusMinutes(10), d.istice());
        assertFalse(d.toString().contains(d.kod()));
        assertEquals(0, mejl.poslate().size());
        assertEquals("NASTAVNIK", jdbc.queryForObject(
                "select nacin from kodovi_za_prijavu where student_id = ?", String.class, s.getId()));
        assertEquals(s.getId(), servis.proveri(s.getIndeks(), d.kod(), noviIp()));
        assertEquals(401, greska(() -> servis.proveri(s.getIndeks(), d.kod(), noviIp())).getCode());
        assertEquals(404, greska(() -> servis.kodZaDiktiranje(-1L)).getCode());
    }

    @Test
    void ponovnoSlanjeMejlaNePonistavaIzdiktiranKod() {
        Student s = studenti.napravi("pera@example.test");
        KodServis.DiktiranKod d = servis.kodZaDiktiranje(s.getId());
        servis.posalji(s.getIndeks(), noviIp());
        servis.posalji(s.getIndeks(), noviIp());
        String mejlKod = kodIz(mejl.cekaj(2).get(1));
        // aktivni: poslednji kod sa mejla i kod za diktiranje
        assertEquals(2, aktivni(s).size());
        // nov kod za diktiranje poništava samo prethodni kod za diktiranje
        KodServis.DiktiranKod d2 = servis.kodZaDiktiranje(s.getId());
        assertEquals(2, aktivni(s).size());
        if (!d.kod().equals(d2.kod()) && !d.kod().equals(mejlKod)) {
            assertEquals(401, greska(() -> servis.proveri(s.getIndeks(), d.kod(), noviIp())).getCode());
        }
        // uspeh jednim kodom poništava sve ostale
        assertEquals(s.getId(), servis.proveri(s.getIndeks(), d2.kod(), noviIp()));
        assertEquals(List.of(), aktivni(s));
        if (!mejlKod.equals(d2.kod())) {
            assertEquals(401, greska(() -> servis.proveri(s.getIndeks(), mejlKod, noviIp())).getCode());
        }
    }

    @Test
    void kodZaDiktiranjeImaUkupanLimitPoSatu() {
        jdbc.update("delete from pokusaji where vrsta = 'KOD_DIKTIRAN'");
        try {
            Student s = studenti.napravi(null);
            for (int i = 0; i < KodServis.LIMIT_DIKTIRANIH; i++) servis.kodZaDiktiranje(s.getId());
            SystemException e = greska(() -> servis.kodZaDiktiranje(s.getId()));
            assertEquals(429, e.getCode());
            assertEquals(PREVISE, e.getMessage());
            assertEquals(1, aktivni(s).size());
        } finally {
            jdbc.update("delete from pokusaji where vrsta = 'KOD_DIKTIRAN'");
        }
    }
}
