package tri.novica.gfssystem.config;

import java.security.Principal;

/**
 * Korisnik WebSocket sesije: student {@code u-<ucesnikId>} (za {@code /user/queue/licno} i {@code /user/queue/greske}),
 * nastavnik {@code n-<uuid>} (svaki prozor posebno).
 */
public record StompPrincipal(String name) implements Principal {

    public static final String STUDENT_PREFIKS = "u-";

    public static String student(Long ucesnikId) {
        return STUDENT_PREFIKS + ucesnikId;
    }

    @Override
    public String getName() {
        return name;
    }
}
