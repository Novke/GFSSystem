package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.onboarding.*;
import tri.novica.gfssystem.service.OnboardingService;

import java.util.List;

/** Nastavnički (privatni, iza basic-auth-a) deo onboardinga: sesije po grupi i obrada prijava. */
@RestController
@RequiredArgsConstructor
public class OnboardingRest {

    private final OnboardingService onboardingService;

    @PostMapping("/grupe/{grupaId}/onboarding")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public OnboardingSesijaInfo kreiraj(@PathVariable Long grupaId,
                                        @RequestBody(required = false) CreateOnboardingCmd cmd) {
        return onboardingService.kreiraj(grupaId, cmd);
    }

    @GetMapping("/grupe/{grupaId}/onboarding")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<OnboardingSesijaInfo> sesijeGrupe(@PathVariable Long grupaId) {
        return onboardingService.sesijeGrupe(grupaId);
    }

    @GetMapping("/onboarding/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public OnboardingSesijaDetails details(@PathVariable Long id) {
        return onboardingService.details(id);
    }

    @PatchMapping("/onboarding/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public OnboardingSesijaInfo promeniAktivnost(@PathVariable Long id, @RequestBody UpdateOnboardingCmd cmd) {
        return onboardingService.promeniAktivnost(id, cmd);
    }

    @PutMapping("/onboarding/{id}/prijave/{prijavaId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public PrijavaInfo izmeniPrijavu(@PathVariable Long id, @PathVariable Long prijavaId,
                                     @RequestBody UpdatePrijavaCmd cmd) {
        return onboardingService.izmeniPrijavu(id, prijavaId, cmd);
    }

    @PostMapping("/onboarding/{id}/prijave/{prijavaId}/prihvati")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public OnboardingSesijaDetails prihvati(@PathVariable Long id, @PathVariable Long prijavaId) {
        return onboardingService.prihvati(id, prijavaId);
    }

    @PostMapping("/onboarding/{id}/prijave/{prijavaId}/odbij")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public OnboardingSesijaDetails odbij(@PathVariable Long id, @PathVariable Long prijavaId,
                                         @RequestBody(required = false) OdbijPrijavuCmd cmd) {
        return onboardingService.odbij(id, prijavaId, cmd);
    }

    @PostMapping("/onboarding/{id}/prihvati-sve")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public OnboardingSesijaDetails prihvatiSve(@PathVariable Long id) {
        return onboardingService.prihvatiSve(id);
    }
}
