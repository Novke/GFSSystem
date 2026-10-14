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
import tri.novica.gfssystem.utility.KlijentIp;

/**
 * Javni (neautentifikovani) deo onboardinga. nginx izuzima /api/public/ iz basic-auth-a; ovde ništa drugo ne sme.
 * {@code produces}: neprihvatljiv Accept dobija 406 pre poziva servisa, pa se prijava ne upiše uz odgovor sa greškom.
 */
@RestController
@RequestMapping(path = "/public/upis", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PublicUpisRest {

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
        return onboardingService.podnesi(token, cmd, KlijentIp.iz(request));
    }
}
