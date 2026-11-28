package tri.novica.gfssystem.rest.test;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.test.online.*;
import tri.novica.gfssystem.service.test.OnlineTestService;
import tri.novica.gfssystem.service.test.RezultatiOnlineTesta;

/**
 * Online test, nastavnička strana (plan 4.2-4.4): pravljenje, priprema, tok, konzola, rezultati, ispravke ključa i
 * objava. Sve je ADMIN u {@code PristupMatrica}; ništa nije pod {@code /public/**}.
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class OnlineTestRest {

    private final OnlineTestService service;
    private final RezultatiOnlineTesta rezultati;

    // ---------------------------------------------------------------- pravljenje i priprema

    @GetMapping("/online/kandidati-studenti")
    @ResponseStatus(HttpStatus.OK)
    public KandidatiStudenti kandidatiStudenti(@RequestParam Long grupaId, @RequestParam Long predmetId) {
        return service.kandidatiStudenti(grupaId, predmetId);
    }

    @PostMapping("/online")
    @ResponseStatus(HttpStatus.CREATED)
    public OnlineTest kreiraj(@RequestBody NoviOnlineTestCmd cmd) {
        return service.kreiraj(cmd);
    }

    @GetMapping("/{id}/online")
    @ResponseStatus(HttpStatus.OK)
    public OnlineTest online(@PathVariable Long id) {
        return service.online(id);
    }

    @PostMapping("/{id}/online/izvuci")
    @ResponseStatus(HttpStatus.OK)
    public OnlineTest izvuci(@PathVariable Long id, @RequestBody(required = false) IzvuciCmd cmd) {
        return service.izvuci(id, cmd);
    }

    @PostMapping("/{id}/online/studenti")
    @ResponseStatus(HttpStatus.OK)
    public OnlineTest dodajStudenta(@PathVariable Long id, @RequestBody StudentIdCmd cmd) {
        return service.dodajStudenta(id, cmd);
    }

    @DeleteMapping("/{id}/online/studenti/{studentId}")
    @ResponseStatus(HttpStatus.OK)
    public OnlineTest ukloniStudenta(@PathVariable Long id, @PathVariable Long studentId) {
        return service.ukloniStudenta(id, studentId);
    }

    // ---------------------------------------------------------------- tok i konzola

    @PostMapping("/{id}/online/otvori-ulaz")
    @ResponseStatus(HttpStatus.OK)
    public OnlineTest otvoriUlaz(@PathVariable Long id, @RequestBody(required = false) OtvoriUlazCmd cmd) {
        return service.otvoriUlaz(id, cmd);
    }

    @PostMapping("/{id}/online/pokreni")
    @ResponseStatus(HttpStatus.OK)
    public OnlineTest pokreni(@PathVariable Long id) {
        return service.pokreni(id);
    }

    @PostMapping("/{id}/online/zavrsi")
    @ResponseStatus(HttpStatus.OK)
    public OnlineTest zavrsi(@PathVariable Long id) {
        return service.zavrsi(id);
    }

    @PostMapping("/{id}/online/produzi")
    @ResponseStatus(HttpStatus.OK)
    public KonzolaTesta produzi(@PathVariable Long id, @RequestBody ProduziCmd cmd) {
        return service.produzi(id, cmd);
    }

    @PostMapping("/{id}/online/primerci/{pid}/vrati")
    @ResponseStatus(HttpStatus.OK)
    public KonzolaTesta vrati(@PathVariable Long id, @PathVariable Long pid) {
        return service.vrati(id, pid);
    }

    @PostMapping("/{id}/online/primerci/{pid}/ponisti")
    @ResponseStatus(HttpStatus.OK)
    public KonzolaTesta ponisti(@PathVariable Long id, @PathVariable Long pid,
                                @RequestBody(required = false) NapomenaCmd cmd) {
        return service.ponisti(id, pid, cmd);
    }

    @PatchMapping("/{id}/online/primerci/{pid}/prepisivao")
    @ResponseStatus(HttpStatus.OK)
    public KonzolaTesta prepisivao(@PathVariable Long id, @PathVariable Long pid, @RequestBody PrepisivaoCmd cmd) {
        return service.prepisivao(id, pid, cmd);
    }

    @GetMapping("/{id}/online/konzola")
    @ResponseStatus(HttpStatus.OK)
    public KonzolaTesta konzola(@PathVariable Long id) {
        return service.konzola(id);
    }

    // ---------------------------------------------------------------- rezultati i ispravke

    @GetMapping("/{id}/online/rezultati")
    @ResponseStatus(HttpStatus.OK)
    public RezultatiTesta rezultati(@PathVariable Long id) {
        return rezultati.rezultati(id);
    }

    @GetMapping("/{id}/online/primerci/{pid}")
    @ResponseStatus(HttpStatus.OK)
    public PrimerakNastavnika primerak(@PathVariable Long id, @PathVariable Long pid) {
        return rezultati.primerak(id, pid);
    }

    @PutMapping("/{id}/online/zadaci/{zid}/rucni-poeni")
    @ResponseStatus(HttpStatus.OK)
    public PrimerakNastavnika rucniPoeni(@PathVariable Long id, @PathVariable Long zid, @RequestBody RucniPoeniCmd cmd) {
        return rezultati.rucniPoeni(id, zid, cmd);
    }

    @GetMapping("/{id}/online/odgovori")
    @ResponseStatus(HttpStatus.OK)
    public OdgovoriTesta odgovori(@PathVariable Long id) {
        return rezultati.odgovori(id);
    }

    @PostMapping("/{id}/online/ispravke")
    @ResponseStatus(HttpStatus.OK)
    public OdgovoriTesta ispravi(@PathVariable Long id, @RequestBody IspravkaCmd cmd) {
        return rezultati.ispravi(id, cmd);
    }

    @DeleteMapping("/{id}/online/ispravke/{iid}")
    @ResponseStatus(HttpStatus.OK)
    public OdgovoriTesta vratiIspravku(@PathVariable Long id, @PathVariable Long iid) {
        return rezultati.vratiIspravku(id, iid);
    }

    // ---------------------------------------------------------------- objava (svaki test, i na papiru)

    @PostMapping("/{id}/objava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void objavi(@PathVariable Long id, @RequestBody(required = false) ObjavaCmd cmd) {
        rezultati.objavi(id, cmd);
    }

    @DeleteMapping("/{id}/objava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void povuciObjavu(@PathVariable Long id) {
        rezultati.povuciObjavu(id);
    }
}
