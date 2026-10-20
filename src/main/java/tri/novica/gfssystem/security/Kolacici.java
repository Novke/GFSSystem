package tri.novica.gfssystem.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Kolačići prijave (spec 2.2): uvek {@code HttpOnly; Path=/}, {@code Secure} kad je spoljni zahtev HTTPS
 * ({@code request.isSecure()}, Tomcat čita {@code X-Forwarded-Proto}), kad je {@code SameSite=None} (pregledač ga
 * bez {@code Secure} odbija) ili kad ime ima prefiks {@code __Host-} ({@link #LTI}, {@link #LTI_PAR}: prefiks traži
 * {@code Secure}, {@code Path=/} i nijedan {@code Domain}, pa sestrinski {@code *.trif.rs} host ne može da podmetne
 * kolačić istog imena). Vrednost je token iz {@code TokenGenerator}, u bazi je samo njegov SHA-256.
 */
public final class Kolacici {

    public static final String NASTAVNIK = "gfs_nastavnik";
    public static final String STUDENT = "gfs_student";
    public static final String LTI = "__Host-gfs_lti";
    public static final String LTI_PAR = "__Host-gfs_lti_par";

    /** Najviše toliko istoimenih vrednosti čita {@link #procitajSve} (svaka košta jedan upit u bazu). */
    public static final int MAX_ISTOIMENIH = 5;

    private static final Set<String> SAME_SITE = Set.of("Lax", "Strict", "None");

    private Kolacici() {}

    public static void postavi(HttpServletRequest req, HttpServletResponse res, String ime, String vrednost,
                               Duration trajanje, String sameSite) {
        dodaj(req, res, ime, vrednost, trajanje, sameSite);
    }

    /**
     * Briše kolačić ({@code Max-Age=0}) sa istim atributima kao pri postavljanju. {@code SameSite} se zna po imenu:
     * {@code __Host-gfs_lti} je {@code None}, svi ostali (i {@code __Host-gfs_lti_par}) {@code Lax}; {@code __Host-}
     * imena se brišu sa {@code Secure} i {@code Path=/} kao i pri postavljanju (inače pregledač brisanje odbija).
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

    /**
     * Sve neprazne vrednosti kolačića {@code ime}, redom kojim ih je pregledač poslao (najviše {@link #MAX_ISTOIMENIH}).
     * Sestrinski poddomen ({@code *.trif.rs}) može da podmetne kolačić istog imena za {@code Domain=.trif.rs} (cookie
     * tossing), a pregledač šalje oba; zato se za prijavu proba svaka vrednost, a ne samo prva.
     */
    public static List<String> procitajSve(HttpServletRequest req, String ime) {
        List<String> vrednosti = new ArrayList<>();
        Cookie[] kolacici = req.getCookies();
        if (kolacici == null) {
            return vrednosti;
        }
        for (Cookie k : kolacici) {
            if (ime.equals(k.getName()) && k.getValue() != null && !k.getValue().isEmpty()) {
                vrednosti.add(k.getValue());
                if (vrednosti.size() == MAX_ISTOIMENIH) {
                    break;
                }
            }
        }
        return vrednosti;
    }

    /** Broj nepraznih vrednosti kolačića {@code ime}, bez gornje granice (da se vidi da li je {@link #procitajSve} skratio). */
    public static int brojVrednosti(HttpServletRequest req, String ime) {
        Cookie[] kolacici = req.getCookies();
        if (kolacici == null) {
            return 0;
        }
        int n = 0;
        for (Cookie k : kolacici) {
            if (ime.equals(k.getName()) && k.getValue() != null && !k.getValue().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static void dodaj(HttpServletRequest req, HttpServletResponse res, String ime, String vrednost,
                              Duration trajanje, String sameSite) {
        if (!SAME_SITE.contains(sameSite)) {
            throw new IllegalArgumentException("Neispravan SameSite: " + sameSite);
        }
        ResponseCookie kolacic = ResponseCookie.from(ime, vrednost)
                .httpOnly(true)
                .secure(req.isSecure() || "None".equals(sameSite) || ime.startsWith("__Host-"))
                .path("/")
                .maxAge(trajanje)
                .sameSite(sameSite)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, kolacic.toString());
    }
}
