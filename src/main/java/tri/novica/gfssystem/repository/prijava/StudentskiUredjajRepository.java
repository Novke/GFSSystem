package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.StudentskiUredjaj;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentskiUredjajRepository extends JpaRepository<StudentskiUredjaj, Long> {

    /** Uređaj po SHA-256 tokena iz kolačića (UNIQUE ključ, bez petlje); važenje proverava servis. */
    Optional<StudentskiUredjaj> findByTokenHes(String tokenHes);

    /** Važeći uređaji studenta (nisu opozvani ni istekli). */
    List<StudentskiUredjaj> findAllByStudentIdAndOpozvanIsNullAndIsticeAfter(Long studentId, LocalDateTime sada);

    /**
     * Opoziva sve još neopozvane uređaje studenta (i istekle); vraća broj izmenjenih redova. Izmene iz persistence
     * context-a se prvo upišu; već učitani uređaji se ne osvežavaju (bulk update ih zaobilazi), a zbog
     * {@code @DynamicUpdate} na entitetu njihov kasniji flush ne vraća {@code opozvan} na null.
     */
    @Modifying(flushAutomatically = true)
    @Query("update StudentskiUredjaj u set u.opozvan = :sada where u.student.id = :studentId and u.opozvan is null")
    int opozoviSve(@Param("studentId") Long studentId, @Param("sada") LocalDateTime sada);

    /** Poslednji ulaz bilo kog uređaja studenta, i opozvanog ili isteklog; prazno kad uređaja nema. */
    @Query("select max(u.poslednjiUlaz) from StudentskiUredjaj u where u.student.id = :studentId")
    Optional<LocalDateTime> poslednjiUlaz(@Param("studentId") Long studentId);

    /**
     * Opoziva još neopozvane uređaje studenta otvorene kroz Moodle (način {@code LTI}); vraća broj izmenjenih. Bulk update,
     * pa se već učitani entiteti ne osvežavaju (kao {@link #opozoviSve}).
     */
    @Modifying(flushAutomatically = true)
    @Query("update StudentskiUredjaj u set u.opozvan = :sada where u.student.id = :studentId and u.nacin = :nacin and u.opozvan is null")
    int opozoviPoNacinu(@Param("studentId") Long studentId, @Param("nacin") NacinPrijave nacin, @Param("sada") LocalDateTime sada);
}
