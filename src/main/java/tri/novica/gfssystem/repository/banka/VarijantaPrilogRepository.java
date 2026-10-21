package tri.novica.gfssystem.repository.banka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.banka.VarijantaPrilog;

@Repository
public interface VarijantaPrilogRepository extends JpaRepository<VarijantaPrilog, Long> {
}
