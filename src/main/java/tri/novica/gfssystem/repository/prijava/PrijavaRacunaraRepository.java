package tri.novica.gfssystem.repository.prijava;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.PrijavaRacunara;

import java.util.Optional;

@Repository
public interface PrijavaRacunaraRepository extends JpaRepository<PrijavaRacunara, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrijavaRacunara p where p.tokenHes = :hes")
    Optional<PrijavaRacunara> zaTokenForUpdate(@Param("hes") String tokenHes);

    Optional<PrijavaRacunara> findByTokenHes(String tokenHes);

    /** Najnoviji zahtev sa ovom tajnom (tajna je slučajna, pa je u praksi jedan). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrijavaRacunara p where p.tajnaHes = :hes order by p.id desc limit 1")
    Optional<PrijavaRacunara> zaTajnuForUpdate(@Param("hes") String tajnaHes);
}
