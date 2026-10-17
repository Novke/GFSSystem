package tri.novica.gfssystem.repository.banka;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.entity.banka.Varijanta;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VarijantaRepository extends JpaRepository<Varijanta, Long> {

    List<Varijanta> findByGrupaIdOrderByRb(Long grupaId);

    List<Varijanta> findByGrupaIdAndStatusOrderByRb(Long grupaId, StatusVarijante status);

    /**
     * Varijante grupe po {@code rb} (pa id-ju), samo zadatog statusa kad nije {@code null}; SVG mediji u istom upitu
     * (bez upita po varijanti).
     */
    @Query("""
            select v from Varijanta v left join fetch v.renderSvg left join fetch v.renderResenjeSvg
            where v.grupa.id = :grupaId and (:status is null or v.status = :status) order by v.rb, v.id""")
    List<Varijanta> lista(@Param("grupaId") Long grupaId, @Param("status") StatusVarijante status);

    /** Samo id grupe, pre zaključavanja: red grupe se zaključava pre reda varijante, a entitet učitan pre ključa se ne osvežava. */
    @Query("select v.grupa.id from Varijanta v where v.id = :id")
    Optional<Long> findGrupaId(@Param("id") Long id);

    /** Najveći {@code rb} u grupi, 0 za praznu grupu (nova varijanta dobija sledeći). */
    @Query("select coalesce(max(v.rb), 0) from Varijanta v where v.grupa.id = :grupaId")
    int maxRb(@Param("grupaId") Long grupaId);

    /** Zaključava red varijante (SELECT ... FOR UPDATE) do kraja transakcije: izmene i render idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Varijanta v where v.id = :id")
    Optional<Varijanta> findByIdForUpdate(@Param("id") Long id);

    /** Sve varijante grupe, zaključane (posle reda grupe): promena šeme grupe i brisanje grupe. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Varijanta v where v.grupa.id = :grupaId order by v.rb, v.id")
    List<Varijanta> findByGrupaIdForUpdate(@Param("grupaId") Long grupaId);

    long countByGrupaId(Long grupaId);

    long countByGrupaIdAndStatus(Long grupaId, StatusVarijante status);

    /** Brojači za stablo, jedan upit: redovi {@code [grupaId, status, renderStatus, broj]}. */
    @Query("""
            select v.grupa.id, v.status, v.renderStatus, count(v) from Varijanta v
            where v.grupa.id in :ids group by v.grupa.id, v.status, v.renderStatus""")
    List<Object[]> brojaciPoGrupi(@Param("ids") Collection<Long> ids);

    /** Sve varijante predmeta (ponovni render cele banke posle izmene preambule). */
    @Query("select v from Varijanta v where v.grupa.predmet.id = :predmetId")
    List<Varijanta> svePredmeta(@Param("predmetId") Long predmetId);

    /** Poništava keš rendera cele banke predmeta (preambula je ulaz heša); vraća broj izmenjenih varijanti. */
    @Modifying
    @Query("update Varijanta v set v.renderHash = null where v.grupa.predmet.id = :predmetId")
    int ponistiHashPredmeta(@Param("predmetId") Long predmetId);
}
