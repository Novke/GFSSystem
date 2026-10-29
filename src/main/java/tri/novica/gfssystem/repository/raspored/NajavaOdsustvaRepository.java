package tri.novica.gfssystem.repository.raspored;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.raspored.NajavaOdsustva;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NajavaOdsustvaRepository extends JpaRepository<NajavaOdsustva, Long> {

    Optional<NajavaOdsustva> findByStavkaIdAndDatumAndStudentId(Long stavkaId, LocalDate datum, Long studentId);

    long countByStavkaIdAndDatum(Long stavkaId, LocalDate datum);

    @Modifying(flushAutomatically = true)
    @Query("delete from NajavaOdsustva n where n.stavka.id = :stavkaId and n.datum = :datum and n.student.id = :studentId")
    int obrisi(@Param("stavkaId") Long stavkaId, @Param("datum") LocalDate datum, @Param("studentId") Long studentId);

    /** Najave stavki u prozoru sa studentima, po vremenu najave (spisak za nastavnika). */
    @Query("""
            select n from NajavaOdsustva n join fetch n.student
            where n.stavka.id in :ids and n.datum between :od and :doDatuma
            order by n.vreme asc, n.id asc""")
    List<NajavaOdsustva> uProzoruSaStudentima(@Param("ids") Collection<Long> ids, @Param("od") LocalDate od,
                                              @Param("doDatuma") LocalDate doDatuma);

    /** Najave jednog studenta za stavke u prozoru (nalog: "Javio si da nećeš doći"). */
    @Query("""
            select n from NajavaOdsustva n
            where n.student.id = :studentId and n.stavka.id in :ids and n.datum between :od and :doDatuma""")
    List<NajavaOdsustva> studentaUProzoru(@Param("studentId") Long studentId, @Param("ids") Collection<Long> ids,
                                          @Param("od") LocalDate od, @Param("doDatuma") LocalDate doDatuma);

    /** Broj najava po terminu: redovi {@code [stavkaId, datum, broj]} (početna). */
    @Query("""
            select n.stavka.id, n.datum, count(n) from NajavaOdsustva n
            where n.stavka.id in :ids and n.datum between :od and :doDatuma
            group by n.stavka.id, n.datum""")
    List<Object[]> brojeviUProzoru(@Param("ids") Collection<Long> ids, @Param("od") LocalDate od,
                                   @Param("doDatuma") LocalDate doDatuma);
}
