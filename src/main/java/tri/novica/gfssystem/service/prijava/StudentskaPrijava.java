package tri.novica.gfssystem.service.prijava;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.prijava.StudentJaInfo;
import tri.novica.gfssystem.dto.prijava.UredjajInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.SpoljniIdentitetRepository;
import tri.novica.gfssystem.repository.prijava.StudentskiUredjajRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Zapamćeni uređaji studenta (spec 2.2, 5.1): posle prijave kodom (ili iz Moodle-a) uređaj dobija kolačić
 * {@code gfs_student} (KOD 365 dana, LTI {@code gfs.nalog.lti-trajanje}, podrazumevano 12 h), a u {@code studentski_uredjaji} ostaje samo SHA-256 tokena. Korišćenje pomera
 * {@code istice} (najviše jednom na 10 minuta). Nalog nema lozinku ni svoje stanje: uređaji vise direktno o studentu.
 * Log ima samo id-jeve, nikad token.
 */
@Service
@Slf4j
public class StudentskaPrijava {

    /**
     * Trajanje KOD uređaja (studentov telefon) od poslednjeg korišćenja; isto je Max-Age kolačića {@code gfs_student}.
     * LTI uređaj ima kraće trajanje ({@link #trajanje}).
     */
    public static final Duration TRAJANJE = Duration.ofDays(365);
    static final Duration PRODUZENJE_NAJCESCE = Duration.ofMinutes(10);

    private final StudentskiUredjajRepository uredjaji;
    private final StudentRepository studenti;
    private final SpoljniIdentitetRepository identiteti;
    private final TokenGenerator tokenGenerator;
    private final MaskaMejla maska;
    private final Clock clock;
    private final Duration ltiTrajanje;

    /**
     * {@code gfs.nalog.lti-trajanje} (ISO-8601, podrazumevano {@code PT12H}): uređaj iz Moodle-a često stoji na zajedničkom
     * računaru u učionici, pa se posle celog dana rada (klizno od poslednjeg korišćenja) ili jedne noći sam odjavljuje.
     */
    public StudentskaPrijava(StudentskiUredjajRepository uredjaji, StudentRepository studenti,
                             SpoljniIdentitetRepository identiteti, TokenGenerator tokenGenerator, MaskaMejla maska,
                             Clock clock, @Value("${gfs.nalog.lti-trajanje:PT12H}") Duration ltiTrajanje) {
        if (ltiTrajanje.isZero() || ltiTrajanje.isNegative() || ltiTrajanje.compareTo(TRAJANJE) > 0) {
            throw new IllegalStateException("gfs.nalog.lti-trajanje mora biti između 0 i 365 dana");
        }
        this.uredjaji = uredjaji;
        this.studenti = studenti;
        this.identiteti = identiteti;
        this.tokenGenerator = tokenGenerator;
        this.maska = maska;
        this.clock = clock;
        this.ltiTrajanje = ltiTrajanje;
    }

    /** Trajanje uređaja od poslednjeg korišćenja za dati način prijave: i {@code istice} u bazi i Max-Age kolačića. */
    public Duration trajanje(NacinPrijave nacin) {
        return nacin == NacinPrijave.LTI ? ltiTrajanje : TRAJANJE;
    }

    /** Nov uređaj studenta i kolačić {@code gfs_student} ({@code SameSite=Lax}, {@link #trajanje}, {@code Secure} na HTTPS-u). */
    @Transactional
    public void prijavi(Long studentId, NacinPrijave nacin, HttpServletRequest req, HttpServletResponse res) {
        Student s = studenti.findById(studentId).orElseThrow(() -> new SystemException("Ne postoji.", 404));
        String token = tokenGenerator.novi();
        LocalDateTime sada = LocalDateTime.now(clock);
        StudentskiUredjaj u = new StudentskiUredjaj();
        u.setStudent(s);
        u.setTokenHes(TokenHash.od(token));
        u.setOpis(OpisUredjaja.iz(req.getHeader(HttpHeaders.USER_AGENT)));
        u.setNacin(Objects.requireNonNull(nacin));
        u.setPrviUlaz(sada);
        u.setPoslednjiUlaz(sada);
        u.setIstice(sada.plus(trajanje(nacin)));
        uredjaji.save(u);
        Kolacici.postavi(req, res, Kolacici.STUDENT, token, trajanje(nacin), "Lax");
        log.info("Prijava studenta, student={}, uredjaj={}, nacin={}", s.getId(), u.getId(), nacin);
    }

    /**
     * Važeći uređaj za token iz kolačića (nije opozvan, {@code istice} je u budućnosti). Korišćenje pomera
     * {@code poslednjiUlaz} i {@code istice} najviše jednom na 10 minuta.
     */
    @Transactional
    public Optional<StudentskiUredjaj> potvrdi(String token) {
        if (token == null || !TokenGenerator.FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        LocalDateTime sada = LocalDateTime.now(clock);
        Optional<StudentskiUredjaj> nadjen = uredjaji.findByTokenHes(TokenHash.od(token))
                .filter(u -> u.getOpozvan() == null && u.getIstice().isAfter(sada));
        nadjen.ifPresent(u -> {
            if (!u.getPoslednjiUlaz().plus(PRODUZENJE_NAJCESCE).isAfter(sada)) {
                u.setPoslednjiUlaz(sada);
                u.setIstice(sada.plus(trajanje(u.getNacin())));
                uredjaji.save(u);
            }
        });
        return nadjen;
    }

    /** Odjava uređaja po tokenu; nepoznat token se ćutke preskače. */
    @Transactional
    public void odjavi(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        uredjaji.findByTokenHes(TokenHash.od(token)).ifPresent(this::opozovi);
    }

    /** Odjava uređaja po id-ju (uređaj prijavljenog zahteva, ne zavisi od toga koji je kolačić stigao prvi). */
    @Transactional
    public void odjaviUredjaj(Long uredjajId) {
        if (uredjajId != null) {
            uredjaji.findById(uredjajId).ifPresent(this::opozovi);
        }
    }

    /**
     * Opoziva sve uređaje studenta; vraća koliko ih je do tada važilo. Bulk update ne osvežava već učitane entitete, pa
     * se posle njega ovde ništa ne čita iz memorije.
     */
    @Transactional
    public int odjaviSve(Long studentId) {
        LocalDateTime sada = LocalDateTime.now(clock);
        int vazecih = uredjaji.findAllByStudentIdAndOpozvanIsNullAndIsticeAfter(studentId, sada).size();
        uredjaji.opozoviSve(studentId, sada);
        log.info("Odjava svih uređaja studenta, student={}, odjavljeno={}", studentId, vazecih);
        return vazecih;
    }

    /** Važeći uređaji studenta, najskorije korišćen prvi; {@code trenutni} je uređaj sa id-jem {@code trenutniId}. */
    @Transactional(readOnly = true)
    public List<UredjajInfo> uredjaji(Long studentId, Long trenutniId) {
        return uredjaji.findAllByStudentIdAndOpozvanIsNullAndIsticeAfter(studentId, LocalDateTime.now(clock)).stream()
                .sorted(Comparator.comparing(StudentskiUredjaj::getPoslednjiUlaz).reversed()
                        .thenComparing(StudentskiUredjaj::getId, Comparator.reverseOrder()))
                .map(u -> new UredjajInfo(u.getId(), u.getOpis(), u.getNacin(), u.getPrviUlaz(), u.getPoslednjiUlaz(),
                        u.getId().equals(trenutniId)))
                .toList();
    }

    /** Nalog prijavljenog studenta za {@code GET /public/nalog/ja}: mejl samo kao maska. */
    @Transactional(readOnly = true)
    public StudentJaInfo ja(Long studentId, Long trenutniUredjajId) {
        Student s = studenti.findById(studentId).orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
        return new StudentJaInfo(s.getIndeks(), s.getIme(), s.getPrezime(), maska.maska(s.getEmail()),
                uredjaji(studentId, trenutniUredjajId), !identiteti.findAllByStudentId(studentId).isEmpty(), s.getGodina(),
                s.getGrupa() == null ? null : s.getGrupa().getNaziv(),
                s.getGrupa() == null ? null : s.getGrupa().getGodinaUpisa());
    }

    private void opozovi(StudentskiUredjaj u) {
        if (u.getOpozvan() == null) {
            u.setOpozvan(LocalDateTime.now(clock));
            uredjaji.save(u);
            log.info("Uređaj studenta opozvan, uredjaj={}", u.getId());
        }
    }
}
