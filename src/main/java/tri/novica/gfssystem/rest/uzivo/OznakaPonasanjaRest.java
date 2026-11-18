package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.uzivo.OznakaPonasanjaInfo;
import tri.novica.gfssystem.service.uzivo.OznakePonasanja;

import java.util.List;

/** Oznake ponašanja na kartonu studenta (samo nastavnik): lista i brisanje. */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OznakaPonasanjaRest {

    private final OznakePonasanja oznake;

    @GetMapping("/studenti/{id}/oznake-ponasanja")
    @ResponseStatus(HttpStatus.OK)
    public List<OznakaPonasanjaInfo> zaStudenta(@PathVariable Long id) {
        return oznake.zaStudenta(id);
    }

    @DeleteMapping("/oznake-ponasanja/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        oznake.obrisi(id);
    }
}
