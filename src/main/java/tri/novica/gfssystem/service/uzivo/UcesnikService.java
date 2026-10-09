package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.uzivo.JavnoIzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.UcesnikInfo;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Javni ulaz u izvođenje uživo: naziv po kodu, prijava imenom (kolačić sa nasumičnim tokenom; u bazi samo njegov
 * SHA-256) i provera kolačića ("ja", WebSocket rukovanje). Prijava zaključava red izvođenja, kao komande i odgovori,
 * pa dve istovremene prijave istog imena ne dobijaju isto ime i granica od 300 učesnika važi tačno.
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class UcesnikService {

    public static final String NEPOZNAT_KOD = "Izvođenje sa ovim kodom ne postoji ili je završeno.";
    public static final String POPUNJENO = "Izvođenje je popunjeno.";
    /** Spec 2.12: najviše 300 (neizbačenih) učesnika po izvođenju. */
    static final int MAX_UCESNIKA = 300;
    private static final Pattern KOD = Pattern.compile("^\\d{6}$");

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final UcesnikRepository ucesnikRepository;
    private final ImenaUcesnika imenaUcesnika;
    private final TokenGenerator tokenGenerator;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /** Prijavljen učesnik i token za kolačić (token se ne čuva i ne loguje). */
    public record Prijavljen(UcesnikInfo info, String token) {
    }

    /** Naziv prezentacije aktivnog izvođenja; nepoznat ili završen kod -> 404. */
    @Transactional(readOnly = true)
    public JavnoIzvodjenjeInfo info(String kod) {
        return new JavnoIzvodjenjeInfo(aktivno(kod).getPrezentacija().getNaziv());
    }

    /** Učesnik kome pripada token, ako je to učesnik ovog (aktivnog) izvođenja i nije izbačen. */
    @Transactional(readOnly = true)
    public Optional<UcesnikInfo> ja(String kod, String token) {
        return vazeci(token)
                .filter(u -> kod != null && kod.equals(u.getIzvodjenje().getAktivanKod()))
                .map(UcesnikService::info);
    }

    /**
     * Novi učesnik pod jedinstvenim imenom ({@code "ana 2"} kad "Ana" već postoji); 404 za nepoznat ili završen kod,
     * 400 za neispravno ime, 409 kad izvođenje već ima 300 učesnika. Ne menja verziju stanja (verzija raste po
     * komandi), ali objavljuje promenu: broj i imena učesnika.
     */
    public Prijavljen prijavi(String kod, String ime) {
        // samo id pre zaključavanja: entitet učitan ranije ne bi video završetak do kog je došlo dok se čekalo
        Long id = (ispravanKod(kod) ? izvodjenjeRepository.findIdByAktivanKodAndStatus(kod, StatusIzvodjenja.AKTIVNO)
                : Optional.<Long>empty()).orElseThrow(UcesnikService::nepoznatKod);
        String validno = imenaUcesnika.validiraj(ime);
        // pod zaključavanjem iznova: izvođenje je možda završeno dok se čekalo
        Izvodjenje iz = izvodjenjeRepository.findByIdForUpdate(id)
                .filter(i -> i.getStatus() == StatusIzvodjenja.AKTIVNO)
                .orElseThrow(UcesnikService::nepoznatKod);
        if (ucesnikRepository.countByIzvodjenjeIdAndIzbacenFalse(iz.getId()) >= MAX_UCESNIKA) {
            throw new SystemException(POPUNJENO, HttpStatus.CONFLICT);
        }
        String token = tokenGenerator.novi();
        Ucesnik u = new Ucesnik();
        u.setIzvodjenje(iz);
        u.setIme(imenaUcesnika.jedinstvenoIme(iz.getId(), validno, null));
        u.setTokenHash(TokenHash.od(token));
        u.setIzbacen(false);
        u.setKreirano(LocalDateTime.now(clock));
        u = ucesnikRepository.save(u);
        publisher.publishEvent(new IzvodjenjePromenjeno(iz.getId()));
        return new Prijavljen(info(u), token);
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
