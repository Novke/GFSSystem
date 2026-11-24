package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.uzivo.CrtezIzvodjenjaInfo;
import tri.novica.gfssystem.dto.uzivo.RanijiCrtez;
import tri.novica.gfssystem.dto.uzivo.StranaCrteza;
import tri.novica.gfssystem.dto.uzivo.VerzijaCrteza;
import tri.novica.gfssystem.entity.uzivo.PozadinaCrteza;
import tri.novica.gfssystem.service.uzivo.CrtanjeService;

import java.util.List;

/**
 * Crtanje u izvođenju i tabla predavanja (spec crtanja 6; iza prijave nastavnika, ništa pod {@code /public/**}). Upis je ceo
 * niz elemenata strane; konzola ga šalje posle svake izmene, jedan po jedan.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CrtanjeRest {

    private final CrtanjeService crtanjeService;

    @GetMapping("/izvodjenja/{id}/crtezi")
    @ResponseStatus(HttpStatus.OK)
    public List<CrtezIzvodjenjaInfo> sve(@PathVariable Long id) {
        return crtanjeService.sve(id);
    }

    @GetMapping("/izvodjenja/{id}/crtezi/strana")
    @ResponseStatus(HttpStatus.OK)
    public StranaCrteza strana(@PathVariable Long id, @RequestParam Long slajdId, @RequestParam PozadinaCrteza pozadina) {
        return crtanjeService.strana(id, slajdId, pozadina);
    }

    @PutMapping(path = "/izvodjenja/{id}/crtezi/strana", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public VerzijaCrteza upisi(@PathVariable Long id, @RequestParam Long slajdId, @RequestParam PozadinaCrteza pozadina,
                               @RequestBody JsonNode telo) {
        return crtanjeService.upisi(id, slajdId, pozadina, telo);
    }

    @GetMapping("/izvodjenja/{id}/crtezi/ranije")
    @ResponseStatus(HttpStatus.OK)
    public List<RanijiCrtez> ranije(@PathVariable Long id, @RequestParam Long slajdId) {
        return crtanjeService.ranije(id, slajdId);
    }

    /** 204 kad je {@code posle} zadat, a tabla nema noviju verziju (publika na drugom uređaju proverava na 3 s). */
    @GetMapping("/predavanja/{id}/tabla")
    public ResponseEntity<StranaCrteza> tabla(@PathVariable Long id, @RequestParam(required = false) Integer posle) {
        return crtanjeService.tabla(id, posle).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping(path = "/predavanja/{id}/tabla", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public VerzijaCrteza upisiTablu(@PathVariable Long id, @RequestBody JsonNode telo) {
        return crtanjeService.upisiTablu(id, telo);
    }
}
