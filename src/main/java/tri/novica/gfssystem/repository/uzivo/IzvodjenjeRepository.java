package tri.novica.gfssystem.repository.uzivo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IzvodjenjeRepository extends JpaRepository<Izvodjenje, Long> {

    /** Zaključava red izvođenja (SELECT ... FOR UPDATE) do kraja transakcije: komande i odgovori idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Izvodjenje i where i.id = :id")
    Optional<Izvodjenje> findByIdForUpdate(@Param("id") Long id);

    /** Izvođenje sa prezentacijom, grupom i predavanjem u jednom upitu (stanje, rezultati). */
    @Query("""
            select i from Izvodjenje i join fetch i.prezentacija left join fetch i.grupa left join fetch i.predavanje
            where i.id = :id""")
    Optional<Izvodjenje> findSaVezama(@Param("id") Long id);

    /** Lista izvođenja (oba filtra opciona), najnovija prva, sa prezentacijom, grupom i predavanjem. */
    @Query("""
            select i from Izvodjenje i join fetch i.prezentacija left join fetch i.grupa left join fetch i.predavanje
            where (:prezentacijaId is null or i.prezentacija.id = :prezentacijaId)
              and (:status is null or i.status = :status)
            order by i.pocetak desc, i.id desc""")
    List<Izvodjenje> findZaListu(@Param("prezentacijaId") Long prezentacijaId, @Param("status") StatusIzvodjenja status);

    Optional<Izvodjenje> findByAktivanKod(String kod);

    /**
     * Id aktivnog izvođenja sa kodom (pre zaključavanja). Samo id: entitet učitan pre {@link #findByIdForUpdate}
     * ostao bi u persistence context-u sa stanjem od pre čekanja na zaključavanje.
     */
    @Query("select i.id from Izvodjenje i where i.aktivanKod = :kod and i.status = :status")
    Optional<Long> findIdByAktivanKodAndStatus(@Param("kod") String kod, @Param("status") StatusIzvodjenja status);

    /** Id prezentacije izvođenja (bez učitavanja entiteta, iz istog razloga kao gore): za redosled zaključavanja. */
    @Query("select i.prezentacija.id from Izvodjenje i where i.id = :id")
    Optional<Long> findPrezentacijaIdById(@Param("id") Long id);

    boolean existsByAktivanKod(String kod);

    List<Izvodjenje> findAllByStatus(StatusIzvodjenja s);

    /**
     * Id-jevi izvođenja prezentacije u statusu, rastuće (zaključavaju se tim redom). Samo id-jevi: entitet učitan pre
     * zaključavanja ostao bi u persistence context-u sa stanjem od pre čekanja na zaključavanje.
     */
    @Query("select i.id from Izvodjenje i where i.prezentacija.id = :prezentacijaId and i.status = :status order by i.id")
    List<Long> findIdsByPrezentacijaIdAndStatus(@Param("prezentacijaId") Long prezentacijaId,
                                                @Param("status") StatusIzvodjenja status);

    /** Id-jevi aktivnih izvođenja počelih pre {@code granica} (automatski kraj posle 12 h). */
    @Query("select i.id from Izvodjenje i where i.status = :status and i.pocetak < :granica order by i.id")
    List<Long> findIdsStarijaOd(@Param("status") StatusIzvodjenja status, @Param("granica") LocalDateTime granica);

    List<Izvodjenje> findAllByPrezentacijaIdOrderByPocetakDesc(Long id);

    List<Izvodjenje> findAllByOrderByPocetakDesc();

    boolean existsByPrezentacijaIdAndStatus(Long id, StatusIzvodjenja s);

    long countByPrezentacijaId(Long id);

    Optional<Izvodjenje> findFirstByPrezentacijaIdAndStatusOrderByPocetakDesc(Long id, StatusIzvodjenja s);
}
