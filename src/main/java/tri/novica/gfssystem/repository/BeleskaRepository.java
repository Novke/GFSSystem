package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Beleska;

import java.util.List;

@Repository
public interface BeleskaRepository extends JpaRepository<Beleska, Long> {

    /** Najnovije prvo; {@code id} razrešava beleške sa istim vremenom. */
    List<Beleska> findByStudentIdOrderByKreiranoDescIdDesc(Long studentId);
}
