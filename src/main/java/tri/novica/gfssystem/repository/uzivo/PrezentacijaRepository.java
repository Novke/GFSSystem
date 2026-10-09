package tri.novica.gfssystem.repository.uzivo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Prezentacija;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrezentacijaRepository extends JpaRepository<Prezentacija, Long> {

    List<Prezentacija> findAllByPredmetIdOrderByIzmenjenoDesc(Long predmetId);

    List<Prezentacija> findAllByOrderByIzmenjenoDesc();

    /** Zaključava red prezentacije (SELECT ... FOR UPDATE) do kraja transakcije: izmene slajdova idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prezentacija p where p.id = :id")
    Optional<Prezentacija> findByIdForUpdate(@Param("id") Long id);
}
