package tri.novica.gfssystem.rest.lti;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.lti.LtiRegistracijaCmd;
import tri.novica.gfssystem.dto.lti.LtiRegistracijaInfo;
import tri.novica.gfssystem.dto.lti.LtiVrednostiZaMoodleInfo;
import tri.novica.gfssystem.service.lti.LtiRegistracijaServis;

import java.util.List;

/** LTI registracije (Podešavanja → Moodle). Nastavničko (ADMIN): van {@code /public}, nikad ANONIMNO. */
@RestController
@RequestMapping(path = "/lti/registracije", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LtiRegistracijaRest {

    private final LtiRegistracijaServis servis;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<LtiRegistracijaInfo> lista() {
        return servis.lista();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LtiRegistracijaInfo napravi(@Valid @RequestBody LtiRegistracijaCmd cmd) {
        return servis.napravi(cmd);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public LtiRegistracijaInfo izmeni(@PathVariable Long id, @Valid @RequestBody LtiRegistracijaCmd cmd) {
        return servis.izmeni(id, cmd);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        servis.obrisi(id);
    }

    /** Vrednosti za Moodle formu alata (Tool URL, Initiate login URL, Redirection URI, Public keyset URL). */
    @GetMapping("/vrednosti")
    @ResponseStatus(HttpStatus.OK)
    public LtiVrednostiZaMoodleInfo vrednosti() {
        return servis.vrednosti();
    }
}
