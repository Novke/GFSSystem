package tri.novica.gfssystem.rest.prijava;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.prijava.KodZaDiktiranjeInfo;
import tri.novica.gfssystem.dto.prijava.OpozvanoInfo;
import tri.novica.gfssystem.dto.prijava.StudentNalogInfo;
import tri.novica.gfssystem.service.prijava.StudentNalogServis;

/**
 * Sekcija "Nalog" na kartici studenta (spec 5.3): ADMIN, iza nastavničke sesije (lanac vraća 401 bez nje). Nije pod
 * {@code /public}. Mejl studenta se menja postojećim {@code PUT /studenti/{id}}.
 */
@RestController
@RequestMapping("/studenti/{id}/nalog")
@RequiredArgsConstructor
public class StudentNalogRest {

    private final StudentNalogServis servis;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public StudentNalogInfo pregled(@PathVariable Long id) {
        return servis.pregled(id);
    }

    /** Kod za prijavu na mejl studenta; rešava njegova otvorena obaveštenja "Ne stiže mi kod". */
    @PostMapping("posalji-kod")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void posaljiKod(@PathVariable Long id) {
        servis.posaljiKod(id);
    }

    /**
     * Kod koji nastavnik izdiktira studentu (bez mejla): isti kod i ista pravila kao kod sa mejla. Vraća se jednom,
     * {@code Cache-Control: no-store}; rešava otvorena obaveštenja studenta.
     */
    @PostMapping("kod-za-diktiranje")
    @ResponseStatus(HttpStatus.OK)
    public KodZaDiktiranjeInfo kodZaDiktiranje(@PathVariable Long id, HttpServletResponse res) {
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return servis.kodZaDiktiranje(id);
    }

    @PostMapping("opozovi-uredjaje")
    @ResponseStatus(HttpStatus.OK)
    public OpozvanoInfo opozoviUredjaje(@PathVariable Long id) {
        return new OpozvanoInfo(servis.opozoviUredjaje(id));
    }

    @DeleteMapping("moodle")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void raskiniMoodle(@PathVariable Long id) {
        servis.raskiniMoodle(id);
    }
}
