package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DomaciRepository extends JpaRepository<Domaci, Long>, JpaSpecificationExecutor<Domaci> {
    @Query("""
            SELECT d FROM Domaci d
                   JOIN FETCH d.predmet p
                   JOIN FETCH d.grupa g
                   LEFT JOIN FETCH d.predavanje pr
                   LEFT JOIN FETCH d.uradjeniDomaci u
                   WHERE d.id = :id""")
    Optional<Domaci> findDomaciPlusGrupaPredmetPredavanje(Long id);

    List<Domaci> findAllByGrupaAndPredmetOrderByDatumAsc(Grupa grupa, Predmet predmet);

    @Query("SELECT ud FROM UradjenDomaci ud " +
            "JOIN FETCH ud.domaci d " +
            "WHERE ud.student = :student AND d.predmet = :predmet " +
            "ORDER BY d.datum ASC")
    List<UradjenDomaci> findUradjeniDomaciByStudentAndPredmet(@Param("student") Student student,
                                                              @Param("predmet") Predmet predmet);

    /**
     * Broj urađenih domaćih (bez oslobođenih) po domaćem, za stranicu liste ({@code Brojaci.poId}).
     * Redovi: {@code [domaciId, broj]}.
     */
    @Query("select u.domaci.id, count(u) from UradjenDomaci u where u.domaci.id in :ids and u.oslobodjen = false group by u.domaci.id")
    List<Object[]> brojUradjenihPoDomacem(@Param("ids") Collection<Long> ids);

    long countByGrupaIdAndPredmetId(Long grupaId, Long predmetId);

    /** Domaći grupe, opciono jednog predmeta ({@code predmetId} null = svi). */
    @Query("select count(d) from Domaci d where d.grupa.id = :grupaId and (:predmetId is null or d.predmet.id = :predmetId)")
    long brojDomacihGrupe(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);

    /**
     * Urađeni domaći grupe po studentu grupe (i oslobođeni, kao u ocenjivanju). Redovi {@code [studentId, broj]};
     * {@code predmetId} null = svi predmeti.
     */
    @Query("""
            select u.student.id, count(u) from UradjenDomaci u
            where u.student.grupa.id = :grupaId and u.domaci.grupa.id = :grupaId
              and (:predmetId is null or u.domaci.predmet.id = :predmetId)
            group by u.student.id""")
    List<Object[]> brojUradjenihPoStudentu(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);

    /** Urađeni domaći studenta na domaćima grupe za predmet, po datumu domaćeg (isti skup kao u {@code getRezultati}). */
    @Query("""
            select u from UradjenDomaci u join fetch u.domaci d
            where u.student.id = :studentId and d.grupa.id = :grupaId and d.predmet.id = :predmetId
            order by d.datum asc, d.id asc""")
    List<UradjenDomaci> uradjeniNaDomacimaGrupe(@Param("studentId") Long studentId, @Param("grupaId") Long grupaId,
                                                @Param("predmetId") Long predmetId);
}
