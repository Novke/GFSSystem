package tri.novica.gfssystem.rest.fajlovi;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeCmd;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeInfo;
import tri.novica.gfssystem.dto.fajlovi.RedosledFajlovaCmd;
import tri.novica.gfssystem.service.fajlovi.FajloviPrezentacijeServis;

import java.util.List;

/**
 * Fajlovi za studente uz prezentaciju, nastavnik (spec fajlova 5; ADMIN po {@code PristupMatrica}, nikad pod
 * {@code /public/**}). Sva pravila su u {@link FajloviPrezentacijeServis}.
 */
@RestController
@RequiredArgsConstructor
public class FajloviPrezentacijeRest {

    private final FajloviPrezentacijeServis fajlovi;

    @GetMapping(path = "/prezentacije/{id}/fajlovi", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<FajlPrezentacijeInfo> lista(@PathVariable Long id) {
        return fajlovi.lista(id);
    }

    @PostMapping(path = "/prezentacije/{id}/fajlovi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FajlPrezentacijeInfo dodaj(@PathVariable Long id, @RequestParam("fajl") MultipartFile fajl,
                                      @RequestParam(required = false) Long slajdId) {
        return fajlovi.dodaj(id, fajl, slajdId);
    }

    @PutMapping(path = "/fajlovi-prezentacije/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public FajlPrezentacijeInfo izmeni(@PathVariable Long id, @Valid @RequestBody FajlPrezentacijeCmd cmd) {
        return fajlovi.izmeni(id, cmd);
    }

    @PutMapping(path = "/prezentacije/{id}/fajlovi/redosled", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<FajlPrezentacijeInfo> redosled(@PathVariable Long id, @Valid @RequestBody RedosledFajlovaCmd cmd) {
        return fajlovi.redosled(id, cmd.ids());
    }

    @DeleteMapping("/fajlovi-prezentacije/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void obrisi(@PathVariable Long id) {
        fajlovi.obrisi(id);
    }

    @GetMapping("/fajlovi-prezentacije/{id}/sadrzaj")
    public ResponseEntity<Resource> sadrzaj(@PathVariable Long id) {
        FajloviPrezentacijeServis.Sadrzaj s = fajlovi.sadrzaj(id);
        return Preuzimanje.odgovor(s.naziv(), s.fajl());
    }
}
