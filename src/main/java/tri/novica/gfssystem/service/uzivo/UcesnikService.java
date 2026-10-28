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
import tri.novica.gfssystem.repository.cas.CekiranjeRepository;
import tri.novica.gfssystem.service.cas.CekiranjeService;
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
    /** Prijavljen student koga je nastavnik izbacio iz ovog izvođenja ne ulazi ponovo (spec studentskog dela 4.7). */
    public static final String IZBACEN = "Izbačen si iz ovog izvođenja.";
    /** Bodovano izvođenje sa predavanja traži čekiranje na tom predavanju u poslednjih 5 h (spec studentskog dela 4.6). */
    public static final String PRVO_CEKIRANJE = "Prvo se čekiraj: skeniraj QR sa table.";
    /** Spec 2.12: najviše 300 (neizbačenih) učesnika po izvođenju. */
    static final int MAX_UCESNIKA = 300;
    private static final Pattern KOD = Pattern.compile("^\\d{6}$");

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final UcesnikRepository ucesnikRepository;
    private final StudentRepository studentRepository;
    private final ImenaUcesnika imenaUcesnika;
    private final ZadatakDodelaService zadatakDodelaService;
    private final TokenGenerator tokenGenerator;
    private final ApplicationEventPublisher publisher;
    private final CekiranjeRepository cekiranjeRepository;
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
     * prijavljenog studenta ({@code imaIdentitet}), i godina upisa grupe predavanja izvođenja (predlog godine na
     * {@code /nalog}; null bez predavanja ili grupe); nepoznat ili završen kod -> 404.
     */
    @Transactional(readOnly = true)
    public JavnoIzvodjenjeInfo info(String kod, boolean imaIdentitet) {
        Izvodjenje iz = aktivno(kod);
        Integer godinaUpisa = iz.getPredavanje() == null || iz.getPredavanje().getGrupa() == null ? null
                : iz.getPredavanje().getGrupa().getGodinaUpisa();
        return new JavnoIzvodjenjeInfo(iz.getPrezentacija().getNaziv(), iz.isBoduje(), imaIdentitet, godinaUpisa);
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
     * novog reda i bez granice od 300. Bez studenta bodovan run je 403, pre provere imena. Student izbačen iz ovog
     * izvođenja: 403 {@value #IZBACEN}; bodovan run sa predavanja bez čekiranja studenta na njemu u poslednjih 5 h: 403
     * {@value #PRVO_CEKIRANJE} (i za postojećeg učesnika); obe pre ikakvog upisa. Učesnik koji uđe dok je
     * otvorena ZADATAK runda odmah dobija svoj zadatak ({@link ZadatakDodelaService#dodeliNovom}, pod istim zaključanim
     * redom izvođenja; isti student i dalje ima tačno jednu dodelu). Ne menja verziju stanja (verzija raste po komandi),
     * ali objavljuje promenu: broj i imena učesnika.
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
        if (ucesnikRepository.existsByIzvodjenjeIdAndStudentIdAndIzbacenTrue(iz.getId(), student.getId())) {
            throw new SystemException(IZBACEN, HttpStatus.FORBIDDEN);
        }
        // učesnik sa drugog telefona prolazi samo uz čekiranje, kao i nov (kod iz WhatsApp grupe ne vodi u bodovano od kuće)
        if (iz.isBoduje() && iz.getPredavanje() != null && !cekiranjeRepository
                .existsByPredavanjeIdAndStudentIdAndVremeGreaterThanEqual(iz.getPredavanje().getId(), student.getId(),
                        LocalDateTime.now(clock).minus(CekiranjeService.NA_CASU))) {
            throw new SystemException(PRVO_CEKIRANJE, HttpStatus.FORBIDDEN);
        }
        Optional<Ucesnik> postojeci =
                ucesnikRepository.findFirstByIzvodjenjeIdAndStudentIdAndIzbacenFalse(iz.getId(), student.getId());
        if (postojeci.isPresent()) {
            // isti student sa drugog telefona (ili posle brisanja kolačića): isti red, nov token
            Ucesnik u = postojeci.get();
            String token = tokenGenerator.novi();
            u.setTokenHash(TokenHash.od(token));
            zadatakDodelaService.dodeliNovom(iz, u);
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
        zadatakDodelaService.dodeliNovom(iz, u);
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
