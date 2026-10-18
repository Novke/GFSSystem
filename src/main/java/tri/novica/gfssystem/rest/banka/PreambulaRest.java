package tri.novica.gfssystem.rest.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.service.banka.LatexPreambulaService;

/**
 * LaTeX preambula predmeta za render banke zadataka (iza basic-auth-a, van {@code /public}). Izmena poništava render keš
 * cele banke predmeta; ponovni render ide kroz {@code POST /banka/predmeti/{id}/renderuj-sve}.
 */
@RestController
@RequestMapping(path = "/predmeti", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PreambulaRest {

    private final LatexPreambulaService preambulaService;

    @GetMapping("/{id}/latex-preambula")
    @ResponseStatus(HttpStatus.OK)
    public PreambulaInfo preambula(@PathVariable Long id) {
        return preambulaService.preambula(id);
    }

    /** Ključ {@code preambula} je obavezan; {@code null} ili prazan tekst vraća podrazumevanu. */
    @PutMapping("/{id}/latex-preambula")
    @ResponseStatus(HttpStatus.OK)
    public PreambulaInfo postavi(@PathVariable Long id, @RequestBody PreambulaCmd cmd) {
        return preambulaService.postavi(id, cmd);
    }
}
