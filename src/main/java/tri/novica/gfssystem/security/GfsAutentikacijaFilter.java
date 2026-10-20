package tri.novica.gfssystem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tri.novica.gfssystem.entity.prijava.NastavnickaSesija;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.utility.KlijentIp;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Identitet zahteva iz naših kolačića (spec 2.2), pre pravila pristupa: važeći {@code gfs_nastavnik} daje
 * {@link NastavnikAutentikacija} ({@code ROLE_ADMIN}); inače važeći {@code gfs_student} daje {@link StudentAutentikacija}
 * ({@code ROLE_STUDENT}, id studenta i uređaja). Nevažeći, istekao ili opozvan token: zahtev ide dalje anoniman, a
 * kolačić se briše u odgovoru. Kad se sesija ili uređaj produži (najviše jednom na 10 min), kolačić dobija nov
 * {@code Max-Age}.
 * <p>
 * <b>Cookie tossing:</b> sestrinski poddomen ({@code *.trif.rs}) može da podmetne kolačić istog imena za
 * {@code Domain=.trif.rs}, pa pregledač šalje dva. Zato se proba svaka vrednost ({@link Kolacici#procitajSve}) i važi prva
 * ispravna; kolačić (host-only, jedini koji ovde možemo da obrišemo) se briše samo kad nijedna vrednost ne važi.
 * <p>
 * Grane su redom ({@link #autentikuj}): prva koja vrati identitet odlučuje. Nije Spring bean (lanac ga pravi sam, da ga
 * Boot ne registruje još jednom kao servlet filter).
 */
@Slf4j
public class GfsAutentikacijaFilter extends OncePerRequestFilter {

    private final NastavnikSesijaServis nastavnici;
    private final StudentskaPrijava studenti;
    private final Clock clock;

    public GfsAutentikacijaFilter(NastavnikSesijaServis nastavnici, StudentskaPrijava studenti, Clock clock) {
        this.nastavnici = Objects.requireNonNull(nastavnici);
        this.studenti = Objects.requireNonNull(studenti);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain lanac)
            throws ServletException, IOException {
        Authentication a = autentikuj(req, res);
        if (a != null) {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(a);
            SecurityContextHolder.setContext(ctx);
        }
        lanac.doFilter(req, res);
    }

    /** Prva grana koja prepozna kolačić; {@code null} = anoniman zahtev. */
    private Authentication autentikuj(HttpServletRequest req, HttpServletResponse res) {
        Authentication nastavnik = nastavnik(req, res);
        return nastavnik != null ? nastavnik : student(req, res);
    }

    private Authentication nastavnik(HttpServletRequest req, HttpServletResponse res) {
        List<String> tokeni = Kolacici.procitajSve(req, Kolacici.NASTAVNIK);
        LocalDateTime pre = LocalDateTime.now(clock);
        for (String token : tokeni) {
            Optional<NastavnickaSesija> sesija = nastavnici.potvrdi(token);
            if (sesija.isPresent()) {
                NastavnickaSesija s = sesija.get();
                // potvrdi je upravo pomerio poslednji ulaz (produženje): i kolačić dobija pun Max-Age
                if (!s.getPoslednjiUlaz().isBefore(pre) && smeDaPostaviKolacic(req)) {
                    Kolacici.postavi(req, res, Kolacici.NASTAVNIK, token, NastavnikSesijaServis.TRAJANJE, "Lax");
                }
                return new NastavnikAutentikacija(s.getId());
            }
        }
        obrisiAkoNijedanNeVazi(req, res, Kolacici.NASTAVNIK, tokeni);
        return null;
    }

    private Authentication student(HttpServletRequest req, HttpServletResponse res) {
        List<String> tokeni = Kolacici.procitajSve(req, Kolacici.STUDENT);
        LocalDateTime pre = LocalDateTime.now(clock);
        for (String token : tokeni) {
            Optional<StudentskiUredjaj> uredjaj = studenti.potvrdi(token);
            if (uredjaj.isPresent()) {
                StudentskiUredjaj u = uredjaj.get();
                if (!u.getPoslednjiUlaz().isBefore(pre) && smeDaPostaviKolacic(req)) {
                    Kolacici.postavi(req, res, Kolacici.STUDENT, token, studenti.trajanje(u.getNacin()), "Lax");
                }
                return new StudentAutentikacija(u.getStudent().getId(), u.getId());
            }
        }
        obrisiAkoNijedanNeVazi(req, res, Kolacici.STUDENT, tokeni);
        return null;
    }

    /**
     * Obnova i brisanje kolačića ne idu na {@code GET /public/mediji/**}: ti odgovori su javno keširani
     * ({@code Cache-Control: public, max-age=31536000, immutable}), pa bi deljeni keš (proxy, CDN) mogao da ponovi
     * {@code Set-Cookie} sa sesijom drugima. Kolačić se obnavlja ili briše na sledećem običnom zahtevu.
     */
    static boolean smeDaPostaviKolacic(HttpServletRequest req) {
        String m = req.getMethod();
        boolean citanje = "GET".equals(m) || "HEAD".equals(m);
        return !(citanje && req.getRequestURI().startsWith("/public/mediji/"));
    }

    /**
     * Nijedna probana vrednost ne važi: host-only kolačić se briše. Ali ne kad je vrednosti bilo više nego što se proba
     * ({@link Kolacici#MAX_ISTOIMENIH}): pravi kolačić je možda bio među neprobanim, a brisanje bi odjavilo korisnika zbog
     * podmetnutih vrednosti.
     */
    private static void obrisiAkoNijedanNeVazi(HttpServletRequest req, HttpServletResponse res, String ime,
                                               List<String> probani) {
        if (probani.isEmpty() || !smeDaPostaviKolacic(req)) {
            return;
        }
        if (Kolacici.brojVrednosti(req, ime) > probani.size()) {
            log.info("Previše istoimenih kolačića {}, nijedan probani ne važi; ne briše se, ip={}", ime, KlijentIp.iz(req));
            return;
        }
        log.debug("Nevažeći kolačić {}, briše se, ip={}", ime, KlijentIp.iz(req));
        Kolacici.obrisi(req, res, ime);
    }
}
