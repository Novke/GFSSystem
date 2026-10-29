package tri.novica.gfssystem.rest.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.portal.OtvorenoZaGrupu;
import tri.novica.gfssystem.service.portal.TemeZaVezbanje;

import java.util.List;

/**
 * Panel "Za vežbanje" na čvoru banke (nastavnik, ADMIN; spec studentskog dela 5.3, 12.3): stanje po grupi studenata,
 * ručno otvaranje (RUCNO) i zatvaranje (briše zapise čvora i podstabla). Van {@code /public/**}.
 */
@RestController
@RequestMapping("/banka/grupe/{id}/otvoreno")
@RequiredArgsConstructor
public class TemeZaVezbanjeRest {

    private final TemeZaVezbanje teme;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<OtvorenoZaGrupu> otvoreno(@PathVariable Long id) {
        return teme.otvoreno(id);
    }

    @PutMapping("{grupaStudenataId}")
    @ResponseStatus(HttpStatus.OK)
    public List<OtvorenoZaGrupu> otvori(@PathVariable Long id, @PathVariable Long grupaStudenataId) {
        return teme.otvori(id, grupaStudenataId);
    }

    @DeleteMapping("{grupaStudenataId}")
    @ResponseStatus(HttpStatus.OK)
    public List<OtvorenoZaGrupu> zatvori(@PathVariable Long id, @PathVariable Long grupaStudenataId) {
        return teme.zatvori(id, grupaStudenataId);
    }
}
