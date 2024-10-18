package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.view.DomaciPredavanjaStudentiView;

import java.util.List;

@Repository
public interface DomaciPredavanjaStudentiViewRepository extends JpaRepository<DomaciPredavanjaStudentiView, Long> {

    List<DomaciPredavanjaStudentiView> findAllByDomaciId(Long id);
}
