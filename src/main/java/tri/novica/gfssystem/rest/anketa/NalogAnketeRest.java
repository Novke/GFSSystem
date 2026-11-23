package tri.novica.gfssystem.rest.anketa;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.anketa.AnketaStudentu;
import tri.novica.gfssystem.dto.anketa.GlasCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.anketa.AnketaService;

import java.util.List;

/**
 * Brza anketa na telefonu studenta (spec brze ankete 5, STUDENT u {@code PristupMatrica}, javno na internetu kao
 * {@code /api/public/nalog/ankete*}): skup anketa (isti kao {@code CasStanje.ankete}, za telefon u prezentaciji) i glas.
 * Student se uzima samo iz sesije.
 */
@RestController
@RequestMapping(path = "/public/nalog/ankete", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class NalogAnketeRest {

    private final AnketaService ankete;

    @GetMapping
    public List<AnketaStudentu> ankete() {
        return ankete.zaStudenta(student());
    }

    /** Glas: 404 nepostojeća, 403 nije za tebe, 409 zatvorena, 400 neispravan izbor. */
    @PostMapping("{id}/glas")
    public AnketaStudentu glas(@PathVariable Long id, @RequestBody GlasCmd cmd) {
        return ankete.glasaj(student(), id, cmd);
    }

    /** STUDENT putanje: lanac je već tražio uređaj, pa je ovo uvek prisutno. */
    private static long student() {
        return StudentAutentikacija.trenutna().map(StudentAutentikacija::studentId)
                .orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
