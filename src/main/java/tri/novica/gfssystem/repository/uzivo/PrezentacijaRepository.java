package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Prezentacija;

import java.util.List;

@Repository
public interface PrezentacijaRepository extends JpaRepository<Prezentacija, Long> {

    List<Prezentacija> findAllByPredmetIdOrderByIzmenjenoDesc(Long predmetId);

    List<Prezentacija> findAllByOrderByIzmenjenoDesc();
}
