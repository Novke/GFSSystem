package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.view.DomaciEvidentiranjeView;

import java.util.List;

@Repository
public interface DomaciEvidentiranjeRepository extends JpaRepository<DomaciEvidentiranjeView, Long> {

    List<DomaciEvidentiranjeView> findAllByDomaciId(Long id);
}
