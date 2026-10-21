package tri.novica.gfssystem.security;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;

import java.util.Arrays;

/**
 * Nastavnička sesija za testove kroz pravi lanac (spec 14.2): prijava test lozinkom iz test {@code application.properties}
 * (korisnik {@code nastavnik}, lozinka {@code test-lozinka-123}, JAVNA, samo za testove) i {@code Origin} sa liste
 * {@code gfs.front.url} testa, da upisi prođu proveru porekla.
 */
public final class PrijavaTestUtil {

    public static final String KORISNIK = "nastavnik";
    public static final String LOZINKA = "test-lozinka-123";
    /** Prvi (i jedini) origin iz {@code gfs.front.url} u test svojstvima. */
    public static final String ORIGIN = "http://localhost:4200";
    /** IP sesija koje pravi ovaj pomoćnik (TEST-NET-1); uspešne prijave se ne broje u limit. */
    public static final String IP = "192.0.2.77";

    private PrijavaTestUtil() {}

    /** Prijavljuje nastavnika i vraća vrednost za zaglavlje {@code Cookie}: {@code gfs_nastavnik=<token>}. */
    public static String nastavnickiKolacic(NastavnikSesijaServis s) {
        return Kolacici.NASTAVNIK + "=" + s.prijavi(KORISNIK, LOZINKA, IP, "JUnit");
    }

    /** Dodaje kolačić (oblik {@code ime=vrednost}) i {@code Origin: http://localhost:4200}. */
    public static RequestPostProcessor kaoNastavnik(String kolacic) {
        int i = kolacic.indexOf('=');
        Cookie c = new Cookie(kolacic.substring(0, i), kolacic.substring(i + 1));
        return req -> {
            Cookie[] stari = req.getCookies();
            Cookie[] novi = stari == null ? new Cookie[]{c} : Arrays.copyOf(stari, stari.length + 1);
            novi[novi.length - 1] = c;
            req.setCookies(novi);
            req.addHeader(HttpHeaders.ORIGIN, ORIGIN);
            return req;
        };
    }
}
