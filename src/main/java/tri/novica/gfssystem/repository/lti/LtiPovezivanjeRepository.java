package tri.novica.gfssystem.repository.lti;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.lti.LtiPovezivanje;

import java.util.Optional;

@Repository
public interface LtiPovezivanjeRepository extends JpaRepository<LtiPovezivanje, Long> {

    /** Povezivanje po SHA-256 tokena iz kolačića {@code gfs_lti_par} (UNIQUE ključ). */
    Optional<LtiPovezivanje> findByTokenHes(String tokenHes);

    /** Isto, zaključan red ({@code PESSIMISTIC_WRITE}) sa registracijom: dva istovremena {@code povezi} ne troše ga dvaput. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LtiPovezivanje p join fetch p.registracija where p.tokenHes = :hes")
    Optional<LtiPovezivanje> nadjiZaIzmenu(@Param("hes") String tokenHes);
}
