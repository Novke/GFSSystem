package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predmet;

@Repository
public interface PredmetRepository extends JpaRepository<Predmet, Long> {
}
