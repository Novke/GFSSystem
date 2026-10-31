package tri.novica.gfssystem.rest.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.portal.ObjavaOcenaInfo;
import tri.novica.gfssystem.service.portal.ObjavaOcenaServis;

/**
 * Objava predloga ocena studentima (nastavnik, ADMIN; spec studentskog dela 12.4): stanje, objavi (ili ponovo, nov snimak)
 * i povuci, po predmetu i grupi.
 */
@RestController
@RequestMapping("/ocenjivanje/predmet/{predmetId}/objava")
@RequiredArgsConstructor
public class ObjavaOcenaRest {

    private final ObjavaOcenaServis objava;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ObjavaOcenaInfo stanje(@PathVariable Long predmetId, @RequestParam Long grupa) {
        return objava.stanje(predmetId, grupa);
    }

    @PutMapping("{grupaId}")
    @ResponseStatus(HttpStatus.OK)
    public ObjavaOcenaInfo objavi(@PathVariable Long predmetId, @PathVariable Long grupaId) {
        return objava.objavi(predmetId, grupaId);
    }

    @DeleteMapping("{grupaId}")
    @ResponseStatus(HttpStatus.OK)
    public ObjavaOcenaInfo povuci(@PathVariable Long predmetId, @PathVariable Long grupaId) {
        return objava.povuci(predmetId, grupaId);
    }
}
