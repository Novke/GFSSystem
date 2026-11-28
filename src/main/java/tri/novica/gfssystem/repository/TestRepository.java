package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Test;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestRepository extends JpaRepository<Test, Long>, JpaSpecificationExecutor<Test> {

    @Query("SELECT t FROM Test t LEFT JOIN FETCH t.polaganja WHERE t.id = :id")
    Optional<Test> findByIdFetchPolaganja(Long id);

    List<Test> findAllByGrupaIdAndPredmetIdOrderByDatumAsc(Long gId, Long pId);

    /** Zaključava red testa (SELECT ... FOR UPDATE) do kraja transakcije: izvlačenje, raspodela i tok online testa idu redom. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Test t where t.id = :id")
    Optional<Test> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
