package tri.novica.gfssystem.repository.raspored;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.raspored.RasporedStavka;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RasporedStavkaRepository extends JpaRepository<RasporedStavka, Long> {

    /**
     * <b>Jedino pravilo "kome važi stavka"</b> (spec najave odsustva 4.1): parovi {@code [stavkaId, studentId]} gde je
     * studentova grupa grupa stavke, ili student ima aktivnost ili čekiranje na predavanju istog predmeta i grupe (starija
     * generacija koja sluša sa mlađom). Samo stavke perioda koji traju bar do {@code od}. Filter po stavci ili po studentu
     * ({@code null} = bez filtera; nikad oba {@code null}): po studentu daje njegove stavke (nalog, provera najave), po
     * stavci primaoce mejla pri otkazivanju.
     */
    @Query("""
            select s.id, st.id from RasporedStavka s, Student st
            where (:stavkaId is null or s.id = :stavkaId)
              and (:studentId is null or st.id = :studentId)
              and s.raspored.vaziDo >= :od
              and (st.grupa = s.grupa
                   or exists (select a.id from Aktivnost a
                              where a.student = st and a.predavanje.grupa = s.grupa and a.predavanje.predmet = s.predmet)
                   or exists (select c.id from Cekiranje c
                              where c.student = st and c.predavanje.grupa = s.grupa and c.predavanje.predmet = s.predmet))""")
    List<Object[]> parovi(@Param("stavkaId") Long stavkaId, @Param("studentId") Long studentId, @Param("od") LocalDate od);

    /** Stavke sa periodom, predmetom i grupom (bez N+1). */
    @Query("""
            select s from RasporedStavka s join fetch s.raspored join fetch s.predmet join fetch s.grupa
            where s.id in :ids""")
    List<RasporedStavka> saDetaljima(@Param("ids") Collection<Long> ids);

    /** Stavke perioda koji seku prozor {@code [od, doDatuma]}, sa periodom, predmetom i grupom. */
    @Query("""
            select s from RasporedStavka s join fetch s.raspored r join fetch s.predmet join fetch s.grupa
            where r.vaziDo >= :od and r.vaziOd <= :doDatuma""")
    List<RasporedStavka> uProzoru(@Param("od") LocalDate od, @Param("doDatuma") LocalDate doDatuma);

    /**
     * Zaključava red stavke (najava, povlačenje, otkazivanje i vraćanje njenih termina idu redom: broj najava je tačan i
     * obaveštenje se pravi jednom). Posle reda perioda, pre MySQL brave mejla nastavniku.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RasporedStavka s join fetch s.raspored join fetch s.predmet join fetch s.grupa where s.id = :id")
    Optional<RasporedStavka> findByIdForUpdate(@Param("id") Long id);

    /** Stavke drugih perioda ({@code r.id <> :bez}) čiji se period preklapa sa {@code [od, doDatuma]} (provera preklapanja). */
    @Query("""
            select s from RasporedStavka s join fetch s.raspored r join fetch s.predmet join fetch s.grupa
            where r.id <> :bez and r.vaziOd <= :doDatuma and r.vaziDo >= :od""")
    List<RasporedStavka> uDrugimPeriodima(@Param("bez") Long bez, @Param("od") LocalDate od,
                                          @Param("doDatuma") LocalDate doDatuma);
}
