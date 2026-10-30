package tri.novica.gfssystem.service.prijava;

import lombok.extern.slf4j.Slf4j;
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
import tri.novica.gfssystem.entity.prijava.NacinKoda;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.KodZaPrijavuRepository;
import tri.novica.gfssystem.service.mejl.MejlPoruke;
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
import java.util.concurrent.atomic.AtomicInteger;
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
 * nekoliko upisa između postojećeg i nepostojećeg naloga ne izlazi u vreme odgovora ({@code PREVISE_KODOVA} se upisuje
 * u pozadini, posle odgovora: {@link DostignutLimitKodova}). Izuzetak
 * su 503 (slanje nije podešeno) i 429 po IP-u: oni ne zavise od indeksa, pa vraćaju odmah (inače bi napadač preko
 * limita i dalje držao niti zahteva).
 * <p>
 * <b>Student je par (kanonski indeks, godina upisa)</b> ({@link IndeksUtil#kanonski}): broj indeksa se ponavlja kroz
 * generacije, pa se student traži tačno po paru, nikad "najnovija generacija". Zahtev iz forme u tri dela nosi godinu, a
 * indeks mora biti u obliku javne forme ({@link IndeksUtil#jeJavniOblik}); sve ostalo je kao nepostojeći indeks. Brojači,
 * brave i lažna maska idu po identitetu {@code <kanonski>/<godina>}. PRIVREMENO (keširan stari SPA, jedno izdanje): zahtev
 * bez godine sa sufiksom {@code /YYYY} uzima taj sufiks kao godinu, a bez sufiksa traži najnoviju generaciju kao ranije.
 * <p>
 * <b>Kod za diktiranje</b> ({@link #kodZaDiktiranje}): isti kod, ista tabela i ista pravila, samo se prikazuje nastavniku
 * umesto da ide mejlom ({@link NacinKoda#NASTAVNIK}). Nov kod poništava samo stare kodove iste vrste, provera prihvata bilo
 * koji aktivan kod studenta, a uspešna prijava poništava sve ostale.
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
    static final String NASLOV = MejlPoruke.NASLOV_KODA;

    static final Duration TRAJANJE_KODA = Duration.ofMinutes(10);
    static final int MAX_POGRESNIH = 5;
    static final Duration SAT = Duration.ofHours(1);
    static final int LIMIT_SLANJA_IP = 50;
    static final int LIMIT_SLANJA_STUDENT = 3;
    static final int LIMIT_GRESAKA_IP = 20;
    static final int LIMIT_SA_KARTICE = 20;
    /** Kodova za diktiranje na sat, ukupno (nastavnik na času; mejl se ne šalje, pa je limit širi). */
    static final int LIMIT_DIKTIRANIH = 60;
    /** Ključ ukupnog brojača za kodove sa kartice studenta. */
    static final String UKUPNO = "ukupno";
    private static final Pattern FORMAT_KODA = Pattern.compile("^[0-9]{6}$");
    /** Normalizovan indeks koji se uopšte traži u bazi (ASCII; onboarding forma dozvoljava podskup ovoga). */
    static final Pattern BELA_LISTA_INDEKSA = Pattern.compile("^[A-Z0-9/.-]{1,30}$");
    /**
     * Podrazumevano čekanje MySQL brave indeksa (s); posle toga 429. Kratko, jer čekanje drži vezu iz pula (podrazumevano
     * 10): navala zahteva za isti indeks ne sme da zaustavi ostale pozive. Menja se samo u testovima ({@link #cekanjeBrave}). */
    static final int CEKANJE_BRAVE_S = 2;

    private final StudentRepository studenti;
    private final KodZaPrijavuRepository kodovi;
    private final PokusajiServis pokusaji;
    private final MejlServis mejl;
    private final MejlPoruke poruke;
    private final ApplicationEventPublisher dogadjaji;
    private final MaskaMejla maska;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;
    private final KljuceviAplikacije kljucevi;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate zakljucano;
    /** Prefiks imena brava iz {@code gfs.prijava.brava-prefiks}; prazno = šema baze ({@code SELECT DATABASE()}). */
    private final String bravaPrefiks;
    private volatile String sema;
    private final SecureRandom random = new SecureRandom();
    /** Donja granica trajanja odgovora (ns); u testovima 0, osim gde se proverava. */
    private volatile long minimalnoTrajanjeNs;
    private final AtomicInteger spava = new AtomicInteger();
    /** Koliko se najduže čeka MySQL brava indeksa (s); u testovima dodatno spušteno. */
    private volatile int cekanjeBraveS = CEKANJE_BRAVE_S;

    public KodServis(StudentRepository studenti, KodZaPrijavuRepository kodovi, PokusajiServis pokusaji, MejlServis mejl,
                     MejlPoruke poruke, ApplicationEventPublisher dogadjaji, MaskaMejla maska, TokenGenerator tokenGenerator,
                     KljuceviAplikacije kljucevi, JdbcTemplate jdbc, PlatformTransactionManager transakcije, Clock clock,
                     @Value("${gfs.nalog.minimalno-trajanje-ms:200}") long minimalnoTrajanjeMs,
                     @Value("${gfs.prijava.brava-prefiks:}") String bravaPrefiks) {
        this.bravaPrefiks = bravaPrefiks == null ? "" : bravaPrefiks.trim();
        this.kljucevi = kljucevi;
        this.jdbc = jdbc;
        this.minimalnoTrajanjeNs = TimeUnit.MILLISECONDS.toNanos(minimalnoTrajanjeMs);
        this.studenti = studenti;
        this.kodovi = kodovi;
        this.pokusaji = pokusaji;
        this.mejl = mejl;
        this.poruke = poruke;
        this.dogadjaji = dogadjaji;
        this.maska = maska;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
        this.zakljucano = new TransactionTemplate(transakcije);
        this.zakljucano.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Ishod zaključanog dela slanja: maska za odgovor, ili limit po indeksu ({@code studentId} može biti null). */
    private record Slanje(String maska, boolean limit, Long studentId) {}

    /**
     * Indeks zahteva: kanonski {@code n}, godina upisa (null samo za stari zahtev bez godine), HMAC identiteta (ključ brojača
     * i ime brave) i da li sme u bazu.
     */
    record Indeks(String n, Integer godina, String hmac, boolean trazi) {
        /** {@code <kanonski>/<godina>}, ili samo indeks bez godine: ključ brojača, lažne maske i traga u logu. */
        String identitet() {
            return identitet(n, godina);
        }

        static String identitet(String n, Integer godina) {
            return godina == null ? n : n + "/" + godina;
        }

        /** Indeks za log (prvih 8 hex SHA-256 identiteta). */
        String trag() {
            return KodServis.trag(identitet());
        }
    }

    /**
     * Indeks iz zahteva. {@code godina} != null: forma u tri dela, indeks mora biti u obliku javne forme. {@code godina} ==
     * null: stari SPA (PRIVREMENO), sufiks {@code /YYYY} je godina. Bela lista se proverava na normalizovanom unosu.
     */
    Indeks indeks(String indeks, Integer godina) {
        String sirov = indeks;
        Integer g = godina;
        if (g == null) {
            IndeksUtil.Razdvojen r = IndeksUtil.razdvoji(indeks);
            sirov = r.indeks();
            g = r.godina();
        }
        String normalizovan = normalizuj(sirov);
        boolean trazi = BELA_LISTA_INDEKSA.matcher(normalizovan).matches();
        String n = trazi ? IndeksUtil.kanonski(normalizovan, g) : normalizovan;
        if (godina != null) {
            trazi = trazi && IndeksUtil.jeJavniOblik(n);
        }
        return new Indeks(n, g, hmacIndeksa(Indeks.identitet(n, g)), trazi);
    }

    /** Stari zahtev sa jednim poljem (testovi i keširan SPA): isto što {@link #indeks(String, Integer)} bez godine. */
    Indeks indeks(String indeks) {
        return indeks(indeks, null);
    }

    /** Stari zahtev bez godine (PRIVREMENO, vidi opis klase). */
    public KodOdgovor posalji(String indeks, String ip) {
        return posalji(indeks, null, ip);
    }

    /**
     * Šalje kod na mejl studenta sa parom (indeks, godina upisa); uvek isti odgovor (spec 5.1). 503 kad slanje nije
     * podešeno, 429 preko 50/h po IP-u (broji se svaki zahtev, i za nepostojeći indeks) ili 3/h po indeksu.
     */
    public KodOdgovor posalji(String indeks, Integer godina, String ip) {
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
            Indeks i = indeks(indeks, godina);
            String lazna = maska.lazna(i.identitet());

            Slanje s = zakljucano.execute(tx -> posaljiZakljucano(i, lazna));
            if (s.limit()) {
                log.info("Zahtev za kod odbijen (limit po indeksu), indeks={}, student={}, ip={}",
                        i.trag(), s.studentId(), ip);
                if (s.studentId() != null) {
                    // posao u pozadini (ObavestenjeServis, posle ovog zahteva): 429 za postojećeg studenta ne radi
                    // ništa više u bazi nego za nepostojeći indeks
                    dogadjaji.publishEvent(new DostignutLimitKodova(s.studentId(), ip));
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
        Optional<Student> student = zakljucajStudenta(i);
        String kljuc = student.map(s -> String.valueOf(s.getId())).orElse(kljucIndeksaIzHmac(i.hmac()));
        Long studentId = student.map(Student::getId).orElse(null);
        if (pokusaji.broj(VrstaPokusaja.KOD_SLANJE_STUDENT, kljuc, SAT) >= LIMIT_SLANJA_STUDENT) {
            return new Slanje(null, true, studentId);
        }
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.KOD_SLANJE_STUDENT, kljuc);
        if (student.isEmpty() || nemaMejl(student.get())) {
            log.info("Zahtev za kod bez naloga ili mejla, indeks={}, student={}", i.trag(), studentId);
            return new Slanje(lazna, false, studentId);
        }
        Student s = student.get();
        novKodNaMejl(s, false);
        log.info("Kod poslat, student={}", s.getId());
        return new Slanje(maska.maska(s.getEmail()), false, s.getId());
    }

    /** Stari zahtev bez godine (PRIVREMENO, vidi opis klase). */
    public Long proveri(String indeks, String kod, String ip) {
        return proveri(indeks, null, kod, ip);
    }

    /**
     * Proverava kod za par (indeks, godina upisa); vraća {@code studentId} i troši kod. 429 preko 20 pogrešnih na sat po
     * IP-u; inače svaki neuspeh (nema aktivnog koda, pogrešan, istekao, nepostojeći indeks) daje isti 401 i broji se po IP-u.
     */
    public Long proveri(String indeks, Integer godina, String kod, String ip) {
        long pocetak = System.nanoTime();
        // 429 po IP-u ne zavisi od indeksa ni koda, pa ide bez donje granice (kao u posalji)
        if (pokusaji.dostignut(VrstaPokusaja.KOD_GRESKA_IP, ip, LIMIT_GRESAKA_IP, SAT)) {
            log.info("Provera koda odbijena (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE, 429);
        }
        try {
            Indeks i = indeks(indeks, godina);
            Long studentId = zakljucano.execute(tx -> proveriZakljucano(i, kod));
            if (studentId == null) {
                pokusaji.zabelezi(VrstaPokusaja.KOD_GRESKA_IP, ip);
                log.info("Neispravan kod, indeks={}, ip={}", i.trag(), ip);
                throw new SystemException(NEISPRAVAN, 401);
            }
            log.info("Kod iskorišćen, student={}, ip={}", studentId, ip);
            return studentId;
        } finally {
            sacekajDoGranice(pocetak);
        }
    }

    /**
     * Kod se poredi sa svakim aktivnim kodom studenta (najviše po jedan sa mejla i od nastavnika). Pogodak troši taj kod i
     * poništava ostale aktivne; promašaj povećava brojač pogrešnih na svakom aktivnom kodu (posle 5 kod se poništava).
     */
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
        boolean formatOk = kod != null && FORMAT_KODA.matcher(kod).matches();
        KodZaPrijavu pogodjen = null;
        for (KodZaPrijavu k : aktivni) {
            // poredi se svaki (bez ranog izlaska): isto trajanje bez obzira na to koji je pogođen
            boolean tacan = formatOk && MessageDigest.isEqual(ascii(TokenHash.od(k.getSo() + kod)), ascii(k.getKodHes()));
            if (tacan && pogodjen == null) {
                pogodjen = k;
            }
        }
        if (pogodjen != null) {
            for (KodZaPrijavu k : aktivni) {
                if (k == pogodjen) {
                    k.setIskoriscen(sada);
                } else {
                    k.setPonisten(sada);
                }
                kodovi.save(k);
            }
            return student.get().getId();
        }
        for (KodZaPrijavu k : aktivni) {
            k.setPogresni(k.getPogresni() + 1);
            if (k.getPogresni() >= MAX_POGRESNIH) {
                k.setPonisten(sada);
                log.info("Kod poništen posle {} pogrešnih unosa, student={}, nacin={}", MAX_POGRESNIH,
                        student.get().getId(), k.getNacin());
            }
            kodovi.save(k);
        }
        return null;
    }

    /**
     * Student za indeks zahteva, sa zaključanim redom. Samo indeks sa bele liste (prazan, npr. od samih neprekidivih
     * razmaka koje {@code @NotBlank} pušta, nije na njoj), i samo tačno isti kanonski oblik: upit u bazi poredi pod
     * {@code ai_ci} (akcenti, širina), pa se kandidati ovde porede binarno.
     */
    private Optional<Student> zakljucajStudenta(Indeks i) {
        return nadjiStudenta(i).flatMap(s -> studenti.findByIdForUpdate(s.getId()));
    }

    /**
     * Isto bez zaključavanja reda: {@link ObavestenjeServis#neStizeKod}. Sa godinom tačno par (kanonski indeks, godina)
     * ({@link StudentRepository#findAllPoIndeksuIGodini}; dva reda sa istim parom ne bi smela da postoje, uzima se noviji
     * id). Bez godine (PRIVREMENO, stari SPA) najnovija generacija sa tim indeksom, kao pre.
     */
    Optional<Student> nadjiStudenta(Indeks i) {
        if (!i.trazi()) {
            return Optional.empty();
        }
        if (i.godina() != null) {
            return studenti.findAllPoIndeksuIGodini(i.n(), i.godina()).stream().findFirst();
        }
        return studenti.findAllPoNormalizovanomIndeksu(i.n()).stream()
                .filter(s -> i.n().equals(IndeksUtil.kanonski(s.getIndeks(), s.getGodina())))
                .findFirst();
    }

    /**
     * Prva naredba zaključane transakcije: MySQL brava za indeks na vezi te transakcije ({@code JdbcTemplate} u JPA
     * transakciji koristi istu vezu). Otpušta se u {@code afterCompletion} (posle commit-a ili rollback-a, veza je još
     * vezana za nit), da sledeći zahtev za isti indeks vidi upisan brojač. Ne dobije li je za {@link #CEKANJE_BRAVE_S} s: 429.
     */
    private void zakljucajIndeks(Indeks i) {
        if (!uzmiBravu("gfs-kod:", i)) {
            throw new SystemException(PREVISE, 429);
        }
    }

    /**
     * Uzima MySQL bravu {@code prefiks + hmac indeksa} na vezi tekuće transakcije (mora postojati) i otpušta je u
     * {@code afterCompletion}. {@code false} kad je ne dobije za {@link #CEKANJE_BRAVE_S} s. Svaka funkcija ima svoj
     * prefiks (slanje koda, "Ne stiže mi kod"), a redosled je uvek brava indeksa pa red studenta.
     */
    boolean uzmiBravu(String prefiks, Indeks i) {
        return uzmiBravu(prefiks + i.hmac().substring(0, 32), cekanjeBraveS, i.trag());
    }

    /**
     * Ime brave na serveru: logičko ime sa prefiksom šeme ({@link ImenaBrava}), jer su korisničke brave MySQL-a
     * zajedničke za sve šeme servera (produkcija i staging dele {@code shared-mysql}). Šema se čita jednom.
     */
    String imeBrave(String logickoIme) {
        String s = sema;
        if (s == null) {
            s = !bravaPrefiks.isEmpty() ? bravaPrefiks : jdbc.queryForObject("SELECT DATABASE()", String.class);
            sema = s == null ? "" : s;
            s = sema;
        }
        return ImenaBrava.ime(s, logickoIme);
    }

    /**
     * Isto za bravu zadatog (logičkog) imena i čekanja (npr. ukupni brojač mejlova nastavniku); {@code oznaka} je samo za log.
     */
    public boolean uzmiBravu(String logickoIme, int cekanjeS, String oznaka) {
        String brava = imeBrave(logickoIme);
        Integer dobijena = jdbc.queryForObject("SELECT GET_LOCK(?, ?)", Integer.class, brava, cekanjeS);
        if (dobijena == null || dobijena != 1) {
            log.warn("Brava nije dobijena za {} s, oznaka={}", cekanjeS, oznaka);
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    Integer otpustena = jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, brava);
                    if (otpustena == null || otpustena != 1) {
                        log.error("Brava nije otpuštena (rezultat {}), oznaka={}", otpustena, oznaka);
                    }
                } catch (RuntimeException e) {
                    log.error("Brava nije otpuštena: {}", e.getClass().getSimpleName());
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
            novKodNaMejl(s, true);
        });
        log.info("Kod poslat sa kartice, student={}", studentId);
    }

    /** Kod koji nastavnik izdiktira: vrednost (jednom, nikad u bazi ni logu) i trenutak isteka. */
    public record DiktiranKod(String kod, LocalDateTime istice) {
        @Override
        public String toString() {
            return "DiktiranKod[kod=***, istice=" + istice + "]";
        }
    }

    /**
     * Kod za diktiranje sa kartice studenta (nastavnik, plan C): isti kod i ista pravila kao kod sa mejla, ali se ne šalje
     * nikud, nego se jednom vraća nastavniku. Sme i studentu bez mejla i kad SMTP nije podešen. Najviše
     * {@value #LIMIT_DIKTIRANIH} na sat ukupno (429); 404 kad student ne postoji. Poništava samo raniji kod za diktiranje
     * istog studenta, ne i kod sa mejla.
     */
    public DiktiranKod kodZaDiktiranje(Long studentId) {
        if (pokusaji.dostignut(VrstaPokusaja.KOD_DIKTIRAN, UKUPNO, LIMIT_DIKTIRANIH, SAT)) {
            log.info("Kod za diktiranje odbijen (limit ukupno), student={}", studentId);
            throw new SystemException(PREVISE, 429);
        }
        DiktiranKod d = zakljucano.execute(tx -> {
            Student s = studenti.findByIdForUpdate(studentId)
                    .orElseThrow(() -> new SystemException("Ne postoji.", 404));
            pokusaji.zabeleziUTransakciji(VrstaPokusaja.KOD_DIKTIRAN, UKUPNO);
            String kod = novKod(s, NacinKoda.NASTAVNIK);
            return new DiktiranKod(kod, LocalDateTime.now(clock).plus(TRAJANJE_KODA));
        });
        log.info("Kod za diktiranje izdat, student={}", studentId);
        return d;
    }

    /** Nov kod na mejl studenta (mejl se šalje posle commit-a). */
    private void novKodNaMejl(Student s, boolean odNastavnika) {
        String kod = novKod(s, NacinKoda.MEJL);
        dogadjaji.publishEvent(poruke.kod(s.getEmail().trim(), s.getIme(), kod, TRAJANJE_KODA.toMinutes(), odNastavnika));
    }

    /**
     * Pod zaključanim redom studenta: poništava aktivne kodove iste vrste ({@code nacin}), upisuje nov i vraća njegovu
     * vrednost (u bazi je samo heš).
     */
    private String novKod(Student s, NacinKoda nacin) {
        LocalDateTime sada = LocalDateTime.now(clock);
        for (KodZaPrijavu stari : kodovi.findAktivne(s.getId(), sada)) {
            if (stari.getNacin() == nacin) {
                stari.setPonisten(sada);
                kodovi.save(stari);
            }
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
        k.setNacin(nacin);
        kodovi.save(k);
        return kod;
    }

    /** Menja čekanje brave indeksa (s, testovi); {@link #CEKANJE_BRAVE_S} je podrazumevano. */
    void cekanjeBrave(int sekundi) {
        cekanjeBraveS = sekundi;
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
            spava.incrementAndGet();
            try {
                TimeUnit.NANOSECONDS.sleep(ostalo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                spava.decrementAndGet();
            }
        }
    }

    /** Koliko niti trenutno spava do donje granice (testovi: uzorkovanje tek kad su svi zahtevi završili posao u bazi). */
    int uSpavanju() {
        return spava.get();
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
