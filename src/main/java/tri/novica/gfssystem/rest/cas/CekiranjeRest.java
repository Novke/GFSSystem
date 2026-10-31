package tri.novica.gfssystem.rest.cas;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.cas.CekiranjeInfo;
import tri.novica.gfssystem.service.cas.CekiranjeService;

/**
 * Čekiranje za nastavnika (ADMIN, van {@code /public}): token QR-a tekućeg prozora i spisak čekiranih. Projektor
 * predavanja i Uživo QR ga čitaju na ~3 s dok je QR na ekranu, stranica predavanja na ~5 s.
 */
@RestController
@RequestMapping("/predavanja/{id}/cekiranje")
@RequiredArgsConstructor
public class CekiranjeRest {

    private final CekiranjeService cekiranje;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public CekiranjeInfo info(@PathVariable Long id) {
        return cekiranje.zaNastavnika(id);
    }
}
