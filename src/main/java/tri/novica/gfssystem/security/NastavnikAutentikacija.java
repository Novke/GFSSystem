package tri.novica.gfssystem.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Nastavnik prijavljen važećom sesijom ({@code gfs_nastavnik}); uloga {@code ROLE_ADMIN}. Nosi samo id sesije. */
public final class NastavnikAutentikacija extends AbstractAuthenticationToken {

    private final Long sesijaId;

    public NastavnikAutentikacija(Long sesijaId) {
        super(AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        this.sesijaId = sesijaId;
        setAuthenticated(true);
    }

    public Long sesijaId() {
        return sesijaId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return sesijaId;
    }

    @Override
    public String getName() {
        return "nastavnik-sesija-" + sesijaId;
    }

    /** Nastavnik ovog zahteva, prazno kad zahtev nije nastavnički (anoniman ili student). */
    public static Optional<NastavnikAutentikacija> trenutna() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a instanceof NastavnikAutentikacija n ? Optional.of(n) : Optional.empty();
    }
}
