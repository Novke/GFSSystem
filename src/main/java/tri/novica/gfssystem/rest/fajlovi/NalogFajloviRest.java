package tri.novica.gfssystem.rest.fajlovi;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.fajlovi.FajloviPrezentacije;
import tri.novica.gfssystem.dto.fajlovi.MejlFajlaOdgovor;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.fajlovi.MejlFajlova;
import tri.novica.gfssystem.service.fajlovi.NalogFajlovi;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.util.List;

/**
 * Fajlovi uz prezentaciju na nalogu studenta (spec fajlova, odeljak 5), STUDENT u {@code PristupMatrica}. Student se uzima
 * samo iz sesije; tuđe i nepostojeće je 404 {@code Nije pronađeno.}. Pravila su u {@code PristupFajlovima}.
 */
@RestController
@RequestMapping("/public/nalog")
@RequiredArgsConstructor
public class NalogFajloviRest {

    private final NalogFajlovi nalog;
    private final MejlFajlova mejl;
    private final MedijService medijService;

    @GetMapping("predmeti/{id}/fajlovi")
    @ResponseStatus(HttpStatus.OK)
    public List<FajloviPrezentacije> spisak(@PathVariable Long id) {
        return nalog.spisak(student(), id);
    }

    @GetMapping("fajlovi/{id}")
    public ResponseEntity<Resource> preuzmi(@PathVariable Long id) {
        NalogFajlovi.ZaPreuzimanje f = nalog.zaPreuzimanje(student(), id);
        return Preuzimanje.odgovor(f.naziv(), medijService.ucitaj(f.medijId()));
    }

    @PostMapping("fajlovi/{id}/mejl")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MejlFajlaOdgovor posaljiMejlom(@PathVariable Long id) {
        return mejl.posalji(student(), id);
    }

    private static long student() {
        return StudentAutentikacija.trenutna().map(StudentAutentikacija::studentId)
                .orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
