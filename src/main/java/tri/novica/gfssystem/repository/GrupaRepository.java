package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;

@Repository
public interface GrupaRepository extends JpaRepository<Grupa, Long> {
}