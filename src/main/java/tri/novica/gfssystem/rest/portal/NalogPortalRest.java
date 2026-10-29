package tri.novica.gfssystem.rest.portal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.portal.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.portal.IstorijaStudenta;
import tri.novica.gfssystem.service.portal.ObjavaOcenaServis;
import tri.novica.gfssystem.service.portal.PredmetiNaNalogu;
import tri.novica.gfssystem.service.portal.VezbanjeStudenta;

import java.util.List;

/**
 * Nalog studenta, faze 2 i 3 (spec studentskog dela 12.7), javno na internetu pod {@code /api/public/nalog/...}: sve putanje
 * su STUDENT u {@code PristupMatrica} (kolačić {@code gfs_student}). Student se uzima samo iz sesije; svaki id iz putanje ili
 * tela se proverava prema njegovim grupama, a tuđe i nepostojeće je isti 404 (spec 7.2). Open-in-view je isključen pod
 * {@code /public/nalog/**}, pa servisi grade DTO u svojoj transakciji.
 */
@RestController
@RequestMapping("/public/nalog")
@RequiredArgsConstructor
public class NalogPortalRest {

    private final PredmetiNaNalogu predmeti;
    private final VezbanjeStudenta vezbanje;
    private final IstorijaStudenta istorija;
    private final ObjavaOcenaServis ocene;

    @GetMapping("predmeti")
    @ResponseStatus(HttpStatus.OK)
    public List<PredmetStudenta> predmeti() {
        return predmeti.predmeti(student());
    }

    @GetMapping("predmeti/{id}/prisustvo")
    @ResponseStatus(HttpStatus.OK)
    public PrisustvoStudenta prisustvo(@PathVariable Long id) {
        return predmeti.prisustvo(student(), id);
    }

    @GetMapping("predmeti/{id}/vezbanje")
    @ResponseStatus(HttpStatus.OK)
    public List<TemaVezbanja> vezbanje(@PathVariable Long id) {
        return predmeti.vezbanje(student(), id);
    }

    @GetMapping("predmeti/{id}/moji-zadaci")
    @ResponseStatus(HttpStatus.OK)
    public List<MojZadatak> mojiZadaci(@PathVariable Long id) {
        return istorija.mojiZadaci(student(), id);
    }

    @GetMapping("predmeti/{id}/prezentacije")
    @ResponseStatus(HttpStatus.OK)
    public List<PrezentacijaStudenta> prezentacije(@PathVariable Long id) {
        return istorija.prezentacije(student(), id);
    }

    @GetMapping("predmeti/{id}/ocena")
    @ResponseStatus(HttpStatus.OK)
    public OcenaStudenta ocena(@PathVariable Long id) {
        return ocene.zaStudenta(student(), id);
    }

    @GetMapping("prezentacije/{id}")
    @ResponseStatus(HttpStatus.OK)
    public PregledPrezentacije pregled(@PathVariable Long id) {
        return istorija.pregled(student(), id);
    }

    @PostMapping("vezbanje")
    @ResponseStatus(HttpStatus.OK)
    public ZadatakVezbanja noviZadatak(@Valid @RequestBody NoviZadatakCmd cmd) {
        return vezbanje.novi(student(), cmd);
    }

    @PostMapping("vezbanje/{pokusajId}/provera")
    @ResponseStatus(HttpStatus.OK)
    public ProveraVezbanja provera(@PathVariable Long pokusajId, @RequestBody ProveraCmd cmd) {
        return vezbanje.provera(student(), pokusajId, cmd);
    }

    @PostMapping("vezbanje/{pokusajId}/resenje")
    @ResponseStatus(HttpStatus.OK)
    public ResenjeVezbanja resenje(@PathVariable Long pokusajId) {
        return vezbanje.resenje(student(), pokusajId);
    }

    /** STUDENT putanje: lanac je već tražio studentsku sesiju, pa je ovo uvek prisutno. */
    private static long student() {
        return StudentAutentikacija.trenutna().map(StudentAutentikacija::studentId)
                .orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
