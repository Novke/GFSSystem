package tri.novica.gfssystem.service.prijava;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.test.online.StanjeZahtevaRacunara;
import tri.novica.gfssystem.dto.test.online.ZahtevRacunara;
import tri.novica.gfssystem.dto.test.online.ZahtevZaOdobrenje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.PrijavaRacunara;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.PrijavaRacunaraRepository;
import tri.novica.gfssystem.repository.prijava.StudentskiUredjajRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Prijava računara QR-om (spec online testova 11, plan 5.1). Računar napravi zahtev (token u QR-u, tajna u kolačiću
 * {@value #KOLACIC}, broj na ekranu), telefon prijavljen kodom ga odobri, računar pri sledećem čitanju dobija sesiju
 * RACUNAR (4 h od prijave, bez produžavanja). U bazi su samo SHA-256 tokena i tajne; zahtev važi 2 minuta i jednokratan je.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrijavaRacunaraService {

    public static final String KOLACIC = "gfs_racunar";
    public static final String ISTEKLA = "Prijava je istekla. Osveži stranu na računaru.";
    public static final String SA_TELEFONA = "Računar se odobrava sa telefona.";
    static final Duration VAZI = Duration.ofMinutes(2);
    /** Posle odobrenja računar ima još ovoliko da preuzme sesiju, i kad je odobreno u poslednjoj sekundi. */
    static final Duration PREUZIMANJE = Duration.ofMinutes(1);
    static final Duration KOLACIC_TRAJE = Duration.ofSeconds(300);
    static final int MAX_NA_SAT = 200;
    private static final SecureRandom BROJ = new SecureRandom();

    private final PrijavaRacunaraRepository repository;
    private final StudentskiUredjajRepository uredjaji;
    private final StudentRepository studenti;
    private final StudentskaPrijava prijava;
    private final PokusajiServis pokusaji;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    /** Nov zahtev; 429 posle {@value #MAX_NA_SAT} zahteva na sat sa iste adrese. */
    @Transactional
    public ZahtevRacunara zahtev(String ip, HttpServletRequest req, HttpServletResponse res) {
        String kljuc = "ip:" + ip;
        if (pokusaji.dostignut(VrstaPokusaja.RACUNAR_ZAHTEV, kljuc, MAX_NA_SAT, Duration.ofHours(1))) {
            throw new SystemException("Previše zahteva. Pokušaj za nekoliko minuta.", HttpStatus.TOO_MANY_REQUESTS);
        }
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.RACUNAR_ZAHTEV, kljuc);
        String token = tokenGenerator.novi();
        String tajna = tokenGenerator.novi();
        LocalDateTime sada = LocalDateTime.now(clock);
        PrijavaRacunara p = new PrijavaRacunara();
        p.setTokenHes(TokenHash.od(token));
        p.setTajnaHes(TokenHash.od(tajna));
        p.setBroj("%04d".formatted(BROJ.nextInt(10_000)));
        p.setIp(ip);
        p.setKreirano(sada);
        p.setIstice(sada.plus(VAZI));
        repository.save(p);
        Kolacici.postavi(req, res, KOLACIC, tajna, KOLACIC_TRAJE, "Lax");
        return new ZahtevRacunara(token, p.getBroj(), ms(p.getIstice()), clock.millis());
    }

    /**
     * Stanje zahteva ovog pregledača (po kolačiću). Odobren i nepreuzet zahtev se ovde troši: uređaj kojim je pregledač
     * do sada bio prijavljen se odjavljuje, nastaje sesija RACUNAR i briše se kolačić zahteva.
     */
    @Transactional
    public StanjeZahtevaRacunara stanje(Long trenutniUredjajId, HttpServletRequest req, HttpServletResponse res) {
        Optional<String> tajna = Kolacici.procitaj(req, KOLACIC).filter(t -> TokenGenerator.FORMAT.matcher(t).matches());
        if (tajna.isEmpty()) return new StanjeZahtevaRacunara("ISTEKAO", null);
        PrijavaRacunara p = repository.zaTajnuForUpdate(TokenHash.od(tajna.get())).orElse(null);
        if (p == null || p.getPreuzeto() != null) return new StanjeZahtevaRacunara("ISTEKAO", null);
        LocalDateTime sada = LocalDateTime.now(clock);
        if (!p.getIstice().isAfter(sada)) return new StanjeZahtevaRacunara("ISTEKAO", p.getBroj());
        if (p.getOdobreno() == null) return new StanjeZahtevaRacunara("CEKA", p.getBroj());
        p.setPreuzeto(sada);
        repository.save(p);
        if (trenutniUredjajId != null) prijava.odjaviUredjaj(trenutniUredjajId);
        prijava.prijavi(p.getStudentId(), NacinPrijave.RACUNAR, req, res);
        Kolacici.obrisi(req, res, KOLACIC);
        log.info("Prijava računara preuzeta, zahtev={}, student={}", p.getId(), p.getStudentId());
        return new StanjeZahtevaRacunara("ODOBREN", p.getBroj());
    }

    /** Šta telefon odobrava: broj sa ekrana računara i ko se prijavljuje (sam student). 410 za nepoznat ili istekao. */
    @Transactional(readOnly = true)
    public ZahtevZaOdobrenje zaOdobrenje(long studentId, String token) {
        PrijavaRacunara p = vazeci(token, false);
        if (p.getStudentId() != null && p.getStudentId() != studentId) throw isteklo();
        Student s = studenti.findById(studentId).orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
        return new ZahtevZaOdobrenje(p.getBroj(), s.getIme(), s.getPrezime(), s.getIndeks());
    }

    /** Odobrenje sa telefona prijavljenog kodom (403 inače); isti student ponovo je bez efekta, drugi student 410. */
    @Transactional
    public void odobri(long studentId, Long uredjajId, String token) {
        boolean kod = uredjajId != null && uredjaji.findById(uredjajId)
                .map(u -> u.getNacin() == NacinPrijave.KOD && u.getStudent().getId() == studentId).orElse(false);
        if (!kod) throw new SystemException(SA_TELEFONA, HttpStatus.FORBIDDEN);
        PrijavaRacunara p = vazeci(token, true);
        if (p.getOdobreno() != null) {
            if (p.getStudentId() != studentId) throw isteklo();
            return;
        }
        LocalDateTime sada = LocalDateTime.now(clock);
        p.setStudentId(studentId);
        p.setOdobreno(sada);
        if (p.getIstice().isBefore(sada.plus(PREUZIMANJE))) p.setIstice(sada.plus(PREUZIMANJE));
        repository.save(p);
        log.info("Prijava računara odobrena, zahtev={}, student={}", p.getId(), studentId);
    }

    private PrijavaRacunara vazeci(String token, boolean zakljucaj) {
        if (token == null || !TokenGenerator.FORMAT.matcher(token).matches()) throw isteklo();
        String hes = TokenHash.od(token);
        PrijavaRacunara p = (zakljucaj ? repository.zaTokenForUpdate(hes) : repository.findByTokenHes(hes))
                .orElseThrow(PrijavaRacunaraService::isteklo);
        if (p.getPreuzeto() != null || !p.getIstice().isAfter(LocalDateTime.now(clock))) throw isteklo();
        return p;
    }

    static SystemException isteklo() {
        return new SystemException(ISTEKLA, HttpStatus.GONE);
    }

    private long ms(LocalDateTime t) {
        return t.atZone(clock.getZone()).toInstant().toEpochMilli();
    }
}
