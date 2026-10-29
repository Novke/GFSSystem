package tri.novica.gfssystem.repository.portal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.portal.Vezbanje;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VezbanjeRepository extends JpaRepository<Vezbanje, Long> {

    /** Pokušaj samo zadatog studenta (tuđi i nepostojeći su isto: prazno, pa 404). */
    Optional<Vezbanje> findByIdAndStudentId(Long id, Long studentId);

    /** Isto, zaključano do kraja transakcije (dve brze provere istog pokušaja ne gube brojač). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vezbanje v where v.id = :id and v.studentId = :studentId")
    Optional<Vezbanje> findForUpdate(@Param("id") Long id, @Param("studentId") Long studentId);

    /** Poslednje izvučene varijante studenta u tipu, od najnovije (izbor izbegava poslednjih 5). */
    @Query("select v.varijantaId from Vezbanje v where v.studentId = :studentId and v.temaId = :temaId order by v.izvuceno desc, v.id desc")
    List<Long> poslednjeVarijante(@Param("studentId") Long studentId, @Param("temaId") Long temaId, Pageable pageable);

    /**
     * Netaknuti pokušaji studenta u tipu (bez provere i bez otvorenog rešenja) izvučeni od {@code od}, od najnovijeg: novo
     * izvlačenje bez "Novi zadatak" vraća jedan od njih umesto novog reda.
     */
    @Query("""
            select v from Vezbanje v where v.studentId = :studentId and v.temaId = :temaId and v.brojProvera = 0
              and v.resenjePrikazano = false and v.izvuceno >= :od order by v.izvuceno desc, v.id desc""")
    List<Vezbanje> netaknuti(@Param("studentId") Long studentId, @Param("temaId") Long temaId,
                             @Param("od") LocalDateTime od, Pageable pageable);

    /**
     * Učinak studenta po tipu: redovi {@code [temaId, provereno, tacno]}: koliko izvučenih je bar jednom provereno i koliko
     * ih je poslednjom proverom tačno.
     */
    @Query("""
            select v.temaId, sum(case when v.brojProvera > 0 then 1 else 0 end), sum(case when v.tacno = true then 1 else 0 end)
            from Vezbanje v where v.studentId = :studentId and v.temaId in :temaIds group by v.temaId""")
    List<Object[]> ucinak(@Param("studentId") Long studentId, @Param("temaIds") Collection<Long> temaIds);
}
