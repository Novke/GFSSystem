package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.util.List;

/** Nastavnički editor prezentacija (iza basic-auth-a): prezentacije predmeta, dodavanje i redosled slajdova. */
@RestController
@RequestMapping(path = "/prezentacije", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PrezentacijaRest {

    private final PrezentacijaService prezentacijaService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<PrezentacijaInfo> lista(@RequestParam(required = false) Long predmetId) {
        return prezentacijaService.lista(predmetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrezentacijaDetails kreiraj(@RequestBody CreatePrezentacijaCmd cmd) {
        return prezentacijaService.kreiraj(cmd);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public PrezentacijaDetails detalji(@PathVariable Long id) {
        return prezentacijaService.detalji(id);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public PrezentacijaDetails izmeni(@PathVariable Long id, @RequestBody UpdatePrezentacijaCmd cmd) {
        return prezentacijaService.izmeni(id, cmd);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        prezentacijaService.obrisi(id);
    }

    @PostMapping("/{id}/dupliraj")
    @ResponseStatus(HttpStatus.CREATED)
    public PrezentacijaDetails dupliraj(@PathVariable Long id) {
        return prezentacijaService.dupliraj(id);
    }

    /** Novi slajd na kraj ili odmah posle slajda {@code posle}. */
    @PostMapping("/{id}/slajdovi")
    @ResponseStatus(HttpStatus.CREATED)
    public SlajdDetails dodajSlajd(@PathVariable Long id, @RequestParam(required = false) Long posle,
                                   @RequestBody SlajdCmd cmd) {
        return prezentacijaService.dodajSlajd(id, cmd, posle);
    }

    @PutMapping("/{id}/redosled")
    @ResponseStatus(HttpStatus.OK)
    public PrezentacijaDetails redosled(@PathVariable Long id, @RequestBody RedosledCmd cmd) {
        return prezentacijaService.redosled(id, cmd.slajdIds());
    }

    /** Predavanja za dijalog "Pokreni": predmet prezentacije, nezavršena ili od danas, najnovija prva, najviše 30. */
    @GetMapping("/{id}/predavanja")
    @ResponseStatus(HttpStatus.OK)
    public List<PredavanjeZaPokretanjeInfo> predavanja(@PathVariable Long id) {
        return prezentacijaService.predavanjaZaPokretanje(id);
    }
}
