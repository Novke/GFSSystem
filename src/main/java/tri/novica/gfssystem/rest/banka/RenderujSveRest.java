package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.banka.RenderujSveInfo;
import tri.novica.gfssystem.service.banka.VarijantaService;

/**
 * Ponovni render cele banke predmeta (posle izmene preambule). Jedan poziv je jedan korak: sinhron je, renderuje najviše
 * 200 varijanti i staje posle 40 s; front ga ponavlja dok {@code preostalo} nije 0. Render servis koji ne radi je 503.
 */
@RestController
@RequestMapping(path = "/banka", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RenderujSveRest {

    private final VarijantaService varijantaService;

    @PostMapping("/predmeti/{id}/renderuj-sve")
    @ResponseStatus(HttpStatus.OK)
    public RenderujSveInfo renderujSve(@PathVariable Long id) {
        return varijantaService.renderujSve(id);
    }
}
