package tri.novica.gfssystem.rest.prijava;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.prijava.BrojObavestenjaInfo;
import tri.novica.gfssystem.dto.prijava.ObavestenjeInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.prijava.ObavestenjeServis;
import tri.novica.gfssystem.utility.PageableUtil;

import java.util.Set;

/**
 * Obaveštenja za nastavnika (spec 6): ADMIN, iza nastavničke sesije (lanac vraća 401 bez nje). Ništa od ovoga nije pod
 * {@code /public}.
 */
@RestController
@RequestMapping("/obavestenja")
@RequiredArgsConstructor
public class ObavestenjeRest {

    static final Set<String> SORT_POLJA = Set.of("kreirano", "vrsta", "procitano", "reseno");
    static final Sort PODRAZUMEVANI_SORT = Sort.by(Sort.Order.desc("kreirano"));

    private final ObavestenjeServis servis;

    /** {@code stanje=otvorena} (podrazumevano) ili {@code sva}; najnovija prva. */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public PagedModel<ObavestenjeInfo> lista(@RequestParam(defaultValue = "otvorena") String stanje, Pageable pageable) {
        boolean samoOtvorena = switch (stanje) {
            case "otvorena" -> true;
            case "sva" -> false;
            default -> throw new SystemException("Neispravan parametar: stanje.", 400);
        };
        return new PagedModel<>(servis.lista(samoOtvorena,
                PageableUtil.proveri(pageable, SORT_POLJA, PODRAZUMEVANI_SORT)));
    }

    @GetMapping("broj")
    @ResponseStatus(HttpStatus.OK)
    public BrojObavestenjaInfo broj() {
        return servis.broj();
    }

    @PostMapping("{id}/procitano")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void procitano(@PathVariable Long id) {
        servis.procitano(id);
    }

    @PostMapping("{id}/reseno")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reseno(@PathVariable Long id) {
        servis.reseno(id);
    }

    @PostMapping("procitaj-sve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void procitajSve() {
        servis.procitajSve();
    }
}
