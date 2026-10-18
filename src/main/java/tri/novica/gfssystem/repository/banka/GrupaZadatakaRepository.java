package tri.novica.gfssystem.repository.banka;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.Namena;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupaZadatakaRepository extends JpaRepository<GrupaZadataka, Long> {

    /** Celo stablo predmeta i namene: koreni (roditelj null) prvi, pa deca po roditelju, svi po {@code rb}. */
    List<GrupaZadataka> findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(Long predmetId, Namena namena);

    /** Zaključava red grupe (SELECT ... FOR UPDATE) do kraja transakcije: izmene stabla idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrupaZadataka g where g.id = :id")
    Optional<GrupaZadataka> findByIdForUpdate(@Param("id") Long id);

    /** Samo id predmeta, pre zaključavanja: entitet učitan pre {@code findByIdForUpdate} Hibernate posle ne osvežava. */
    @Query("select g.predmet.id from GrupaZadataka g where g.id = :id")
    Optional<Long> findPredmetId(@Param("id") Long id);

    /** Koreni stabla predmeta i namene, po {@code rb}. */
    List<GrupaZadataka> findByPredmetIdAndNamenaAndRoditeljIsNullOrderByRb(Long predmetId, Namena namena);

    long countByRoditeljId(Long roditeljId);

    List<GrupaZadataka> findByRoditeljIdOrderByRb(Long roditeljId);
}
