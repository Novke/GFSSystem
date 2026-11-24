package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.TablaPredavanja;

import java.util.Optional;

@Repository
public interface TablaPredavanjaRepository extends JpaRepository<TablaPredavanja, Long> {

    /** Upis prvo zaključa red predavanja ({@code PredavanjeRepository.findByIdForUpdate}), pa nema dva prva upisa. */
    Optional<TablaPredavanja> findByPredavanjeId(Long predavanjeId);
}
