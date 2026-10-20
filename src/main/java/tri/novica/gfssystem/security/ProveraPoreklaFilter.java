package tri.novica.gfssystem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tri.novica.gfssystem.utility.KlijentIp;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Zaštita od CSRF-a proverom porekla (spec 2.4) umesto tokena. Za POST/PUT/PATCH/DELETE: kad pregledač pošalje
 * {@code Sec-Fetch-Site}, prolaze samo {@code same-origin} i {@code none} (ručno otkucan zahtev); bez njega
 * {@code Origin} mora biti tačno jedan sa liste {@code gfs.front.url}; bez oba zaglavlja zahtev se odbija.
 * {@code SameSite=Lax} ne bi bio dovoljan: on je po sajtu ({@code trif.rs}), a sestrinski poddomeni ({@code cvgen.trif.rs},
 * {@code *.dev.trif.rs}) su isti sajt. Izuzeti su LTI login i launch (POST stiže sa Moodle-a, štiti ih {@code state}
 * i {@code nonce}); MCP ima svoj lanac bez ovog filtera. Nije Spring bean: lanac ga pravi sam.
 */
@Slf4j
public class ProveraPoreklaFilter extends OncePerRequestFilter {

    /** {@code /public/lti/prijava} i {@code /public/lti/start} (sve metode). */
    public static final RequestMatcher LTI_IZUZECI = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/public/lti/prijava"),
            PathPatternRequestMatcher.withDefaults().matcher("/public/lti/start"));

    static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final Set<String> UPISI = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> DOZVOLJEN_SEC_FETCH_SITE = Set.of("same-origin", "none");
    static final String PORUKA = "Zahtev nije dozvoljen.";

    private final Set<String> dozvoljeniOrigini;
    private final RequestMatcher izuzeci;

    public ProveraPoreklaFilter(List<String> dozvoljeniOrigini, RequestMatcher izuzeci) {
        this.dozvoljeniOrigini = Set.copyOf(dozvoljeniOrigini);
        this.izuzeci = Objects.requireNonNull(izuzeci);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain lanac)
            throws ServletException, IOException {
        if (!UPISI.contains(req.getMethod()) || izuzeci.matches(req) || dozvoljen(req)) {
            lanac.doFilter(req, res);
            return;
        }
        // bez putanje u logu: javne putanje nose tokene (npr. /public/upis/{token})
        log.info("Zahtev odbijen proverom porekla: metod={}, imaSecFetchSite={}, imaOrigin={}, ip={}",
                req.getMethod(), req.getHeader(SEC_FETCH_SITE) != null, req.getHeader(HttpHeaders.ORIGIN) != null,
                KlijentIp.iz(req));
        JsonGreske.posalji(res, HttpServletResponse.SC_FORBIDDEN, PORUKA);
    }

    private boolean dozvoljen(HttpServletRequest req) {
        String sajt = req.getHeader(SEC_FETCH_SITE);
        if (sajt != null) {
            return DOZVOLJEN_SEC_FETCH_SITE.contains(sajt.trim());
        }
        String origin = req.getHeader(HttpHeaders.ORIGIN);
        return origin != null && dozvoljeniOrigini.contains(origin);
    }
}
