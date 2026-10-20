package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/** {@link IdentitetStudenta} kad build nema prijavu studenata: niko nije prijavljen. Registruje ga {@link IdentitetConfig}. */
public class NemaIdentiteta implements IdentitetStudenta {

    @Override
    public Optional<Long> studentId(HttpServletRequest request) {
        return Optional.empty();
    }

    @Override
    public boolean dostupno() {
        return false;
    }
}
