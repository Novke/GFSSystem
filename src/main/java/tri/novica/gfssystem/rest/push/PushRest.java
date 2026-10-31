package tri.novica.gfssystem.rest.push;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.push.PushPodesavanjaInfo;
import tri.novica.gfssystem.dto.push.PushPretplataCmd;
import tri.novica.gfssystem.dto.push.PushStanjeInfo;
import tri.novica.gfssystem.dto.push.PushVrstaCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.NastavnikAutentikacija;
import tri.novica.gfssystem.service.push.PushNastavniku;
import tri.novica.gfssystem.service.push.PushPretplate;
import tri.novica.gfssystem.service.push.PushPretplate.Vlasnik;

/**
 * Push nastavnika (spec aplikacije 5.4 i 6.6): pretplata pripada nastavničkoj sesiji iz zahteva ({@code gfs_nastavnik}),
 * nikad id-ju iz zahteva; sve putanje su ADMIN (podrazumevano u {@code PristupMatrica}).
 */
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushRest {

    private final PushPretplate pretplate;
    private final PushNastavniku nastavniku;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public PushStanjeInfo stanje() {
        return pretplate.stanje(sesija());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pretplati(@RequestBody PushPretplataCmd cmd) {
        pretplate.pretplati(sesija(), cmd);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odjavi() {
        pretplate.odjavi(sesija());
    }

    @PostMapping("proba")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void proba() {
        pretplate.proba(sesija());
    }

    @GetMapping("podesavanja")
    @ResponseStatus(HttpStatus.OK)
    public PushPodesavanjaInfo podesavanja() {
        return nastavniku.podesavanja();
    }

    @PutMapping("podesavanja")
    @ResponseStatus(HttpStatus.OK)
    public PushPodesavanjaInfo izmeni(@RequestBody PushVrstaCmd cmd) {
        return nastavniku.izmeni(cmd);
    }

    /** ADMIN putanje: lanac je već tražio nastavničku sesiju. */
    private static Vlasnik sesija() {
        return Vlasnik.sesija(NastavnikAutentikacija.trenutna().map(NastavnikAutentikacija::sesijaId)
                .orElseThrow(() -> new SystemException("Potrebna je prijava.", 401)));
    }
}
