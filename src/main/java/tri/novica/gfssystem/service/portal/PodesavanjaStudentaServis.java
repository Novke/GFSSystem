package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.portal.PodesavanjaStudentaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Podešavanja studenta (tab "Ja"). Danas samo mejl o otkazanom času; čita ga {@code MejlObavestavanjeStudenata}, jedini put
 * kojim aplikacija šalje studentu mejl o promeni termina, pa izbor važi za svaki budući način slanja kroz isti put.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PodesavanjaStudentaServis {

    private final PodesavanjaStudentaRepository repository;
    private final StudentRepository studenti;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PodesavanjaNaloga za(long studentId) {
        return repository.findById(studentId).map(p -> new PodesavanjaNaloga(p.isMejlOtkazanCas()))
                .orElse(new PodesavanjaNaloga(true));
    }

    /** Čuva izbor (red se pravi pri prvoj izmeni); 404 za nepostojećeg studenta (obrisan dok je sesija još važila). */
    public PodesavanjaNaloga sacuvaj(long studentId, PodesavanjaNaloga cmd) {
        if (!studenti.existsById(studentId)) throw KontekstStudenta.nema();
        repository.upisi(studentId, cmd.mejlOtkazanCas(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
        return new PodesavanjaNaloga(cmd.mejlOtkazanCas());
    }

    /** Od datih studenata oni koji ne žele mejl o otkazanom času. */
    @Transactional(readOnly = true)
    public Set<Long> bezMejlaOtkazanogCasa(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) return Set.of();
        return new HashSet<>(repository.bezMejlaOtkazanogCasa(studentIds));
    }
}
