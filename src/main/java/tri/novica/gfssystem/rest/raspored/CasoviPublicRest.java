package tri.novica.gfssystem.rest.raspored;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.raspored.NajavaCmd;
import tri.novica.gfssystem.dto.raspored.NaredniCasovi;
import tri.novica.gfssystem.dto.raspored.StudentskiTermin;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.raspored.NajavaService;

/**
 * Naredni časovi i najava odsustva sa naloga studenta (spec najave odsustva, odeljak 4), javno na internetu
 * ({@code /api/public/nalog/casovi*}), sve STUDENT u {@code PristupMatrica}. Student se uzima samo iz sesije, nikad iz
 * tela; odgovor nikad ne nosi broj najava ni tuđa imena.
 */
@RestController
@RequestMapping("/public/nalog/casovi")
@RequiredArgsConstructor
public class CasoviPublicRest {

    private final NajavaService najave;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public NaredniCasovi naredni() {
        return najave.naredni(trenutni().studentId());
    }

    /** "Neću doći": 404 za čas van rasporeda studenta, 409 za otkazan ili počeo čas, 400 za predug razlog. */
    @PostMapping("najavi")
    @ResponseStatus(HttpStatus.OK)
    public StudentskiTermin najavi(@Valid @RequestBody NajavaCmd cmd) {
        return najave.najavi(trenutni().studentId(), cmd);
    }

    /** "Ipak dolazim": iste provere kao najava. */
    @PostMapping("povuci")
    @ResponseStatus(HttpStatus.OK)
    public StudentskiTermin povuci(@Valid @RequestBody NajavaCmd cmd) {
        return najave.povuci(trenutni().studentId(), cmd);
    }

    /** STUDENT putanje: lanac je već tražio studenta, pa je ovo uvek prisutno. */
    private static StudentAutentikacija trenutni() {
        return StudentAutentikacija.trenutna().orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
