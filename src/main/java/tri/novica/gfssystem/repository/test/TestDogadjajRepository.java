package tri.novica.gfssystem.repository.test;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.test.TestDogadjaj;

import java.util.Collection;
import java.util.List;

public interface TestDogadjajRepository extends JpaRepository<TestDogadjaj, Long> {

    List<TestDogadjaj> findByPrimerakIdOrderByVremeAscIdAsc(Long primerakId);

    @Query("select d from TestDogadjaj d where d.primerakId in :ids order by d.vreme, d.id")
    List<TestDogadjaj> zaPrimerke(@Param("ids") Collection<Long> primerakIds);
}
