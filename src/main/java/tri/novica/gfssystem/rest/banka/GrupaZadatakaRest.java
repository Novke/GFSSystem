package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;

import java.util.List;

/** Stablo grupa zadataka banke (iza basic-auth-a, van {@code /public}): stablo predmeta i namene, grupe, premeštanje, redosled. */
@RestController
@RequestMapping(path = "/banka", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class GrupaZadatakaRest {

    private final GrupaZadatakaService grupaZadatakaService;

    /** Celo stablo predmeta za jednu namenu, sa brojačima varijanti (unutrašnji čvor = zbir podstabla). */
    @GetMapping("/stablo")
    @ResponseStatus(HttpStatus.OK)
    public List<GrupaCvor> stablo(@RequestParam Long predmetId, @RequestParam Namena namena) {
        return grupaZadatakaService.stablo(predmetId, namena);
    }

    @PostMapping("/grupe")
    @ResponseStatus(HttpStatus.CREATED)
    public GrupaDetails kreiraj(@RequestBody GrupaCmd cmd) {
        return grupaZadatakaService.kreiraj(cmd);
    }

    @GetMapping("/grupe/{id}")
    @ResponseStatus(HttpStatus.OK)
    public GrupaDetails detalji(@PathVariable Long id) {
        return grupaZadatakaService.detalji(id);
    }

    /**
     * Puna zamena naziva, opisa, težine i uputstva; bez {@code polja} (ili {@code null}) šema i odgovori varijanti ostaju,
     * {@code "polja": []} briše šemu.
     */
    @PutMapping("/grupe/{id}")
    @ResponseStatus(HttpStatus.OK)
    public GrupaDetails izmeni(@PathVariable Long id, @RequestBody GrupaCmd cmd) {
        return grupaZadatakaService.izmeni(id, cmd);
    }

    @DeleteMapping("/grupe/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        grupaZadatakaService.obrisi(id);
    }

    @PostMapping("/grupe/{id}/premesti")
    @ResponseStatus(HttpStatus.OK)
    public GrupaDetails premesti(@PathVariable Long id, @RequestBody PremestiCmd cmd) {
        return grupaZadatakaService.premesti(id, cmd);
    }

    /** Redosled dece grupe {@code roditeljId}, ili korena predmeta i namene kad ga nema. */
    @PutMapping("/grupe/redosled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redosled(@RequestParam Long predmetId, @RequestParam Namena namena,
                         @RequestParam(required = false) Long roditeljId, @RequestBody RedosledGrupaCmd cmd) {
        grupaZadatakaService.redosled(roditeljId, predmetId, namena, cmd);
    }
}
