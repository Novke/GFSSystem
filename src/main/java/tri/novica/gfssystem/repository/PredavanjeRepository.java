package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predavanje;

@Repository
public interface PredavanjeRepository extends JpaRepository<Predavanje, Long> {
}
