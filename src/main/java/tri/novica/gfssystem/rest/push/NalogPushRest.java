package tri.novica.gfssystem.rest.push;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.push.PushPretplataCmd;
import tri.novica.gfssystem.dto.push.PushStanjeInfo;
import tri.novica.gfssystem.dto.push.ZvonoInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.push.PushPretplate;
import tri.novica.gfssystem.service.push.PushPretplate.Vlasnik;
import tri.novica.gfssystem.service.push.StudentskaObavestenja;

/**
 * Push i zvono studenta (spec aplikacije 5.4 i 6.8), javno pod {@code /api/public/nalog/...}: sve putanje su STUDENT u
 * {@code PristupMatrica} (kolačić {@code gfs_student}). Uređaj i student se uzimaju samo iz sesije; nijedan endpoint ne
 * prima id uređaja ni pretplate. Open-in-view je isključen pod {@code /public/nalog/**}, pa servisi grade DTO u svojoj
 * transakciji.
 */
@RestController
@RequestMapping("/public/nalog")
@RequiredArgsConstructor
public class NalogPushRest {

    private final PushPretplate pretplate;
    private final StudentskaObavestenja obavestenja;

    @GetMapping("push")
    @ResponseStatus(HttpStatus.OK)
    public PushStanjeInfo stanje() {
        return pretplate.stanje(uredjaj());
    }

    @PutMapping("push")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pretplati(@RequestBody PushPretplataCmd cmd) {
        pretplate.pretplati(uredjaj(), cmd);
    }

    @DeleteMapping("push")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odjavi() {
        pretplate.odjavi(uredjaj());
    }

    @PostMapping("push/proba")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void proba() {
        pretplate.proba(uredjaj());
    }

    @GetMapping("obavestenja")
    @ResponseStatus(HttpStatus.OK)
    public ZvonoInfo zvono() {
        return obavestenja.zvono(student().studentId());
    }

    @PostMapping("obavestenja/otvoreno")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void otvoreno() {
        obavestenja.otvoreno(student().studentId());
    }

    /** STUDENT putanje: lanac je već tražio studentsku sesiju, pa je ovo uvek prisutno. */
    private static StudentAutentikacija student() {
        return StudentAutentikacija.trenutna().orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }

    private static Vlasnik uredjaj() {
        StudentAutentikacija s = student();
        if (s.uredjajId() == null) {
            throw new SystemException("Potrebna je prijava.", 401);
        }
        return Vlasnik.uredjaj(s.uredjajId());
    }
}
