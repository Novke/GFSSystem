package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

/** Izmena, brisanje i dupliranje pojedinačnog slajda (iza basic-auth-a); dodavanje je u {@link PrezentacijaRest}. */
@RestController
@RequestMapping(path = "/slajdovi", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SlajdRest {

    private final PrezentacijaService prezentacijaService;

    /** Puna zamena, uključujući pitanje i opcije. */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public SlajdDetails izmeni(@PathVariable Long id, @RequestBody SlajdCmd cmd) {
        return prezentacijaService.izmeniSlajd(id, cmd);
    }

    /** Preostali slajdovi se prenumerišu. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        prezentacijaService.obrisiSlajd(id);
    }

    /** Kopija odmah posle originala. */
    @PostMapping("/{id}/dupliraj")
    @ResponseStatus(HttpStatus.CREATED)
    public SlajdDetails dupliraj(@PathVariable Long id) {
        return prezentacijaService.duplirajSlajd(id);
    }
}
