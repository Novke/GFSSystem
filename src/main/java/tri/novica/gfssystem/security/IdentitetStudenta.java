package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Šav za fazu 2 banke (spec 13.1): ko je student iza zahteva, nezavisno od toga da li ovaj build ima prijavu studenata.
 * Potpis je dogovoren sa drugim projektom i ne menja se; ko prvi merguje pravi interfejs, drugi uzima postojeći.
 */
public interface IdentitetStudenta {
    /** Student iza HTTP zahteva (kolačić gfs_student), prazno kad nije prijavljen. */
    Optional<Long> studentId(HttpServletRequest request);
    /** Da li prijava studenata uopšte postoji u ovom buildu (false -> "Boduje se" nije dostupno). */
    boolean dostupno();
}
