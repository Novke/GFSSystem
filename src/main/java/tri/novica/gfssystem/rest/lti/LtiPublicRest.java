package tri.novica.gfssystem.rest.lti;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.lti.LtiPovezivanjeInfo;
import tri.novica.gfssystem.dto.lti.LtiPrijavaCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.lti.LtiPovezivanjeServis;
import tri.novica.gfssystem.service.lti.LtiTokServis;

/**
 * Moodle LTI 1.3 (spec 8.3), javno na internetu ({@code /api/public/lti/*}). {@code prijava} (OIDC login initiation) i
 * {@code start} (launch) su ANONIMNO i izuzeti iz provere porekla i CORS-a (stižu sa Moodle-a; štite ih {@code state},
 * {@code nonce} i kolačić {@code __Host-gfs_lti}); {@code povezivanje} je ANONIMNO (samo čita ime uz kolačić
 * {@code __Host-gfs_lti_par}), {@code povezi} je STUDENT (uz važeći {@code __Host-gfs_lti_par}). Odgovori se ne keširaju.
 */
@RestController
@RequestMapping("/public/lti")
@RequiredArgsConstructor
public class LtiPublicRest {

    private final LtiTokServis tok;
    private final LtiPovezivanjeServis povezivanja;

    /** OIDC login initiation ({@code GET} ili {@code POST} forma): 302 na auth URL platforme ili na stranicu greške. */
    @RequestMapping(path = "prijava", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseStatus(HttpStatus.FOUND)
    public ResponseEntity<Void> prijava(@RequestParam(name = "iss", required = false) String iss,
                                        @RequestParam(name = "login_hint", required = false) String loginHint,
                                        @RequestParam(name = "target_link_uri", required = false) String targetLinkUri,
                                        @RequestParam(name = "lti_message_hint", required = false) String messageHint,
                                        @RequestParam(name = "client_id", required = false) String clientId,
                                        @RequestParam(name = "lti_deployment_id", required = false) String deploymentId,
                                        HttpServletRequest req, HttpServletResponse res) {
        String location = tok.prijava(new LtiPrijavaCmd(iss, loginHint, targetLinkUri, messageHint, clientId,
                deploymentId), req, res);
        return preusmeri(HttpStatus.FOUND, location);
    }

    /** Launch ({@code form_post}: {@code id_token}, {@code state}): 303 sa relativnim {@code Location} na front. */
    @PostMapping("start")
    @ResponseStatus(HttpStatus.SEE_OTHER)
    public ResponseEntity<Void> start(@RequestParam(name = "id_token", required = false) String idToken,
                                      @RequestParam(name = "state", required = false) String state,
                                      HttpServletRequest req, HttpServletResponse res) {
        return preusmeri(HttpStatus.SEE_OTHER, tok.start(idToken, state, req, res));
    }

    /** Ime Moodle naloga za ekran povezivanja (R17); ne troši povezivanje. 410 bez važećeg {@code __Host-gfs_lti_par}. */
    @GetMapping("povezivanje")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<LtiPovezivanjeInfo> povezivanje(HttpServletRequest req) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(povezivanja.povezivanje(Kolacici.procitajSve(req, Kolacici.LTI_PAR)));
    }

    /** Upisuje vezu prijavljenog studenta sa Moodle nalogom iz {@code __Host-gfs_lti_par}: 204, 409 ili 410. */
    @PostMapping("povezi")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void povezi(HttpServletRequest req, HttpServletResponse res) {
        StudentAutentikacija s = StudentAutentikacija.trenutna()
                .orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
        povezivanja.povezi(s.studentId(), Kolacici.procitajSve(req, Kolacici.LTI_PAR));
        Kolacici.obrisi(req, res, Kolacici.LTI_PAR);
    }

    private static ResponseEntity<Void> preusmeri(HttpStatus status, String location) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.LOCATION, location)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
