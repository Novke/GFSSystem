package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Aktivnost;

@Repository
public interface AktivnostRepository extends JpaRepository<Aktivnost, Long> {
}
