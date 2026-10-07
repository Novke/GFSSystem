package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Odgovor;

import java.util.List;
import java.util.Optional;

@Repository
public interface OdgovorRepository extends JpaRepository<Odgovor, Long> {

    List<Odgovor> findAllByRundaId(Long id);

    List<Odgovor> findAllByRundaIzvodjenjeId(Long id);

    boolean existsByRundaIdAndUcesnikId(Long r, Long u);

    long countByRundaId(Long id);

    Optional<Odgovor> findByRundaIdAndUcesnikId(Long r, Long u);
}
