package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.SerijaInfo;
import tri.novica.gfssystem.service.banka.SerijaService;

import java.util.List;

/**
 * Klodove serije u banci (iza basic-auth-a, van {@code /public}): pregled sa brojačima i "Odbaci celu seriju" iz UI-ja.
 * Odbacivanje dira samo banku (Klodovi neodobreni nacrti i CLAUDE grupe koje time ostanu prazne); prezentacije serije
 * poništava MCP alat {@code ponisti_seriju}.
 */
@RestController
@RequestMapping(path = "/banka", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SerijaRest {

    private final SerijaService serijaService;

    /** Serije predmeta, najnovije prve; bez {@code predmetId} 400 {@code Predmet je obavezan.} (poruka servisa). */
    @GetMapping("/serije")
    @ResponseStatus(HttpStatus.OK)
    public List<SerijaInfo> serije(@RequestParam(required = false) Long predmetId) {
        return serijaService.serije(predmetId);
    }

    /** Briše šta se iz serije u banci još sme obrisati i vraća obrisano i preskočeno; serija van obrasca 400. */
    @PostMapping("/serije/{serija}/odbaci")
    @ResponseStatus(HttpStatus.OK)
    public PonistavanjeInfo odbaci(@PathVariable String serija) {
        return serijaService.ponisti(serija, true);
    }
}
