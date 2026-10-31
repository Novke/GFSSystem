package tri.novica.gfssystem.rest.cas;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.cas.CasStanje;
import tri.novica.gfssystem.dto.cas.PotvrdaCekiranjaCmd;
import tri.novica.gfssystem.dto.cas.SkeniranjeCmd;
import tri.novica.gfssystem.dto.cas.SkeniranjeOdgovor;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.NastavnikAutentikacija;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.cas.CekiranjeService;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Duration;
import java.util.Optional;

/**
 * Čekiranje na času sa telefona studenta (spec studentskog dela 4.2, 4.10), javno na internetu
 * ({@code /api/public/nalog/cas*}): {@code skeniraj} je ANONIMNO (prijavljen student se čekira odmah, neprijavljen dobija
 * ključ), {@code potvrdi} i stanje traže studentski kolačić (STUDENT u {@code PristupMatrica}). Student se uzima samo iz
 * sesije. {@code skeniraj} i {@code potvrdi} postavljaju kolačić {@code gfs_uredjaj} (oznaka telefona, 365 dana) kad ga
 * nema; servis dobija samo njegov SHA-256.
 */
@RestController
@RequestMapping("/public/nalog/cas")
@RequiredArgsConstructor
public class CasPublicRest {

    static final String ODJAVI_SE_KAO_NASTAVNIK = "Odjavi se kao nastavnik.";
    static final Duration UREDJAJ_TRAJE = Duration.ofDays(365);

    private final CekiranjeService cekiranje;
    private final TokenGenerator tokenGenerator;

    /**
     * Skeniran QR: 410 za istekao token ili završen čas, 403 za tuđu grupu, 409 {@value #ODJAVI_SE_KAO_NASTAVNIK} kad
     * zahtev nosi nastavničku sesiju (nastavnik ima prednost, pa student ne bi bio prepoznat).
     */
    @PostMapping("skeniraj")
    @ResponseStatus(HttpStatus.OK)
    public SkeniranjeOdgovor skeniraj(@Valid @RequestBody SkeniranjeCmd cmd, HttpServletRequest req,
                                      HttpServletResponse res) {
        if (NastavnikAutentikacija.trenutna().isPresent()) {
            throw new SystemException(ODJAVI_SE_KAO_NASTAVNIK, HttpStatus.CONFLICT);
        }
        Optional<Long> student = StudentAutentikacija.trenutna().map(StudentAutentikacija::studentId);
        return cekiranje.skeniraj(cmd.token(), student, uredjaj(req, res));
    }

    /** Čekiranje iz ključa posle prijave: 410 za nepoznat, istekao ili potrošen ključ. */
    @PostMapping("potvrdi")
    @ResponseStatus(HttpStatus.OK)
    public CasStanje potvrdi(@Valid @RequestBody PotvrdaCekiranjaCmd cmd, HttpServletRequest req, HttpServletResponse res) {
        return cekiranje.potvrdi(cmd.kljuc(), trenutni().studentId(), uredjaj(req, res));
    }

    /** Stanje časa: predavanje na kome je student (5 h od čekiranja) i aktivno izvođenje; {@code predavanje: null} inače. */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CasStanje stanje() {
        return cekiranje.stanje(trenutni().studentId());
    }

    /** SHA-256 kolačića {@code gfs_uredjaj}; nov (32 znaka) kad ga nema ili nije ispravnog oblika. */
    private String uredjaj(HttpServletRequest req, HttpServletResponse res) {
        Optional<String> postojeci = Kolacici.procitaj(req, Kolacici.UREDJAJ)
                .filter(v -> TokenGenerator.FORMAT.matcher(v).matches());
        String vrednost = postojeci.orElseGet(tokenGenerator::novi);
        if (postojeci.isEmpty()) {
            Kolacici.postavi(req, res, Kolacici.UREDJAJ, vrednost, UREDJAJ_TRAJE, "Lax");
        }
        return TokenHash.od(vrednost);
    }

    /** STUDENT putanje: lanac je već tražio uređaj, pa je ovo uvek prisutno. */
    private static StudentAutentikacija trenutni() {
        return StudentAutentikacija.trenutna().orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
