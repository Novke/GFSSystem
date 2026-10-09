package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Medij;

@Repository
public interface MedijRepository extends JpaRepository<Medij, String> {
}
