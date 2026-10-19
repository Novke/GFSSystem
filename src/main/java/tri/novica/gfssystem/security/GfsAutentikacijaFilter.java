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
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.utility.KlijentIp;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Identitet zahteva iz naših kolačića (spec 2.2), pre pravila pristupa: važeći {@code gfs_nastavnik} daje
 * {@link NastavnikAutentikacija} ({@code ROLE_ADMIN}). Nevažeći, istekao ili opozvan token: zahtev ide dalje anoniman, a
 * kolačić se briše u odgovoru. Kad se sesija produži (najviše jednom na 10 min, {@link NastavnikSesijaServis#potvrdi}),
 * kolačić dobija nov {@code Max-Age}.
 * <p>
 * Grane su redom ({@link #autentikuj}): prva koja vrati identitet odlučuje. Studentska ({@code gfs_student} ->
 * {@code StudentAutentikacija}) se dodaje kao druga grana. Nije Spring bean (lanac ga pravi sam, da ga Boot ne registruje
 * još jednom kao servlet filter).
 */
@Slf4j
public class GfsAutentikacijaFilter extends OncePerRequestFilter {

    private final NastavnikSesijaServis nastavnici;
    private final Clock clock;

    public GfsAutentikacijaFilter(NastavnikSesijaServis nastavnici, Clock clock) {
        this.nastavnici = Objects.requireNonNull(nastavnici);
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
        return nastavnik(req, res);
    }

    private Authentication nastavnik(HttpServletRequest req, HttpServletResponse res) {
        Optional<String> token = Kolacici.procitaj(req, Kolacici.NASTAVNIK);
        if (token.isEmpty()) {
            return null;
        }
        LocalDateTime pre = LocalDateTime.now(clock);
        Optional<NastavnickaSesija> sesija = nastavnici.potvrdi(token.get());
        if (sesija.isEmpty()) {
            log.debug("Nevažeći nastavnički kolačić, briše se, ip={}", KlijentIp.iz(req));
            Kolacici.obrisi(req, res, Kolacici.NASTAVNIK);
            return null;
        }
        NastavnickaSesija s = sesija.get();
        // potvrdi je upravo pomerio poslednji ulaz (produženje): i kolačić dobija pun Max-Age
        if (!s.getPoslednjiUlaz().isBefore(pre)) {
            Kolacici.postavi(req, res, Kolacici.NASTAVNIK, token.get(), NastavnikSesijaServis.TRAJANJE, "Lax");
        }
        return new NastavnikAutentikacija(s.getId());
    }
}
