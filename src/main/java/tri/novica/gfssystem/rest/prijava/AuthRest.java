package tri.novica.gfssystem.rest.prijava;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.config.NastavnikSvojstva;
import tri.novica.gfssystem.dto.prijava.NastavnikJaInfo;
import tri.novica.gfssystem.dto.prijava.PrijavaNastavnikaCmd;
import tri.novica.gfssystem.dto.prijava.SesijaInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.NastavnikAutentikacija;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.utility.KlijentIp;

import java.util.List;
import java.util.Map;

/**
 * Sesija nastavnika (spec 4.2). Namerno van {@code /public}: {@code prijava} i {@code ja} su ANONIMNO u
 * {@code PristupMatrica} ({@code ja} bez sesije vraća 401 ovde), sve ostalo je ADMIN (lanac traži sesiju).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthRest {

    static final String POTREBNA_PRIJAVA = "Potrebna je prijava.";

    private final NastavnikSesijaServis servis;
    private final NastavnikSvojstva svojstva;

    @PostMapping("prijava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void prijava(@Valid @RequestBody PrijavaNastavnikaCmd cmd, HttpServletRequest req, HttpServletResponse res) {
        String token = servis.prijavi(cmd.korisnik(), cmd.lozinka(), KlijentIp.iz(req),
                req.getHeader(HttpHeaders.USER_AGENT));
        Kolacici.postavi(req, res, Kolacici.NASTAVNIK, token, NastavnikSesijaServis.TRAJANJE, "Lax");
    }

    @GetMapping("ja")
    @ResponseStatus(HttpStatus.OK)
    public NastavnikJaInfo ja() {
        NastavnikAutentikacija.trenutna().orElseThrow(() -> new SystemException(POTREBNA_PRIJAVA, 401));
        return new NastavnikJaInfo("NASTAVNIK", svojstva.korisnik());
    }

    @PostMapping("odjava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odjava(HttpServletRequest req, HttpServletResponse res) {
        Kolacici.procitaj(req, Kolacici.NASTAVNIK).ifPresent(servis::odjavi);
        Kolacici.obrisi(req, res, Kolacici.NASTAVNIK);
    }

    @GetMapping("sesije")
    @ResponseStatus(HttpStatus.OK)
    public List<SesijaInfo> sesije() {
        return servis.sesije(trenutna().sesijaId());
    }

    /** Opoziv jednog uređaja; kad je to ovaj uređaj, briše se i njegov kolačić. */
    @DeleteMapping("sesije/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void opozovi(@PathVariable Long id, HttpServletRequest req, HttpServletResponse res) {
        servis.opozovi(id);
        if (id.equals(trenutna().sesijaId())) {
            Kolacici.obrisi(req, res, Kolacici.NASTAVNIK);
        }
    }

    /** Odjava svih uređaja osim ovog. */
    @PostMapping("sesije/odjavi-sve")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Integer> odjaviSve() {
        return Map.of("odjavljeno", servis.odjaviSveOsim(trenutna().sesijaId()));
    }

    /** ADMIN putanje: lanac je već tražio sesiju, pa je ovo uvek prisutno (401 je samo mreža). */
    private static NastavnikAutentikacija trenutna() {
        return NastavnikAutentikacija.trenutna().orElseThrow(() -> new SystemException(POTREBNA_PRIJAVA, 401));
    }
}
