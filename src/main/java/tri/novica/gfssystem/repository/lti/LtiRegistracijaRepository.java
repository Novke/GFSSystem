package tri.novica.gfssystem.repository.lti;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.lti.LtiRegistracija;

import java.util.Optional;

@Repository
public interface LtiRegistracijaRepository extends JpaRepository<LtiRegistracija, Long> {

    /** Registracija po jedinstvenoj trojci iz LTI login initiation-a ({@code iss}, {@code client_id}, {@code lti_deployment_id}). */
    Optional<LtiRegistracija> findByIzdavacAndClientIdAndDeploymentId(String izdavac, String clientId,
                                                                      String deploymentId);
}
