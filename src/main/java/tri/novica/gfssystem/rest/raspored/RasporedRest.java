package tri.novica.gfssystem.rest.raspored;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.raspored.*;
import tri.novica.gfssystem.service.raspored.RasporedService;
import tri.novica.gfssystem.service.raspored.TerminService;

import java.time.LocalDate;
import java.util.List;

/**
 * Raspored časova i termini za nastavnika (ADMIN, van {@code /public}; spec najave odsustva, odeljci 3 i 6). Imena i
 * razlozi najava idu samo ovuda.
 */
@RestController
@RequestMapping("/rasporedi")
@RequiredArgsConstructor
public class RasporedRest {

    private final RasporedService rasporedi;
    private final TerminService termini;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<RasporedInfo> lista() {
        return rasporedi.lista();
    }

    @GetMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    public RasporedDetails detalj(@PathVariable Long id) {
        return rasporedi.detalj(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RasporedDetails kreiraj(@RequestBody RasporedCmd cmd) {
        return rasporedi.kreiraj(cmd);
    }

    @PutMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    public RasporedDetails izmeni(@PathVariable Long id, @RequestBody RasporedCmd cmd) {
        return rasporedi.izmeni(id, cmd);
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        rasporedi.obrisi(id);
    }

    /** Termini svih perioda od {@code od} (podrazumevano danas) narednih {@code dana} (podrazumevano 28, najviše 120). */
    @GetMapping("termini")
    @ResponseStatus(HttpStatus.OK)
    public List<TerminInfo> termini(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate od,
                                    @RequestParam(required = false) Integer dana) {
        return termini.termini(od, dana);
    }

    @PostMapping("termini/otkazi")
    @ResponseStatus(HttpStatus.OK)
    public PromenaTerminaOdgovor otkazi(@RequestBody TerminCmd cmd) {
        return termini.otkazi(cmd);
    }

    @PostMapping("termini/vrati")
    @ResponseStatus(HttpStatus.OK)
    public PromenaTerminaOdgovor vrati(@RequestBody TerminCmd cmd) {
        return termini.vrati(cmd);
    }
}
