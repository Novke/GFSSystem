package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Domaci;

@Repository
public interface DomaciRepository extends JpaRepository<Domaci, Long> {
}
