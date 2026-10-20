package tri.novica.gfssystem.service.prijava;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    private final StudentRepository studenti;
    private final KodZaPrijavuRepository kodovi;
    private final PokusajiServis pokusaji;
    private final MejlServis mejl;
    private final ApplicationEventPublisher dogadjaji;
    private final MaskaMejla maska;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;
    private final TransactionTemplate zakljucano;
    private final SecureRandom random = new SecureRandom();

    public KodServis(StudentRepository studenti, KodZaPrijavuRepository kodovi, PokusajiServis pokusaji, MejlServis mejl,
                     ApplicationEventPublisher dogadjaji, MaskaMejla maska, TokenGenerator tokenGenerator,
                     PlatformTransactionManager transakcije, Clock clock) {
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

    /**
     * Šalje kod na mejl studenta sa indeksom (najnoviji upis); uvek isti odgovor (spec 5.1). 503 kad slanje nije podešeno,
     * 429 preko 10/h po IP-u (broji se svaki zahtev, i za nepostojeći indeks) ili 3/h po indeksu.
     */
    public KodOdgovor posalji(String indeks, String ip) {
        if (!mejl.dostupan()) {
            throw new SystemException(BEZ_SMTP, 503);
        }
        String n = normalizuj(indeks);
        // uvek se računa (i za postojećeg): isti posao i vreme, a prvi poziv pravi ključ van zaključane transakcije
        String lazna = maska.lazna(n);
        if (pokusaji.dostignut(VrstaPokusaja.KOD_SLANJE_IP, ip, LIMIT_SLANJA_IP, SAT)) {
            log.info("Zahtev za kod odbijen (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE, 429);
        }
        pokusaji.zabelezi(VrstaPokusaja.KOD_SLANJE_IP, ip);

        Slanje s = zakljucano.execute(tx -> posaljiZakljucano(n, lazna));
        if (s.limit()) {
            log.info("Zahtev za kod odbijen (limit po indeksu), indeks={}, student={}, ip={}", trag(n), s.studentId(), ip);
            if (s.studentId() != null) {
                previseKodova(s.studentId(), ip);
            }
            throw new SystemException(PREVISE, 429);
        }
        return new KodOdgovor(PORUKA, s.maska());
    }

    private Slanje posaljiZakljucano(String n, String lazna) {
        Optional<Student> student = zakljucajStudenta(n);
        String kljuc = student.map(s -> String.valueOf(s.getId())).orElse(kljucIndeksa(n));
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
     * Šav za obaveštenje nastavniku (spec 6, {@code PREVISE_KODOVA}): student je dostigao limit od 3 koda na sat. Task B8
     * ovde poziva {@code ObavestenjeServis.previseKodova(studentId, ip)}; do tada ništa. Poziva se van zaključane
     * transakcije, posle njenog kraja, pa sopstveni upis obaveštenja ne zavisi od 429 koji sledi.
     */
    void previseKodova(Long studentId, String ip) {
        // B8: obavestenja.previseKodova(studentId, ip)
    }

    /**
     * Proverava kod za indeks; vraća {@code studentId} i troši kod. 429 preko 20 pogrešnih na sat po IP-u; inače svaki
     * neuspeh (nema aktivnog koda, pogrešan, istekao, nepostojeći indeks) daje isti 401 i broji se po IP-u.
     */
    public Long proveri(String indeks, String kod, String ip) {
        if (pokusaji.dostignut(VrstaPokusaja.KOD_GRESKA_IP, ip, LIMIT_GRESAKA_IP, SAT)) {
            log.info("Provera koda odbijena (limit po IP-u), ip={}", ip);
            throw new SystemException(PREVISE, 429);
        }
        String n = normalizuj(indeks);
        Long studentId = zakljucano.execute(tx -> proveriZakljucano(n, kod));
        if (studentId == null) {
            pokusaji.zabelezi(VrstaPokusaja.KOD_GRESKA_IP, ip);
            log.info("Neispravan kod, indeks={}, ip={}", trag(n), ip);
            throw new SystemException(NEISPRAVAN, 401);
        }
        log.info("Kod iskorišćen, student={}, ip={}", studentId, ip);
        return studentId;
    }

    private Long proveriZakljucano(String n, String kod) {
        Optional<Student> student = zakljucajStudenta(n);
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
     * Student sa najnovijim upisom za normalizovan indeks, sa zaključanim redom. Prazan indeks (npr. samo neprekidivi
     * razmaci, koje {@code @NotBlank} pušta) nikad ne pogađa studenta, ni onog čiji je indeks u bazi prazan.
     */
    private Optional<Student> zakljucajStudenta(String n) {
        if (n.isEmpty()) {
            return Optional.empty();
        }
        return studenti.findNajnovijiPoIndeksu(n).flatMap(s -> studenti.findByIdForUpdate(s.getId()));
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

    /** Ključ brojača {@code KOD_SLANJE_STUDENT} za indeks bez studenta: heš, da limit važi isto kao za postojećeg. */
    static String kljucIndeksa(String normalizovanIndeks) {
        return "i:" + TokenHash.od(normalizovanIndeks);
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
