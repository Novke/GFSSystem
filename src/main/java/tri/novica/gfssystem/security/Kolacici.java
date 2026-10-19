package tri.novica.gfssystem.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Kolačići prijave (spec 2.2): uvek {@code HttpOnly; Path=/}, {@code Secure} kad je spoljni zahtev HTTPS
 * ({@code request.isSecure()}, Tomcat čita {@code X-Forwarded-Proto}) ili kad je {@code SameSite=None} (pregledač ga
 * bez {@code Secure} odbija). Vrednost je token iz {@code TokenGenerator}, u bazi je samo njegov SHA-256.
 */
public final class Kolacici {

    public static final String NASTAVNIK = "gfs_nastavnik";
    public static final String STUDENT = "gfs_student";
    public static final String LTI = "gfs_lti";
    public static final String LTI_PAR = "gfs_lti_par";

    private static final Set<String> SAME_SITE = Set.of("Lax", "Strict", "None");

    private Kolacici() {}

    public static void postavi(HttpServletRequest req, HttpServletResponse res, String ime, String vrednost,
                               Duration trajanje, String sameSite) {
        dodaj(req, res, ime, vrednost, trajanje, sameSite);
    }

    /**
     * Briše kolačić ({@code Max-Age=0}) sa istim atributima kao pri postavljanju. {@code SameSite} se zna po imenu:
     * {@code gfs_lti} je {@code None}, svi ostali {@code Lax}.
     */
    public static void obrisi(HttpServletRequest req, HttpServletResponse res, String ime) {
        dodaj(req, res, ime, "", Duration.ZERO, LTI.equals(ime) ? "None" : "Lax");
    }

    public static Optional<String> procitaj(HttpServletRequest req, String ime) {
        Cookie[] kolacici = req.getCookies();
        if (kolacici == null) {
            return Optional.empty();
        }
        for (Cookie k : kolacici) {
            if (ime.equals(k.getName())) {
                String v = k.getValue();
                return v == null || v.isEmpty() ? Optional.empty() : Optional.of(v);
            }
        }
        return Optional.empty();
    }

    private static void dodaj(HttpServletRequest req, HttpServletResponse res, String ime, String vrednost,
                              Duration trajanje, String sameSite) {
        if (!SAME_SITE.contains(sameSite)) {
            throw new IllegalArgumentException("Neispravan SameSite: " + sameSite);
        }
        ResponseCookie kolacic = ResponseCookie.from(ime, vrednost)
                .httpOnly(true)
                .secure(req.isSecure() || "None".equals(sameSite))
                .path("/")
                .maxAge(trajanje)
                .sameSite(sameSite)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, kolacic.toString());
    }
}
