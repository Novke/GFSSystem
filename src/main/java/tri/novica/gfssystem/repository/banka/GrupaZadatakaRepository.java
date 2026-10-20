package tri.novica.gfssystem.repository.banka;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.Namena;

import java.util.Collection;
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

    /**
     * Brojači serija ({@code SerijaService}), jedan upit: redovi {@code [serija, grupa, najnovije kreirano]};
     * {@code predmetId == null} = svi predmeti.
     */
    @Query("""
            select g.serija, count(g), max(g.kreirano) from GrupaZadataka g
            where g.serija is not null and (:predmetId is null or g.predmet.id = :predmetId) group by g.serija""")
    List<Object[]> brojaciPoSeriji(@Param("predmetId") Long predmetId);

    /**
     * Grupe serije za poništavanje, rastuće po id-ju: redovi {@code [id, naziv, izvor, roditelj, pa preci do korena]} (dubina
     * je najviše 6, pa pet predaka; predak koga nema je {@code null}). Samo vrednosti, ne entiteti, kao
     * {@code VarijantaRepository.zaPonistavanje}.
     */
    @Query("""
            select g.id, g.naziv, g.izvor, r1.id, r2.id, r3.id, r4.id, r5.id from GrupaZadataka g
            left join g.roditelj r1 left join r1.roditelj r2 left join r2.roditelj r3 left join r3.roditelj r4
            left join r4.roditelj r5 where g.serija = :serija order by g.id""")
    List<Object[]> zaPonistavanje(@Param("serija") String serija);

    /** Deca zadatih grupa, jedan upit: redovi {@code [roditeljId, id]} (proba poništavanja serije bez brisanja). */
    @Query("select r.id, g.id from GrupaZadataka g join g.roditelj r where r.id in :ids")
    List<Object[]> decaGrupa(@Param("ids") Collection<Long> ids);
}
