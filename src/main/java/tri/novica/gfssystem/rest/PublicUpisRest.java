package tri.novica.gfssystem.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.onboarding.JavniUpisInfo;
import tri.novica.gfssystem.dto.onboarding.PodnesiPrijavuCmd;
import tri.novica.gfssystem.dto.onboarding.PodnetaPrijavaInfo;
import tri.novica.gfssystem.service.OnboardingService;

/**
 * Javni (neautentifikovani) deo onboardinga. nginx izuzima /api/public/ iz basic-auth-a; ovde ništa drugo ne sme.
 * {@code produces}: neprihvatljiv Accept dobija 406 pre poziva servisa, pa se prijava ne upiše uz odgovor sa greškom.
 */
@RestController
@RequestMapping(path = "/public/upis", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PublicUpisRest {

    /** Dovoljno za IPv6 u tekstualnom obliku; zaglavlje šalje klijent, pa u log ne ide neograničeno. */
    private static final int MAX_IP = 64;

    private final OnboardingService onboardingService;

    @GetMapping("{token}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public JavniUpisInfo info(@PathVariable String token) {
        return onboardingService.javniInfo(token);
    }

    @PostMapping("{token}")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public PodnetaPrijavaInfo podnesi(@PathVariable String token, @RequestBody PodnesiPrijavuCmd cmd,
                                      HttpServletRequest request) {
        return onboardingService.podnesi(token, cmd, klijentIp(request));
    }

    /** Backend je iza dva nginx-a: prvi element X-Forwarded-For je klijent. Samo za log. */
    private static String klijentIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String ip = xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : request.getRemoteAddr();
        return ip != null && ip.length() > MAX_IP ? ip.substring(0, MAX_IP) : ip;
    }
}
