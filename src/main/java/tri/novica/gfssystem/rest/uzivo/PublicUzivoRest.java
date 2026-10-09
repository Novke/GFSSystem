package tri.novica.gfssystem.rest.uzivo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.uzivo.JavnoIzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.PrijavaUcesnikaCmd;
import tri.novica.gfssystem.dto.uzivo.UcesnikInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.UcesnikService;
import tri.novica.gfssystem.utility.KlijentIp;

import java.time.Duration;

/**
 * Javni (neautentifikovani) ulaz u izvođenje uživo: naziv po kodu, prijava imenom (postavlja kolačić) i provera
 * kolačića. nginx izuzima /api/public/ iz basic-auth-a; ovde ništa osim ovoga. {@code produces}: neprihvatljiv Accept
 * dobija 406 pre poziva servisa.
 */
@RestController
@RequestMapping(path = "/public/uzivo", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class PublicUzivoRest {

    /** Kolačić sa tokenom učesnika (32 znaka; u bazi samo SHA-256). */
    public static final String KOLACIC = "gfs_uzivo";
    static final Duration TRAJANJE_KOLACICA = Duration.ofHours(12);
    static final String NISI_PRIJAVLJEN = "Nisi prijavljen.";

    private final UcesnikService ucesnikService;

    @GetMapping("{kod}")
    @ResponseStatus(HttpStatus.OK)
    public JavnoIzvodjenjeInfo info(@PathVariable String kod) {
        return ucesnikService.info(kod);
    }

    @GetMapping("{kod}/ja")
    @ResponseStatus(HttpStatus.OK)
    public UcesnikInfo ja(@PathVariable String kod, @CookieValue(name = KOLACIC, required = false) String token) {
        if (token == null) {
            throw new SystemException(NISI_PRIJAVLJEN, HttpStatus.NOT_FOUND);
        }
        return ucesnikService.ja(kod, token)
                .orElseThrow(() -> new SystemException(NISI_PRIJAVLJEN, HttpStatus.NOT_FOUND));
    }

    /**
     * Nova prijava (i sa telefona koji već ima kolačić drugog izvođenja: kolačić se prepisuje). {@code Secure} kad je
     * spoljni zahtev bio HTTPS (nginx šalje {@code X-Forwarded-Proto}). U log idu id-jevi i IP, ne ime.
     */
    @PostMapping("{kod}/prijava")
    @ResponseStatus(HttpStatus.CREATED)
    public UcesnikInfo prijava(@PathVariable String kod, @RequestBody PrijavaUcesnikaCmd cmd,
                               HttpServletRequest request, HttpServletResponse response) {
        UcesnikService.Prijavljen p = ucesnikService.prijavi(kod, cmd.ime());
        ResponseCookie kolacic = ResponseCookie.from(KOLACIC, p.token())
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(TRAJANJE_KOLACICA)
                .secure("https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, kolacic.toString());
        log.info("Uživo prijava: izvodjenje={}, ucesnik={}, ip={}", p.info().izvodjenjeId(), p.info().ucesnikId(),
                KlijentIp.iz(request));
        return p.info();
    }
}
