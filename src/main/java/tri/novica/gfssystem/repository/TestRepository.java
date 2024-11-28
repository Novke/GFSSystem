package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Test;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestRepository extends JpaRepository<Test, Long> {

    @Query("SELECT t FROM Test t LEFT JOIN FETCH t.polaganja WHERE t.id = :id")
    Optional<Test> findByIdFetchPolaganja(Long id);

    List<Test> findAllByGrupaIdAndPredmetIdOrderByDatumAsc(Long gId, Long pId);
}
