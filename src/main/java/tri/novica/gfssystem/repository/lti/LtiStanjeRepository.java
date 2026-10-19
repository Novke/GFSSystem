package tri.novica.gfssystem.repository.lti;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.lti.LtiStanje;

import java.util.Optional;

@Repository
public interface LtiStanjeRepository extends JpaRepository<LtiStanje, Long> {

    /** Stanje po SHA-256 parametra {@code state} (UNIQUE ključ); važenje i jednokratnost proverava servis. */
    Optional<LtiStanje> findByStanjeHes(String stanjeHes);
}
