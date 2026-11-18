package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.OznakaPonasanja;
import tri.novica.gfssystem.entity.uzivo.VrstaOznake;

import java.util.Collection;
import java.util.List;

@Repository
public interface OznakaPonasanjaRepository extends JpaRepository<OznakaPonasanja, Long> {

    /** Karton: oznake studenta, najnovije prve. */
    List<OznakaPonasanja> findAllByStudentIdOrderByKreiranoDescIdDesc(Long studentId);

    /** Da li student ima oznaku te vrste na predavanju (zabrana "do kraja predavanja"). */
    boolean existsByStudentIdAndPredavanjeIdAndVrsta(Long studentId, Long predavanjeId, VrstaOznake vrsta);

    /** Studenti sa oznakom te vrste na predavanju (brojanje očekivanih u konzoli). */
    @Query("select distinct o.student.id from OznakaPonasanja o where o.predavanjeId = :p and o.vrsta = :v")
    List<Long> findStudentIds(@Param("p") Long predavanjeId, @Param("v") VrstaOznake vrsta);

    /**
     * Za konzolu, po učesniku izvođenja iza kog je student: {@code [ucesnikId, brojOznaka, brojNaOvomCasu]}, gde je "ovaj čas"
     * predavanje izvođenja ili, bez predavanja, samo izvođenje. Jedan upit po snimku; gost nema red.
     */
    @Query("""
            select u.id, count(o), sum(case when o.predavanjeId = :pred or o.izvodjenjeId = :iz then 1 else 0 end)
            from Ucesnik u, OznakaPonasanja o
            where u.izvodjenje.id = :iz and u.student is not null and o.student = u.student
            group by u.id""")
    List<Object[]> zbirPoUcesniku(@Param("iz") Long izvodjenjeId, @Param("pred") Long predavanjeId);

    /** Vraćanje sakrivenog odgovora briše oznake te runde za te studente. */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from OznakaPonasanja o
            where o.rundaId = :runda and o.vrsta = :v and o.student.id in :studenti""")
    int obrisiZaRundu(@Param("runda") Long rundaId, @Param("v") VrstaOznake vrsta,
                      @Param("studenti") Collection<Long> studentIds);
}
