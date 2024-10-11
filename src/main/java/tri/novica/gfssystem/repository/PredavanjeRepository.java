package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predavanje;

@Repository
public interface PredavanjeRepository extends JpaRepository<Predavanje, Long> {

    @Query("select max(p.rb) from Predavanje p")
    Integer findPoslednjiRB();
}
