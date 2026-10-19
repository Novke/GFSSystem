package tri.novica.gfssystem.repository.lti;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.lti.LtiPovezivanje;

import java.util.Optional;

@Repository
public interface LtiPovezivanjeRepository extends JpaRepository<LtiPovezivanje, Long> {

    /** Povezivanje po SHA-256 tokena iz kolačića {@code gfs_lti_par} (UNIQUE ključ). */
    Optional<LtiPovezivanje> findByTokenHes(String tokenHes);
}
