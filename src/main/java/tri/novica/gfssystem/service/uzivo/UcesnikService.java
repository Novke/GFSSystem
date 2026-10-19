package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.uzivo.JavnoIzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.UcesnikInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Javni ulaz u izvođenje uživo: naziv po kodu, prijava imenom ili kao prijavljen student (kolačić sa nasumičnim tokenom;
 * u bazi samo njegov SHA-256) i provera kolačića ("ja", WebSocket rukovanje). Prijava zaključava red izvođenja, kao
 * komande i odgovori, pa dve istovremene prijave istog imena (ili istog studenta) ne dobijaju isto ime ni dva reda, a
 * granica od 300 učesnika važi tačno.
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class UcesnikService {

    public static final String NEPOZNAT_KOD = "Izvođenje sa ovim kodom ne postoji ili je završeno.";
    public static final String POPUNJENO = "Izvođenje je popunjeno.";
    public static final String POTREBNA_PRIJAVA = "Potrebna je prijava studenta.";
    /** Spec 2.12: najviše 300 (neizbačenih) učesnika po izvođenju. */
    static final int MAX_UCESNIKA = 300;
    private static final Pattern KOD = Pattern.compile("^\\d{6}$");

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final UcesnikRepository ucesnikRepository;
    private final StudentRepository studentRepository;
    private final ImenaUcesnika imenaUcesnika;
    private final TokenGenerator tokenGenerator;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /**
     * Prijavljen učesnik i token za kolačić (token se ne čuva i ne loguje); {@code student}: učesnik je prijavljen
     * student (ime iz baze).
     */
    public record Prijavljen(UcesnikInfo info, String token, boolean student) {

        /** Učesnik koji je ukucao ime. */
        public Prijavljen(UcesnikInfo info, String token) {
            this(info, token, false);
        }
    }

    /** {@link #info(String, boolean)} za zahtev bez prijavljenog studenta. */
    @Transactional(readOnly = true)
    public JavnoIzvodjenjeInfo info(String kod) {
        return info(kod, false);
    }

    /**
     * Naziv prezentacije aktivnog izvođenja, da li je prijava studenta obavezna (bodovan run) i da li zahtev nosi
     * prijavljenog studenta ({@code imaIdentitet}); nepoznat ili završen kod -> 404.
     */
    @Transactional(readOnly = true)
    public JavnoIzvodjenjeInfo info(String kod, boolean imaIdentitet) {
        Izvodjenje iz = aktivno(kod);
        return new JavnoIzvodjenjeInfo(iz.getPrezentacija().getNaziv(), iz.isBoduje(), imaIdentitet);
    }

    /** Učesnik kome pripada token, ako je to učesnik ovog (aktivnog) izvođenja i nije izbačen. */
    @Transactional(readOnly = true)
    public Optional<UcesnikInfo> ja(String kod, String token) {
        return vazeci(token)
                .filter(u -> kod != null && kod.equals(u.getIzvodjenje().getAktivanKod()))
                .map(UcesnikService::info);
    }

    /** {@link #prijavi(String, String, Optional)} bez prijavljenog studenta. */
    public Prijavljen prijavi(String kod, String ime) {
        return prijavi(kod, ime, Optional.empty());
    }

    /**
     * Novi učesnik pod jedinstvenim imenom ({@code "ana 2"} kad "Ana" već postoji); 404 za nepoznat ili završen kod,
     * 400 za neispravno ime, 409 kad izvođenje već ima 300 učesnika. Sa {@code studentId} (prijavljen student koji
     * postoji) ukucano ime se ignoriše: ime je {@link ImenaUcesnika#imeStudenta} (nikad 400), a učesnik nosi studenta;
     * ako izvođenje već ima neizbačenog učesnika tog studenta, isti red dobija nov token (stari prestaje da važi), bez
     * novog reda i bez granice od 300. Bez studenta bodovan run je 403, pre provere imena. Ne menja verziju stanja
     * (verzija raste po komandi), ali objavljuje promenu: broj i imena učesnika.
     */
    public Prijavljen prijavi(String kod, String ime, Optional<Long> studentId) {
        // samo id pre zaključavanja: entitet učitan ranije ne bi video završetak do kog je došlo dok se čekalo
        Long id = (ispravanKod(kod) ? izvodjenjeRepository.findIdByAktivanKodAndStatus(kod, StatusIzvodjenja.AKTIVNO)
                : Optional.<Long>empty()).orElseThrow(UcesnikService::nepoznatKod);
        // pod zaključavanjem iznova: izvođenje je možda završeno dok se čekalo
        Izvodjenje iz = izvodjenjeRepository.findByIdForUpdate(id)
                .filter(i -> i.getStatus() == StatusIzvodjenja.AKTIVNO)
                .orElseThrow(UcesnikService::nepoznatKod);
        Student student = studentId.flatMap(studentRepository::findById).orElse(null);
        if (student == null) {
            if (iz.isBoduje()) {
                throw new SystemException(POTREBNA_PRIJAVA, HttpStatus.FORBIDDEN);
            }
            return novi(iz, imenaUcesnika.validiraj(ime), null);
        }
        Optional<Ucesnik> postojeci =
                ucesnikRepository.findFirstByIzvodjenjeIdAndStudentIdAndIzbacenFalse(iz.getId(), student.getId());
        if (postojeci.isPresent()) {
            // isti student sa drugog telefona (ili posle brisanja kolačića): isti red, nov token
            Ucesnik u = postojeci.get();
            String token = tokenGenerator.novi();
            u.setTokenHash(TokenHash.od(token));
            publisher.publishEvent(new UcesnikPrijavljen(iz.getId()));
            return new Prijavljen(info(u), token, true);
        }
        return novi(iz, ImenaUcesnika.imeStudenta(student), student);
    }

    /** Nov učesnik pod jedinstvenim imenom od {@code osnova}; pozivalac drži zaključan red izvođenja. */
    private Prijavljen novi(Izvodjenje iz, String osnova, Student student) {
        if (ucesnikRepository.countByIzvodjenjeIdAndIzbacenFalse(iz.getId()) >= MAX_UCESNIKA) {
            throw new SystemException(POPUNJENO, HttpStatus.CONFLICT);
        }
        String token = tokenGenerator.novi();
        Ucesnik u = new Ucesnik();
        u.setIzvodjenje(iz);
        u.setIme(imenaUcesnika.jedinstvenoIme(iz.getId(), osnova, null));
        u.setTokenHash(TokenHash.od(token));
        u.setIzbacen(false);
        u.setKreirano(LocalDateTime.now(clock));
        u.setStudent(student);
        u = ucesnikRepository.save(u);
        publisher.publishEvent(new UcesnikPrijavljen(iz.getId()));
        return new Prijavljen(info(u), token, student != null);
    }

    /** Učesnik za WebSocket rukovanje: token važi, učesnik nije izbačen, izvođenje je AKTIVNO. */
    @Transactional(readOnly = true)
    public Optional<Ucesnik> poTokenu(String token) {
        return vazeci(token);
    }

    private Optional<Ucesnik> vazeci(String token) {
        // neispravan oblik (prazan, predug, drugi znaci) se i ne hešira ni ne traži
        if (token == null || !TokenGenerator.FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        return ucesnikRepository.findByTokenHash(TokenHash.od(token))
                .filter(u -> !u.isIzbacen())
                .filter(u -> u.getIzvodjenje().getStatus() == StatusIzvodjenja.AKTIVNO);
    }

    private Izvodjenje aktivno(String kod) {
        if (!ispravanKod(kod)) {
            throw nepoznatKod();
        }
        return izvodjenjeRepository.findByAktivanKod(kod)
                .filter(i -> i.getStatus() == StatusIzvodjenja.AKTIVNO)
                .orElseThrow(UcesnikService::nepoznatKod);
    }

    /** Kod koji nije 6 cifara se i ne traži u bazi. */
    private static boolean ispravanKod(String kod) {
        return kod != null && KOD.matcher(kod).matches();
    }

    private static SystemException nepoznatKod() {
        return new SystemException(NEPOZNAT_KOD, HttpStatus.NOT_FOUND);
    }

    private static UcesnikInfo info(Ucesnik u) {
        return new UcesnikInfo(u.getId(), u.getIme(), u.getIzvodjenje().getId());
    }
}
