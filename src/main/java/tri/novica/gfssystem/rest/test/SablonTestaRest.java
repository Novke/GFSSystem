package tri.novica.gfssystem.rest.test;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.test.online.ArhiviranCmd;
import tri.novica.gfssystem.dto.test.online.SablonCmd;
import tri.novica.gfssystem.dto.test.online.SablonDetails;
import tri.novica.gfssystem.dto.test.online.SablonInfo;
import tri.novica.gfssystem.service.test.SablonTestaService;

import java.util.List;

/** Šabloni online testa (plan 4.1), samo nastavnik. Literal {@code /provera} ima prednost nad {@code /{id}}. */
@RestController
@RequestMapping("/sabloni-testa")
@RequiredArgsConstructor
public class SablonTestaRest {

    private final SablonTestaService service;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<SablonInfo> spisak(@RequestParam(required = false) Long predmetId,
                                   @RequestParam(defaultValue = "false") boolean arhivirani) {
        return service.spisak(predmetId, arhivirani);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public SablonDetails detalji(@PathVariable Long id) {
        return service.detalji(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SablonDetails kreiraj(@RequestBody SablonCmd cmd) {
        return service.kreiraj(cmd);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public SablonDetails izmeni(@PathVariable Long id, @RequestBody SablonCmd cmd) {
        return service.izmeni(id, cmd);
    }

    @PostMapping("/{id}/kopija")
    @ResponseStatus(HttpStatus.CREATED)
    public SablonDetails kopija(@PathVariable Long id) {
        return service.kopija(id);
    }

    @PatchMapping("/{id}/arhiviran")
    @ResponseStatus(HttpStatus.OK)
    public SablonDetails arhiviran(@PathVariable Long id, @RequestBody ArhiviranCmd cmd) {
        return service.arhiviran(id, cmd);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        service.obrisi(id);
    }

    @PostMapping("/provera")
    @ResponseStatus(HttpStatus.OK)
    public SablonDetails provera(@RequestBody SablonCmd cmd) {
        return service.provera(cmd);
    }
}
