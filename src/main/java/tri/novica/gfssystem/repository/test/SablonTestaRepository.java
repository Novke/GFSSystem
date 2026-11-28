package tri.novica.gfssystem.repository.test;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.test.SablonTesta;

import java.util.List;
import java.util.Optional;

public interface SablonTestaRepository extends JpaRepository<SablonTesta, Long> {

    @Query("select s from SablonTesta s where s.predmet.id = :predmetId and (:arhivirani = true or s.arhiviran = false) "
            + "order by s.arhiviran asc, s.izmenjeno desc, s.id desc")
    List<SablonTesta> spisak(@Param("predmetId") Long predmetId, @Param("arhivirani") boolean arhivirani);

    @Query("select s from SablonTesta s where (:arhivirani = true or s.arhiviran = false) "
            + "order by s.arhiviran asc, s.izmenjeno desc, s.id desc")
    List<SablonTesta> svi(@Param("arhivirani") boolean arhivirani);

    /** Zaključava red šablona do kraja transakcije: izmene istog šablona idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SablonTesta s where s.id = :id")
    Optional<SablonTesta> findByIdForUpdate(@Param("id") Long id);
}
