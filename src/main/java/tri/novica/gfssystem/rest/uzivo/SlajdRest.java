package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.uzivo.PoteziSloja;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.util.List;

/**
 * Izmena, brisanje i dupliranje pojedinačnog slajda i potezi njegovih crteža (iza basic-auth-a); dodavanje je u
 * {@link PrezentacijaRest}.
 */
@RestController
@RequestMapping(path = "/slajdovi", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SlajdRest {

    private final PrezentacijaService prezentacijaService;

    /**
     * Potezi crteža slajda (prozor publike i konzola; snimak izvođenja ih ne nosi). Nastavnički endpoint, nikad pod
     * {@code /public/**}: telefoni ne dobijaju sadržaj platna.
     */
    @GetMapping("/{id}/potezi")
    @ResponseStatus(HttpStatus.OK)
    public List<PoteziSloja> potezi(@PathVariable Long id) {
        return prezentacijaService.poteziSlajda(id);
    }

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
