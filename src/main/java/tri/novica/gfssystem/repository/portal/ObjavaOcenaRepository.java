package tri.novica.gfssystem.repository.portal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.portal.ObjavaOcena;

import java.util.Optional;

@Repository
public interface ObjavaOcenaRepository extends JpaRepository<ObjavaOcena, Long> {

    Optional<ObjavaOcena> findByPredmetIdAndGrupaId(Long predmetId, Long grupaId);

    /** Isto, zaključano do kraja transakcije (objava i povlačenje iste grupe idu jedno za drugim). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ObjavaOcena o where o.predmetId = :predmetId and o.grupaId = :grupaId")
    Optional<ObjavaOcena> findForUpdate(@Param("predmetId") Long predmetId, @Param("grupaId") Long grupaId);
}
