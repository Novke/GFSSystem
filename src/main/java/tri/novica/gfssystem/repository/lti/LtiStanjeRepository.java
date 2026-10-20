package tri.novica.gfssystem.repository.lti;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.lti.LtiStanje;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface LtiStanjeRepository extends JpaRepository<LtiStanje, Long> {

    /** Stanje po SHA-256 parametra {@code state} (UNIQUE ključ); važenje i jednokratnost proverava servis. */
    Optional<LtiStanje> findByStanjeHes(String stanjeHes);

    /** Isto, sa registracijom (launch je čita posle transakcije, bez open-in-view). */
    @Query("select s from LtiStanje s join fetch s.registracija where s.stanjeHes = :hes")
    Optional<LtiStanje> nadjiSaRegistracijom(@Param("hes") String stanjeHes);

    /** Troši stanje atomski: 1 samo za prvi poziv dok važi, 0 kad je već potrošeno (ili istekao u međuvremenu). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LtiStanje s set s.iskorisceno = :sada where s.id = :id and s.iskorisceno is null and s.istice > :sada")
    int potrosi(@Param("id") Long id, @Param("sada") LocalDateTime sada);
}
