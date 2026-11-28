package tri.novica.gfssystem.rest.prijava;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.test.online.StanjeZahtevaRacunara;
import tri.novica.gfssystem.dto.test.online.ZahtevRacunara;
import tri.novica.gfssystem.dto.test.online.ZahtevZaOdobrenje;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.NastavnikAutentikacija;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.prijava.PrijavaRacunaraService;
import tri.novica.gfssystem.utility.KlijentIp;

/**
 * Prijava računara QR-om (plan 5.1): {@code zahtev} (POST i GET) je ANONIMNO i radi ga računar, {@code {token}} i
 * {@code {token}/odobri} su STUDENT i radi ih telefon. Javno kao {@code /api/public/nalog/racunar/*}.
 */
@RestController
@RequestMapping("/public/nalog/racunar")
@RequiredArgsConstructor
public class RacunarPublicRest {

    private final PrijavaRacunaraService service;

    /** Nov zahtev i kolačić {@code gfs_racunar}; 409 kad pregledač nosi nastavničku sesiju, 429 posle 200 na sat. */
    @PostMapping("zahtev")
    @ResponseStatus(HttpStatus.OK)
    public ZahtevRacunara zahtev(HttpServletRequest req, HttpServletResponse res) {
        if (NastavnikAutentikacija.trenutna().isPresent()) {
            throw new SystemException(NalogPublicRest.ODJAVI_SE_KAO_NASTAVNIK, HttpStatus.CONFLICT);
        }
        return service.zahtev(KlijentIp.iz(req), req, res);
    }

    /** Stanje zahteva ovog pregledača; ODOBREN u istom odgovoru postavlja {@code gfs_student} (RACUNAR). */
    @GetMapping("zahtev")
    @ResponseStatus(HttpStatus.OK)
    public StanjeZahtevaRacunara stanje(HttpServletRequest req, HttpServletResponse res) {
        if (NastavnikAutentikacija.trenutna().isPresent()) {
            throw new SystemException(NalogPublicRest.ODJAVI_SE_KAO_NASTAVNIK, HttpStatus.CONFLICT);
        }
        Long uredjaj = StudentAutentikacija.trenutna().map(StudentAutentikacija::uredjajId).orElse(null);
        return service.stanje(uredjaj, req, res);
    }

    @GetMapping("{token}")
    @ResponseStatus(HttpStatus.OK)
    public ZahtevZaOdobrenje zaOdobrenje(@PathVariable String token) {
        return service.zaOdobrenje(sesija().studentId(), token);
    }

    @PostMapping("{token}/odobri")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odobri(@PathVariable String token) {
        StudentAutentikacija s = sesija();
        service.odobri(s.studentId(), s.uredjajId(), token);
    }

    private static StudentAutentikacija sesija() {
        return StudentAutentikacija.trenutna().orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
