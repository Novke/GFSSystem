package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Šav sa prijavom studenata: ko je student iza javnog zahteva. Podrazumevani bean je {@link NemaIdentiteta} (iz
 * {@link IdentitetAutoKonfig}); pravi bean daje prijava studenata i potiskuje ga čim je u kontekstu.
 */
public interface IdentitetStudenta {
    /** Student iza HTTP zahteva (kolačić gfs_student), prazno kad nije prijavljen. */
    Optional<Long> studentId(HttpServletRequest request);
    /** Da li prijava studenata uopšte postoji u ovom buildu (false -> "Boduje se" nije dostupno). */
    boolean dostupno();
}
