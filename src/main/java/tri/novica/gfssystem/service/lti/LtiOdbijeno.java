package tri.novica.gfssystem.service.lti;

import java.util.Objects;

/**
 * Odbijen LTI zahtev: {@link #greska()} ide korisniku (kod u preusmerenju), {@link #razlog()} samo u log. Razlog je
 * uvek jedna od stalnih reči iz koda (npr. {@code aud}, {@code nonce}), nikad vrednost iz tokena ili zahteva.
 */
public class LtiOdbijeno extends RuntimeException {

    private final LtiGreska greska;
    private final String razlog;

    public LtiOdbijeno(LtiGreska greska, String razlog) {
        super(greska.kod() + ": " + razlog, null, false, false);
        this.greska = Objects.requireNonNull(greska);
        this.razlog = Objects.requireNonNull(razlog);
    }

    public LtiGreska greska() {
        return greska;
    }

    public String razlog() {
        return razlog;
    }
}
