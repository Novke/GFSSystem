package tri.novica.gfssystem.rest.fajlovi;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tri.novica.gfssystem.dto.fajlovi.FajloviCasa;
import tri.novica.gfssystem.service.fajlovi.FajloviCasaServis;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.KlijentIp;

/**
 * Fajlovi na času po kratkom kodu, bez prijave (spec fajlova 5): {@code GET /public/fajlovi/{kod}} (spisak, {@code no-store})
 * i {@code GET /public/fajlovi/{kod}/{fajlId}} (preuzimanje, zaglavlja iz {@link Preuzimanje}). Ograničenje pogrešnih
 * kodova i pravilo spiska su u {@link FajloviCasaServis}; kontroler vraća samo DTO napravljen u servisu (van open-in-view).
 */
@RestController
@RequestMapping("/public/fajlovi")
@RequiredArgsConstructor
public class PublicFajloviRest {

    private final FajloviCasaServis servis;
    private final MedijService medijService;

    @GetMapping(path = "{kod}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FajloviCasa> spisak(@PathVariable String kod, HttpServletRequest zahtev) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(servis.spisak(kod, KlijentIp.iz(zahtev)));
    }

    @GetMapping("{kod}/{fajlId}")
    public ResponseEntity<Resource> preuzmi(@PathVariable String kod, @PathVariable Long fajlId, HttpServletRequest zahtev) {
        FajloviCasaServis.Preuzet p = servis.preuzmi(kod, fajlId, KlijentIp.iz(zahtev));
        return Preuzimanje.odgovor(p.naziv(), medijService.ucitaj(p.medijId()));
    }
}
