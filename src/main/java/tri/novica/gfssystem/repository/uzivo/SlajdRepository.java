package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

import java.util.List;

@Repository
public interface SlajdRepository extends JpaRepository<Slajd, Long> {

    List<Slajd> findAllByPrezentacijaIdOrderByRbAsc(Long id);

    long countByPrezentacijaId(Long id);

    long countByPrezentacijaIdAndTip(Long id, TipSlajda tip);
}
