package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Polaganje;

@Repository
public interface PolaganjeRepository extends JpaRepository<Polaganje, Long> {
}
