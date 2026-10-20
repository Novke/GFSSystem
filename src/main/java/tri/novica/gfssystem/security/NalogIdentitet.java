package tri.novica.gfssystem.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;

import java.util.Optional;

/**
 * {@link IdentitetStudenta} iz studentskog naloga (spec 13.1): student je vlasnik važećeg uređaja iz kolačića
 * {@code gfs_student} (nije opozvan ni istekao), {@code dostupno()} je {@code true} (bodovan run uživo je dozvoljen).
 * Kao korisnički bean potiskuje podrazumevani {@link NemaIdentiteta} iz {@link IdentitetAutoKonfig}
 * ({@code @ConditionalOnMissingBean}); {@code @Primary} ostaje da izbor bude deterministički i ako se drugi bean ipak pojavi.
 * <p>
 * U zahtevu koji je prošao glavni bezbednosni lanac {@code GfsAutentikacijaFilter} je kolačić već proverio (i produžio),
 * pa važi ono što je on zaključio: {@link StudentAutentikacija} daje studenta, nastavnik ima prednost (spec 2.2) pa je
 * prazno, anoniman zahtev je prazan (nijedan kolačić nije važio). Kolačić se ovde proverava sam samo kad nit nema nikakvu
 * autentikaciju: poziv van lanca (druga nit, npr. STOMP poruka ili {@code @Async}, ili test koji zove direktno). Tada se
 * probaju sve vrednosti kolačića tog imena (podmetnut kolačić za {@code .trif.rs}).
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
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a instanceof StudentAutentikacija s) {
            return Optional.of(s.studentId());
        }
        if (a != null) {
            return Optional.empty();   // nastavnik ili anoniman iz lanca
        }
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
