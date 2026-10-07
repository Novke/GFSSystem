package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predavanje;

import java.util.List;
import java.util.Optional;

@Repository
public interface PredavanjeRepository extends JpaRepository<Predavanje, Long>, JpaSpecificationExecutor<Predavanje> {

    @Query("select max(p.rb) from Predavanje p")
    Integer findPoslednjiRB();
    List<Predavanje> findAllByGrupaIdAndPredmetIdOrderByDatumAsc(Long grupaId, Long predmetId);

    /** Poslednje predavanje po (datum, id); predlog sledećeg na kontrolnoj tabli. */
    Optional<Predavanje> findFirstByOrderByDatumDescIdDesc();
}
