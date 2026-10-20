package tri.novica.gfssystem.service.prijava;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.prijava.KodOdgovor;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.KodZaPrijavu;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.KodZaPrijavuRepository;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.mejl.MejlServis;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.IndeksUtil;
import tri.novica.gfssystem.utility.TokenGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Kod za prijavu studenta bez lozinke (spec 5.1, 5.2): 6 cifara iz {@link SecureRandom}, 10 minuta, jednokratan, najviše 5
 * pogrešnih unosa; u bazi samo {@code SHA-256(so + kod)}. Mejl ide posle commit-a, asinhrono ({@code MejlPosleCommita}).
 * <p>
 * <b>Odgovor ne sme odati da li nalog postoji.</b> {@link #posalji} vraća isto telo za postojećeg studenta, studenta bez
 * mejla i nepostojeći indeks (prava ili lažna maska istog oblika), limit 3/h važi po indeksu i za nepostojeći, a 503 bez
 * SMTP-a dolazi pre svega. {@link #proveri} daje isti 401 za pogrešan kod, istekao kod i nepostojeći indeks.
 * <p>
 * <b>Istovremeni zahtevi za istog studenta</b> idu redom: brojanje, poništavanje starih kodova, upis novog i brojača su u
 * jednoj transakciji pod zaključanim redom studenta ({@code SELECT ... FOR UPDATE}), sa izolacijom {@code READ COMMITTED},
 * da druga transakcija posle čekanja na zaključavanje vidi ono što je prva upisala (pod {@code REPEATABLE READ} bi čitala
 * snimak od pre čekanja i propustila nov kod i brojač). Brojač po studentu se zato upisuje u istoj transakciji
 * ({@link PokusajiServis#zabeleziUTransakciji}), a ne u {@code REQUIRES_NEW}. Brojači po IP-u (bez zaključavanja) i lažna
 * maska (prvi poziv pravi ključ) idu van te transakcije, pa jedan zahtev nikad ne drži dve veze iz pula.
 * <p>
 * <b>Indeks</b> se traži samo kad je posle normalizacije iz bele liste {@code ^[A-Z0-9/.-]{1,30}$}, i onda samo
 * tačnim (binarnim) poređenjem u Javi: MySQL {@code utf8mb4_0900_ai_ci} izjednačava akcente i punu širinu
 * ({@code 'Ť7' = 'T7'}), pa bi varijanta pravog indeksa dobila pravu masku i deljen brojač, a varijanta nepostojećeg
 * novu lažnu masku i nov brojač. Sve van liste se ponaša kao nepostojeći indeks.
 * <p>
 * <b>Svaki indeks se serijalizuje isto</b>, postojao ili ne: prva naredba zaključane transakcije je MySQL brava
 * {@code GET_LOCK('gfs-kod:<hmac>')} na istoj vezi, otpušta se posle commit-a/rollback-a. Bez nje bi navala zahteva za
 * nepostojeći indeks (nema reda za {@code FOR UPDATE}) prošla limit 3/h, a za postojeći ne. Redosled je uvek brava
 * indeksa, pa red studenta; niko ne uzima red studenta pa bravu, pa zastoja nema.
 * <p>
 * <b>Donja granica trajanja</b> ({@code gfs.nalog.minimalno-trajanje-ms}, podrazumevano 200 ms, monotono vreme):
 * {@link #posalji} i {@link #proveri} ne odgovaraju ranije ni na 200, ni na 401, ni na 429 po indeksu, pa razlika od
 * nekoliko upisa između postojećeg i nepostojećeg naloga (i {@link #previseKodova}) ne izlazi u vreme odgovora. Izuzetak
 * su 503 (slanje nije podešeno) i 429 po IP-u: oni ne zavise od indeksa, pa vraćaju odmah (inače bi napadač preko
 * limita i dalje držao niti zahteva).
 * <p>
 * Log: IP i id-jevi, indeks samo kao prvih 8 hex SHA-256; nikad kod, mejl ni ime.
 */
@Service
@Slf4j
public class KodServis {

    public static final String PORUKA = "Ako nalog postoji, kod je poslat.";
    static final String PREVISE = "Previše pokušaja. Pokušaj ponovo kasnije.";
    static final String NEISPRAVAN = "Kod nije ispravan ili je istekao.";
    static final String BEZ_SMTP = "Slanje koda trenutno nije moguće. Javi se nastavniku.";
    static final String NASLOV = "GFS: kod za prijavu";

    static final Duration TRAJANJE_KODA = Duration.ofMinutes(10);
    static final int MAX_POGRESNIH = 5;
    static final Duration SAT = Duration.ofHours(1);
    static final int LIMIT_SLANJA_IP = 10;
    static final int LIMIT_SLANJA_STUDENT = 3;
    static final int LIMIT_GRESAKA_IP = 20;
    static final int LIMIT_SA_KARTICE = 20;
    /** Ključ ukupnog brojača za kodove sa kartice studenta. */
    static final String UKUPNO = "ukupno";
    private static final Pattern FORMAT_KODA = Pattern.compile("^[0-9]{6}$");
    /** Normalizovan indeks koji se uopšte traži u bazi (ASCII; onboarding forma dozvoljava podskup ovoga). */
    static final Pattern BELA_LISTA_INDEKSA = Pattern.compile("^[A-Z0-9/.-]{1,30}$");
    /** Koliko se najduže čeka MySQL brava indeksa (s); posle toga 429. */
    static final int CEKANJE_BRAVE_S = 10;

    private final StudentRepository studenti;
    private final KodZaPrijavuRepository kodovi;
    private final PokusajiServis pokusaji;
    private final MejlServis mejl;
    private final ApplicationEventPublisher dogadjaji;
    private final MaskaMejla maska;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;
    private final KljuceviAplikacije kljucevi;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate zakljucano;
    /** Lenjo, jer {@link ObavestenjeServis} koristi pomoćne metode ovog servisa (indeks, brava, donja granica). */
    private final ObjectProvider<ObavestenjeServis> obavestenja;
    private final SecureRandom random = new SecureRandom();
    /** Donja granica trajanja odgovora (ns); u testovima 0, osim gde se proverava. */
    private volatile long minimalnoTrajanjeNs;

    public KodServis(StudentRepository studenti, KodZaPrijavuRepository kodovi, PokusajiServis pokusaji, MejlServis mejl,
                     ApplicationEventPublisher dogadjaji, MaskaMejla maska, TokenGenerator tokenGenerator,
                     KljuceviAplikacije kljucevi, JdbcTemplate jdbc, PlatformTransactionManager transakcije, Clock clock,
                     ObjectProvider<ObavestenjeServis> obavestenja,
                     @Value("${gfs.nalog.minimalno-trajanje-ms:200}") long minimalnoTrajanjeMs) {
        this.kljucevi = kljucevi;
        this.obavestenja = obavestenja;
        this.jdbc = jdbc;
        this.minimalnoTrajanjeNs = TimeUnit.MILLISECONDS.toNanos(minimalnoTrajanjeMs);
        this.studenti = studenti;
        this.kodovi = kodovi;
        this.pokusaji = pokusaji;
        this.mejl = mejl;
        this.dogadjaji = dogadjaji;
        this.maska = maska;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
        this.zakljucano = new TransactionTemplate(transakcije);
        this.zakljucano.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Ishod zaključanog dela slanja: maska za odgovor, ili limit po indeksu ({@code studentId} može biti null). */
    private record Slanje(String maska, boolean limit, Long studentId) {}

    /** Indeks zahteva: normalizovan, HMAC (ključ brojača i ime brave) i da li sme u bazu. */
    record Indeks(String n, String hmac, boolean trazi) {}

    Indeks indeks(String indeks) {
        String n = normalizuj(indeks);
        return new Indeks(n, hmacIndeksa(n), BELA_LISTA_INDEKSA.matcher(n).matches());
    }

    /**
     * Šalje kod na mejl studenta sa indeksom (najnoviji upis); uvek isti odgovor (spec 5.1). 503 kad slanje nije podešeno,
     * 429 preko 10/h po IP-u (broji se svaki zahtev, i za nepostojeći indeks) ili 3/h po indeksu.
     */
    public KodOdgovor posalji(String indeks, String ip) {
        long pocetak = System.nanoTime();
        // 503 i 429 po IP-u ne zavise od indeksa (ništa ne otkrivaju), pa idu bez donje granice: inače bi napadač
        // preko limita i dalje držao niti zahteva
        if (!mejl.dostupan()) {
            throw new SystemException(BEZ_SMTP, 503);
        }
        if (pokusaji.dostignut(VrstaPokusaja.KOD_SLANJE_IP, ip, LIMIT_SLANJA_IP, SAT)) {
            log.info("Zahtev za kod odbijen (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE, 429);
        }
        try {
            pokusaji.zabelezi(VrstaPokusaja.KOD_SLANJE_IP, ip);
            // HMAC-ovi i lažna maska se računaju uvek (isti posao), a prvi poziv pravi ključeve van zaključane transakcije
            Indeks i = indeks(indeks);
            String lazna = maska.lazna(i.n());

            Slanje s = zakljucano.execute(tx -> posaljiZakljucano(i, lazna));
            if (s.limit()) {
                log.info("Zahtev za kod odbijen (limit po indeksu), indeks={}, student={}, ip={}",
                        trag(i.n()), s.studentId(), ip);
                if (s.studentId() != null) {
                    previseKodova(s.studentId(), ip);
                }
                throw new SystemException(PREVISE, 429);
            }
            return new KodOdgovor(PORUKA, s.maska());
        } finally {
            sacekajDoGranice(pocetak);
        }
    }

    private Slanje posaljiZakljucano(Indeks i, String lazna) {
        zakljucajIndeks(i);
        String n = i.n();
        Optional<Student> student = zakljucajStudenta(i);
        String kljuc = student.map(s -> String.valueOf(s.getId())).orElse(kljucIndeksaIzHmac(i.hmac()));
        Long studentId = student.map(Student::getId).orElse(null);
        if (pokusaji.broj(VrstaPokusaja.KOD_SLANJE_STUDENT, kljuc, SAT) >= LIMIT_SLANJA_STUDENT) {
            return new Slanje(null, true, studentId);
        }
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.KOD_SLANJE_STUDENT, kljuc);
        if (student.isEmpty() || nemaMejl(student.get())) {
            log.info("Zahtev za kod bez naloga ili mejla, indeks={}, student={}", trag(n), studentId);
            return new Slanje(lazna, false, studentId);
        }
        Student s = student.get();
        novKod(s);
        log.info("Kod poslat, student={}", s.getId());
        return new Slanje(maska.maska(s.getEmail()), false, s.getId());
    }

    /**
     * Obaveštenje nastavniku {@code PREVISE_KODOVA} (spec 6): student je dostigao limit od 3 koda na sat; najviše jedno
     * dnevno po studentu ({@link ObavestenjeServis#previseKodova}). Poziva se van zaključane transakcije i pre donje
     * granice trajanja (njen posao se krije u njoj), a neuspeh se guta: obaveštenje nikad ne sme da promeni odgovor 429.
     */
    void previseKodova(Long studentId, String ip) {
        try {
            obavestenja.getObject().previseKodova(studentId, ip);
        } catch (RuntimeException e) {
            // samo klasa izuzetka: poruka može da nosi podatke iz upita
            log.error("Obaveštenje o previše kodova nije upisano, student={}: {}", studentId, e.getClass().getSimpleName());
        }
    }

    /**
     * Proverava kod za indeks; vraća {@code studentId} i troši kod. 429 preko 20 pogrešnih na sat po IP-u; inače svaki
     * neuspeh (nema aktivnog koda, pogrešan, istekao, nepostojeći indeks) daje isti 401 i broji se po IP-u.
     */
    public Long proveri(String indeks, String kod, String ip) {
        long pocetak = System.nanoTime();
        // 429 po IP-u ne zavisi od indeksa ni koda, pa ide bez donje granice (kao u posalji)
        if (pokusaji.dostignut(VrstaPokusaja.KOD_GRESKA_IP, ip, LIMIT_GRESAKA_IP, SAT)) {
            log.info("Provera koda odbijena (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE, 429);
        }
        try {
            Indeks i = indeks(indeks);
            Long studentId = zakljucano.execute(tx -> proveriZakljucano(i, kod));
            if (studentId == null) {
                pokusaji.zabelezi(VrstaPokusaja.KOD_GRESKA_IP, ip);
                log.info("Neispravan kod, indeks={}, ip={}", trag(i.n()), ip);
                throw new SystemException(NEISPRAVAN, 401);
            }
            log.info("Kod iskorišćen, student={}, ip={}", studentId, ip);
            return studentId;
        } finally {
            sacekajDoGranice(pocetak);
        }
    }

    private Long proveriZakljucano(Indeks i, String kod) {
        Optional<Student> student = zakljucajStudenta(i);
        if (student.isEmpty()) {
            return null;
        }
        LocalDateTime sada = LocalDateTime.now(clock);
        List<KodZaPrijavu> aktivni = kodovi.findAktivne(student.get().getId(), sada);
        if (aktivni.isEmpty()) {
            return null;
        }
        KodZaPrijavu k = aktivni.getFirst();
        boolean tacan = kod != null && FORMAT_KODA.matcher(kod).matches()
                && MessageDigest.isEqual(ascii(TokenHash.od(k.getSo() + kod)), ascii(k.getKodHes()));
        if (tacan) {
            k.setIskoriscen(sada);
            kodovi.save(k);
            return student.get().getId();
        }
        k.setPogresni(k.getPogresni() + 1);
        if (k.getPogresni() >= MAX_POGRESNIH) {
            k.setPonisten(sada);
            log.info("Kod poništen posle {} pogrešnih unosa, student={}", MAX_POGRESNIH, student.get().getId());
        }
        kodovi.save(k);
        return null;
    }

    /**
     * Student sa najnovijim upisom za normalizovan indeks, sa zaključanim redom. Samo indeks sa bele liste (prazan, npr.
     * od samih neprekidivih razmaka koje {@code @NotBlank} pušta, nije na njoj), i samo tačno isti posle normalizacije:
     * upit u bazi poredi pod {@code ai_ci} (akcenti, širina), pa se kandidati ovde porede binarno.
     */
    private Optional<Student> zakljucajStudenta(Indeks i) {
        return nadjiStudenta(i).flatMap(s -> studenti.findByIdForUpdate(s.getId()));
    }

    /** Isto bez zaključavanja reda (bela lista i binarno poređenje kao gore): {@link ObavestenjeServis#neStizeKod}. */
    Optional<Student> nadjiStudenta(Indeks i) {
        if (!i.trazi()) {
            return Optional.empty();
        }
        return studenti.findAllPoNormalizovanomIndeksu(i.n()).stream()
                .filter(s -> i.n().equals(IndeksUtil.normalizuj(s.getIndeks())))
                .findFirst();
    }

    /**
     * Prva naredba zaključane transakcije: MySQL brava za indeks na vezi te transakcije ({@code JdbcTemplate} u JPA
     * transakciji koristi istu vezu). Otpušta se u {@code afterCompletion} (posle commit-a ili rollback-a, veza je još
     * vezana za nit), da sledeći zahtev za isti indeks vidi upisan brojač. Ne dobije li je za 10 s: 429.
     */
    private void zakljucajIndeks(Indeks i) {
        if (!uzmiBravu("gfs-kod:", i)) {
            throw new SystemException(PREVISE, 429);
        }
    }

    /**
     * Uzima MySQL bravu {@code prefiks + hmac indeksa} na vezi tekuće transakcije (mora postojati) i otpušta je u
     * {@code afterCompletion}. {@code false} kad je ne dobije za {@value #CEKANJE_BRAVE_S} s. Svaka funkcija ima svoj
     * prefiks (slanje koda, "Ne stiže mi kod"), a redosled je uvek brava indeksa pa red studenta.
     */
    boolean uzmiBravu(String prefiks, Indeks i) {
        String brava = prefiks + i.hmac().substring(0, 32);
        Integer dobijena = jdbc.queryForObject("SELECT GET_LOCK(?, ?)", Integer.class, brava, CEKANJE_BRAVE_S);
        if (dobijena == null || dobijena != 1) {
            log.warn("Brava indeksa nije dobijena za {} s, indeks={}", CEKANJE_BRAVE_S, trag(i.n()));
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    Integer otpustena = jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, brava);
                    if (otpustena == null || otpustena != 1) {
                        log.error("Brava indeksa nije otpuštena (rezultat {}), indeks={}", otpustena, trag(i.n()));
                    }
                } catch (RuntimeException e) {
                    log.error("Brava indeksa nije otpuštena: {}", e.getClass().getSimpleName());
                }
            }
        });
        return true;
    }

    /**
     * Kod sa kartice studenta (nastavnik, spec 5.3): bez limita po studentu, ali najviše 20 na sat ukupno. 409 kad student
     * nema mejl, 404 kad ne postoji, 503 kad slanje nije podešeno.
     */
    public void posaljiOdNastavnika(Long studentId) {
        if (!mejl.dostupan()) {
            throw new SystemException(BEZ_SMTP, 503);
        }
        if (pokusaji.dostignut(VrstaPokusaja.KOD_SA_KARTICE, UKUPNO, LIMIT_SA_KARTICE, SAT)) {
            log.info("Kod sa kartice odbijen (limit ukupno), student={}", studentId);
            throw new SystemException(PREVISE, 429);
        }
        zakljucano.executeWithoutResult(tx -> {
            Student s = studenti.findByIdForUpdate(studentId)
                    .orElseThrow(() -> new SystemException("Ne postoji.", 404));
            if (nemaMejl(s)) {
                throw new SystemException("Student nema mejl.", 409);
            }
            pokusaji.zabeleziUTransakciji(VrstaPokusaja.KOD_SA_KARTICE, UKUPNO);
            novKod(s);
        });
        log.info("Kod poslat sa kartice, student={}", studentId);
    }

    /** Pod zaključanim redom studenta: poništava aktivne kodove, upisuje nov i objavljuje mejl (šalje se posle commit-a). */
    private void novKod(Student s) {
        LocalDateTime sada = LocalDateTime.now(clock);
        for (KodZaPrijavu stari : kodovi.findAktivne(s.getId(), sada)) {
            stari.setPonisten(sada);
            kodovi.save(stari);
        }
        String kod = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String so = tokenGenerator.novi();
        KodZaPrijavu k = new KodZaPrijavu();
        k.setStudent(s);
        k.setSo(so);
        k.setKodHes(TokenHash.od(so + kod));
        k.setKreirano(sada);
        k.setIstice(sada.plus(TRAJANJE_KODA));
        k.setPogresni(0);
        kodovi.save(k);
        dogadjaji.publishEvent(new MejlPoruka(s.getEmail().trim(), NASLOV, telo(kod)));
    }

    static String telo(String kod) {
        return """
                Zdravo,

                tvoj kod za prijavu u GFS je: %s

                Kod važi 10 minuta i može se iskoristiti samo jednom.
                Ako nisi tražio kod, zanemari ovu poruku.
                """.formatted(kod);
    }

    /** Menja donju granicu trajanja (testovi). */
    void minimalnoTrajanje(Duration d) {
        minimalnoTrajanjeNs = d.toNanos();
    }

    /**
     * Spava do donje granice trajanja, mereno monotonim satom od {@code pocetak} ({@code System.nanoTime}). Sme samo kad
     * nit ne drži vezu iz pula: {@code KodServis} nema {@code @Transactional}, a open-in-view je isključen za
     * {@code /public/nalog/**} ({@code OtvorenEntityManagerConfig}); inače bi navala zahteva zauzela ceo pul.
     */
    void sacekajDoGranice(long pocetak) {
        long ostalo = minimalnoTrajanjeNs - (System.nanoTime() - pocetak);
        if (ostalo > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(ostalo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Ključ brojača {@code KOD_SLANJE_STUDENT} za indeks bez studenta: {@code i:} + HMAC-SHA256 normalizovanog indeksa
     * ključem {@code POKUSAJI_HMAC} iz baze (goli SHA-256 bi se lako obrnuo nad malim prostorom indeksa; poseban ključ, a ne
     * {@code MASKA_HMAC}, da se dve namene nikad ne poklope).
     */
    String kljucIndeksa(String normalizovanIndeks) {
        return kljucIndeksaIzHmac(hmacIndeksa(normalizovanIndeks));
    }

    static String kljucIndeksaIzHmac(String hmac) {
        return "i:" + hmac;
    }

    private String hmacIndeksa(String n) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(kljucevi.kljuc(KljuceviAplikacije.POKUSAJI_HMAC), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(n.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 nije dostupan", e);
        }
    }

    /** Indeks za log: prvih 8 hex SHA-256 (spec 11). */
    static String trag(String normalizovanIndeks) {
        return TokenHash.od(normalizovanIndeks).substring(0, 8);
    }

    private static String normalizuj(String indeks) {
        String n = IndeksUtil.normalizuj(indeks);
        return n == null ? "" : n;
    }

    private static boolean nemaMejl(Student s) {
        return s.getEmail() == null || s.getEmail().isBlank();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
