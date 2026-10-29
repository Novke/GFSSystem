package tri.novica.gfssystem.rest.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.portal.StatistikaTipa;
import tri.novica.gfssystem.service.portal.StatistikaVezbanja;

import java.time.LocalDate;
import java.util.List;

/** Statistika vežbanja po tipu za predmet (nastavnik, ADMIN; spec 5.6): samo zbirno. */
@RestController
@RequiredArgsConstructor
public class StatistikaVezbanjaRest {

    private final StatistikaVezbanja statistika;

    @GetMapping("/predmeti/{id}/vezbanje/statistika")
    @ResponseStatus(HttpStatus.OK)
    public List<StatistikaTipa> statistika(@PathVariable Long id, @RequestParam(required = false) Long grupa,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate od,
                                           @RequestParam(name = "do", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate doDana) {
        return statistika.statistika(id, grupa, od, doDana);
    }
}
