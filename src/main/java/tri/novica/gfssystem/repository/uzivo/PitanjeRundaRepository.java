package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;

import java.util.List;
import java.util.Optional;

@Repository
public interface PitanjeRundaRepository extends JpaRepository<PitanjeRunda, Long> {

    List<PitanjeRunda> findAllByIzvodjenjeIdOrderByOtvorenoAsc(Long id);

    /** Poslednja runda tog slajda u izvođenju (pitanje se može otvoriti ponovo). */
    Optional<PitanjeRunda> findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(Long izvodjenjeId, Long slajdId);

    void deleteAllByIzvodjenjeId(Long id);
}
