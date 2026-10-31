package tri.novica.gfssystem.repository.raspored;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.raspored.Raspored;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RasporedRepository extends JpaRepository<Raspored, Long> {

    /** Periodi, najnoviji prvi (po početku važenja). */
    List<Raspored> findAllByOrderByVaziOdDescIdDesc();

    /** Broj stavki po periodu: redovi {@code [rasporedId, broj]}. */
    @Query("select s.raspored.id, count(s) from RasporedStavka s group by s.raspored.id")
    List<Object[]> brojStavki();

    /**
     * Zaključava red perioda do kraja transakcije: izmena stavki ide redom, pa provera duplikata u servisu važi (u bazi nema
     * jedinstvenog ključa stavki). Redosled brava: period, pa stavka.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Raspored r where r.id = :id")
    Optional<Raspored> findByIdForUpdate(@Param("id") Long id);

    /** Da li neki period sadrži dan {@code danas} (početna: raspored ili stara heuristika). */
    @Query("select count(r) > 0 from Raspored r where r.vaziOd <= :danas and r.vaziDo >= :danas")
    boolean pokriva(@Param("danas") LocalDate danas);
}
