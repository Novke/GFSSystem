package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;

import java.util.Optional;

/**
 * {@link IdentitetStudenta} iz studentskog naloga (spec 13.1): student je vlasnik važećeg uređaja iz kolačića
 * {@code gfs_student} (nije opozvan ni istekao). {@code @Primary}, da izbor bude deterministički i kad faza 2 donese svoj
 * {@link NemaIdentiteta} kao bean. Probaju se sve vrednosti kolačića tog imena (podmetnut kolačić za {@code .trif.rs}).
 */
@Component
@Primary
public class NalogIdentitet implements IdentitetStudenta {

    private final StudentskaPrijava prijava;

    public NalogIdentitet(StudentskaPrijava prijava) {
        this.prijava = prijava;
    }

    @Override
    public Optional<Long> studentId(HttpServletRequest request) {
        for (String token : Kolacici.procitajSve(request, Kolacici.STUDENT)) {
            Optional<StudentskiUredjaj> u = prijava.potvrdi(token);
            if (u.isPresent()) {
                return Optional.of(u.get().getStudent().getId());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean dostupno() {
        return true;
    }
}
