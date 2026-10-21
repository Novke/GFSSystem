package tri.novica.gfssystem.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Student prijavljen zapamćenim uređajem ({@code gfs_student}); uloga {@code ROLE_STUDENT}. Nosi id studenta i
 * uređaja (za "odjavi ovaj uređaj"). Pravi je {@code GfsAutentikacijaFilter} kad uređaj važi (studentska grana stiže
 * sa studentskom prijavom).
 */
public final class StudentAutentikacija extends AbstractAuthenticationToken {

    private final Long studentId;
    private final Long uredjajId;

    public StudentAutentikacija(Long studentId, Long uredjajId) {
        super(AuthorityUtils.createAuthorityList("ROLE_STUDENT"));
        this.studentId = studentId;
        this.uredjajId = uredjajId;
        setAuthenticated(true);
    }

    public Long studentId() {
        return studentId;
    }

    public Long uredjajId() {
        return uredjajId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return studentId;
    }

    @Override
    public String getName() {
        return "student-" + studentId;
    }

    /** Student ovog zahteva, prazno kad zahtev nije studentski. */
    public static Optional<StudentAutentikacija> trenutna() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a instanceof StudentAutentikacija s ? Optional.of(s) : Optional.empty();
    }
}
