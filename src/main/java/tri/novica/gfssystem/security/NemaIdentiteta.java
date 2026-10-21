package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * {@link IdentitetStudenta} dok prijave studenata nema: niko nije prijavljen i bodovan run nije dostupan. Bean je samo
 * kad nema drugog ({@link IdentitetAutoKonfig}).
 */
public final class NemaIdentiteta implements IdentitetStudenta {

    @Override
    public Optional<Long> studentId(HttpServletRequest request) {
        return Optional.empty();
    }

    @Override
    public boolean dostupno() {
        return false;
    }
}
