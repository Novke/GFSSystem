package tri.novica.gfssystem.repository.raspored;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.raspored.OtkazanTermin;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtkazanTerminRepository extends JpaRepository<OtkazanTermin, Long> {

    Optional<OtkazanTermin> findByStavkaIdAndDatum(Long stavkaId, LocalDate datum);

    boolean existsByStavkaIdAndDatum(Long stavkaId, LocalDate datum);

    /** Otkazani termini stavki u prozoru (jedan upit za listu termina). */
    @Query("select o from OtkazanTermin o where o.stavka.id in :ids and o.datum between :od and :doDatuma")
    List<OtkazanTermin> uProzoru(@Param("ids") Collection<Long> ids, @Param("od") LocalDate od,
                                 @Param("doDatuma") LocalDate doDatuma);

    /** Otkazivanja perioda od {@code od} (uključeno), sa stavkom, po datumu: upozorenje u formi i brojanje izgubljenih. */
    @Query("select o from OtkazanTermin o join fetch o.stavka s where s.raspored.id = :rasporedId and o.datum >= :od order by o.datum")
    List<OtkazanTermin> odDatumaZaRaspored(@Param("rasporedId") Long rasporedId, @Param("od") LocalDate od);
}
