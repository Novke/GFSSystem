package tri.novica.gfssystem.repository.lti;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.lti.LtiKljuc;

import java.util.List;
import java.util.Optional;

@Repository
public interface LtiKljucRepository extends JpaRepository<LtiKljuc, Long> {

    Optional<LtiKljuc> findByKid(String kid);

    /** Aktivni ključevi alata (JWKS), najnoviji prvi; isti {@code kreirano} razrešava veći id (deterministično). */
    List<LtiKljuc> findAllByAktivanTrueOrderByKreiranoDescIdDesc();
}
