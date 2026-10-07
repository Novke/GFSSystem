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
}
