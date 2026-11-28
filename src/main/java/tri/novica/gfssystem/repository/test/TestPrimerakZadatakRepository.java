package tri.novica.gfssystem.repository.test;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.test.TestPrimerakZadatak;

import java.util.Collection;
import java.util.List;

public interface TestPrimerakZadatakRepository extends JpaRepository<TestPrimerakZadatak, Long> {

    List<TestPrimerakZadatak> findByPrimerakIdOrderByRb(Long primerakId);

    @Query("select z from TestPrimerakZadatak z where z.primerakId in :ids order by z.primerakId, z.rb")
    List<TestPrimerakZadatak> zaPrimerke(@Param("ids") Collection<Long> primerakIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from TestPrimerakZadatak z where z.primerakId = :primerakId order by z.rb")
    List<TestPrimerakZadatak> zaPrimerakForUpdate(@Param("primerakId") Long primerakId);

    @Modifying
    @Query("delete from TestPrimerakZadatak z where z.primerakId = :primerakId")
    int obrisiZaPrimerak(@Param("primerakId") Long primerakId);

    /**
     * Da li je varijanta na nekom testu: u zadatku primerka ili u planu izvlačenja (oba su FK RESTRICT). Takva se ne briše
     * nego arhivira ({@code Korišćeno na testu; arhiviraj.}).
     */
    @Query(value = "select (exists(select 1 from test_primerak_zadaci where varijanta_id = :id) "
            + "or exists(select 1 from test_izbor where varijanta_id = :id))", nativeQuery = true)
    Long varijantaNaTestu(@Param("id") Long varijantaId);

    /** Kao {@link #varijantaNaTestu}, za sve varijante grupe. */
    @Query(value = "select (exists(select 1 from test_primerak_zadaci z join varijante v on v.id = z.varijanta_id where v.grupa_id = :g) "
            + "or exists(select 1 from test_izbor i join varijante v on v.id = i.varijanta_id where v.grupa_id = :g))",
            nativeQuery = true)
    Long grupaNaTestu(@Param("g") Long grupaId);

    /**
     * Varijante koje su studenti imali na ranijim online testovima istog predmeta (osim testa {@code testId}), redovi
     * {@code [studentId, varijantaId]}; za izbegavanje ponavljanja.
     */
    @Query(value = """
            select p.student_id, z.varijanta_id from test_primerak_zadaci z
              join test_primerci p on p.id = z.primerak_id
              join testovi t on t.id = p.test_id
             where t.predmet_id = :predmetId and t.nacin = 'ONLINE' and t.id <> :testId and p.student_id in :studenti
            """, nativeQuery = true)
    List<Object[]> ranijeVarijante(@Param("predmetId") Long predmetId, @Param("testId") Long testId,
                                   @Param("studenti") Collection<Long> studenti);

    /** Varijante čija su tačna rešenja objavljena (objava sa "Pokaži tačne odgovore"): generator ih izbegava kao viđene. */
    @Query(value = """
            select distinct z.varijanta_id from test_primerak_zadaci z
              join test_primerci p on p.id = z.primerak_id
              join testovi t on t.id = p.test_id
             where t.predmet_id = :predmetId and t.objavljeno is not null and t.objava_tacni = true and t.id <> :testId
            """, nativeQuery = true)
    List<Long> objavljenaResenja(@Param("predmetId") Long predmetId, @Param("testId") Long testId);
}
