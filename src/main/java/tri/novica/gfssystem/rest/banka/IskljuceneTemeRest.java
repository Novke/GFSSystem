package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaCmd;
import tri.novica.gfssystem.dto.banka.IskljucenaTemaInfo;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.service.banka.IskljuceneTemeService;

import java.util.List;

/**
 * Isključene teme predmeta (iza basic-auth-a, van {@code /public}). Ovde dodata tema je uvek ručna (RUCNO, bez serije);
 * Klod isključuje kroz MCP alat {@code iskljuci_temu}. UI samo prikazuje teme i uključuje ih ({@code DELETE}).
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class IskljuceneTemeRest {

    private final IskljuceneTemeService iskljuceneTemeService;

    /** Sve teme predmeta, najnovije prve. */
    @GetMapping("/predmeti/{id}/iskljucene-teme")
    @ResponseStatus(HttpStatus.OK)
    public List<IskljucenaTemaInfo> lista(@PathVariable Long id) {
        return iskljuceneTemeService.lista(id);
    }

    @PostMapping("/predmeti/{id}/iskljucene-teme")
    @ResponseStatus(HttpStatus.CREATED)
    public IskljucenaTemaInfo dodaj(@PathVariable Long id, @RequestBody IskljucenaTemaCmd cmd) {
        return iskljuceneTemeService.dodaj(id, cmd, IzvorVarijante.RUCNO, null);
    }

    /** Uključuje temu (briše isključenje). */
    @DeleteMapping("/iskljucene-teme/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        iskljuceneTemeService.obrisi(id);
    }
}
