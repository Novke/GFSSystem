package tri.novica.gfssystem.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.entity.portal.PodesavanjaStudenta;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.portal.PodesavanjaStudentaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Podešavanja studenta (tab "Ja"): mejl o otkazanom času (čita ga {@code MejlObavestavanjeStudenata}, jedini put kojim
 * aplikacija šalje studentu mejl o promeni termina) i, od faze 4, prekidači push-a i zvona po vrsti
 * ({@link Prekidac}; čitaju ih sastavljači push poruka i zvono u {@code service.push}). Bez reda važe podrazumevane
 * vrednosti (sve uključeno).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PodesavanjaStudentaServis {

    /** Prekidač push-a ili zvona: kolona u {@code podesavanja_studenta} (nikad iz zahteva). */
    public enum Prekidac {
        PUSH_PODSETNIK("push_podsetnik"),
        PUSH_OTKAZAN_CAS("push_otkazan_cas"),
        PUSH_OCENE("push_ocene"),
        PUSH_POSLE_CASA("push_posle_casa"),
        ZVONO_OCENE("zvono_ocene"),
        ZVONO_POSLE_CASA("zvono_posle_casa");

        private final String kolona;

        Prekidac(String kolona) {
            this.kolona = kolona;
        }
    }

    private final PodesavanjaStudentaRepository repository;
    private final StudentRepository studenti;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PodesavanjaNaloga za(long studentId) {
        Optional<PodesavanjaStudenta> p = repository.findById(studentId);
        if (p.isEmpty()) {
            return new PodesavanjaNaloga(true, true, true, true, true, true, true);
        }
        PodesavanjaStudenta x = p.get();
        return new PodesavanjaNaloga(x.isMejlOtkazanCas(), x.isPushPodsetnik(), x.isPushOtkazanCas(), x.isPushOcene(),
                x.isPushPosleCasa(), x.isZvonoOcene(), x.isZvonoPosleCasa());
    }

    /**
     * Čuva izbor (red se pravi pri prvoj izmeni; {@code null} u novim poljima = ne menja) i vraća sva podešavanja posle
     * izmene; 404 za nepostojećeg studenta (obrisan dok je sesija još važila).
     */
    public PodesavanjaNaloga sacuvaj(long studentId, PodesavanjaNaloga cmd) {
        if (!studenti.existsById(studentId)) throw KontekstStudenta.nema();
        repository.upisiSve(studentId, cmd.mejlOtkazanCas(), broj(cmd.pushPodsetnik()), broj(cmd.pushOtkazanCas()),
                broj(cmd.pushOcene()), broj(cmd.pushPosleCasa()), broj(cmd.zvonoOcene()), broj(cmd.zvonoPosleCasa()),
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
        return za(studentId);
    }

    /** Od datih studenata oni koji ne žele mejl o otkazanom času. */
    @Transactional(readOnly = true)
    public Set<Long> bezMejlaOtkazanogCasa(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) return Set.of();
        return new HashSet<>(repository.bezMejlaOtkazanogCasa(studentIds));
    }

    /** Od datih studenata oni koji su isključili prekidač {@code p} (bez reda = uključeno). */
    @Transactional(readOnly = true)
    public Set<Long> iskljucili(Prekidac p, Collection<Long> studentIds) {
        if (studentIds.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.queryForList("SELECT student_id FROM podesavanja_studenta WHERE student_id IN (:ids) AND "
                + p.kolona + " = 0", new MapSqlParameterSource("ids", studentIds), Long.class));
    }

    /** Student je otvorio zvono (gasi tačku na svim uređajima). */
    public void zvonoOtvoreno(long studentId) {
        if (!studenti.existsById(studentId)) throw KontekstStudenta.nema();
        repository.zvonoOtvoreno(studentId, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
    }

    /** Poslednje otvaranje zvona, prazno kad ga student još nije otvorio. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> zvonoOtvorenoU(long studentId) {
        return repository.findById(studentId).map(PodesavanjaStudenta::getZvonoOtvoreno);
    }

    private static int broj(Boolean b) {
        return b == null ? -1 : b ? 1 : 0;
    }
}
