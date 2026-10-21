package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.service.banka.VarijantaService;

import java.util.List;

/**
 * Varijante zadataka banke (iza basic-auth-a, van {@code /public}): lista grupe, CRUD, statusi, render i render-proba.
 * Upis renderuje sinhrono: neuspeh kompilacije je stanje u odgovoru ({@code renderStatus=GRESKA}), a render servis koji
 * ne radi je 503 {@code Render servis nije dostupan.} (ništa nije upisano).
 */
@RestController
@RequestMapping(path = "/banka", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class VarijantaRest {

    private final VarijantaService varijantaService;

    /** Varijante grupe po redosledu; bez {@code status} sve. */
    @GetMapping("/grupe/{id}/varijante")
    @ResponseStatus(HttpStatus.OK)
    public List<VarijantaInfo> lista(@PathVariable Long id, @RequestParam(required = false) StatusVarijante status) {
        return varijantaService.lista(id, status);
    }

    @PostMapping("/grupe/{id}/varijante")
    @ResponseStatus(HttpStatus.CREATED)
    public VarijantaDetails kreiraj(@PathVariable Long id, @RequestBody VarijantaCmd cmd) {
        return varijantaService.kreiraj(id, cmd);
    }

    @GetMapping("/varijante/{id}")
    @ResponseStatus(HttpStatus.OK)
    public VarijantaDetails detalji(@PathVariable Long id) {
        return varijantaService.detalji(id);
    }

    /** Puna zamena sadržaja, odgovora i priloga; renderuje samo kad se ulaz rendera promenio. */
    @PutMapping("/varijante/{id}")
    @ResponseStatus(HttpStatus.OK)
    public VarijantaDetails izmeni(@PathVariable Long id, @RequestBody VarijantaCmd cmd) {
        return varijantaService.izmeni(id, cmd);
    }

    @DeleteMapping("/varijante/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        varijantaService.obrisi(id);
    }

    @PostMapping("/varijante/{id}/odobri")
    @ResponseStatus(HttpStatus.OK)
    public VarijantaDetails odobri(@PathVariable Long id) {
        return varijantaService.odobri(id);
    }

    @PostMapping("/varijante/{id}/arhiviraj")
    @ResponseStatus(HttpStatus.OK)
    public VarijantaDetails arhiviraj(@PathVariable Long id) {
        return varijantaService.arhiviraj(id);
    }

    @PostMapping("/varijante/{id}/vrati-u-nacrt")
    @ResponseStatus(HttpStatus.OK)
    public VarijantaDetails vratiUNacrt(@PathVariable Long id) {
        return varijantaService.vratiUNacrt(id);
    }

    /** Ponovni render bez obzira na keš. */
    @PostMapping("/varijante/{id}/render")
    @ResponseStatus(HttpStatus.OK)
    public VarijantaDetails render(@PathVariable Long id) {
        return varijantaService.render(id);
    }

    @PostMapping("/varijante/{id}/dupliraj")
    @ResponseStatus(HttpStatus.CREATED)
    public VarijantaDetails dupliraj(@PathVariable Long id) {
        return varijantaService.dupliraj(id);
    }

    /** Render bez čuvanja (pregled u editoru); SVG kao tekst. Najviše 4 istovremeno, preko toga 429. */
    @PostMapping("/render-proba")
    @ResponseStatus(HttpStatus.OK)
    public RenderProbaInfo proba(@RequestBody RenderProbaCmd cmd) {
        return varijantaService.proba(cmd);
    }
}
