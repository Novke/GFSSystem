package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tri.novica.gfssystem.dto.pregled.KontrolnaTablaInfo;
import tri.novica.gfssystem.dto.pregled.PretragaRezultatInfo;
import tri.novica.gfssystem.service.PregledService;

/** Kontrolna tabla i globalna pretraga. Namerno van {@code /public/**}: ostaje iza basic-auth. */
@RestController
@RequiredArgsConstructor
public class PregledRest {

    private final PregledService pregledService;

    @GetMapping("/pregled/kontrolna-tabla")
    @ResponseStatus(HttpStatus.OK)
    public KontrolnaTablaInfo kontrolnaTabla() {
        return pregledService.kontrolnaTabla();
    }

    /** {@code q} kraći od 2 znaka (ili bez {@code q}) daje prazne nizove. */
    @GetMapping("/pretraga")
    @ResponseStatus(HttpStatus.OK)
    public PretragaRezultatInfo pretraga(@RequestParam(required = false) String q) {
        return pregledService.pretraga(q);
    }
}
