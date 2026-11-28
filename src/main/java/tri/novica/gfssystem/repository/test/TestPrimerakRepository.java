package tri.novica.gfssystem.repository.test;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.test.StanjePrimerka;
import tri.novica.gfssystem.entity.test.TestPrimerak;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TestPrimerakRepository extends JpaRepository<TestPrimerak, Long> {

    @Query("select p from TestPrimerak p join fetch p.student where p.testId = :testId order by p.student.prezime, p.student.ime, p.id")
    List<TestPrimerak> zaTest(@Param("testId") Long testId);

    /** Svi primerci testa pod ključem (zatvaranje smene, istek, ispravke): red je po id-ju, isti za sve. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TestPrimerak p where p.testId = :testId order by p.id")
    List<TestPrimerak> zaTestForUpdate(@Param("testId") Long testId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TestPrimerak p where p.id = :id")
    Optional<TestPrimerak> findByIdForUpdate(@Param("id") Long id);

    Optional<TestPrimerak> findByTestIdAndStudentId(Long testId, Long studentId);

    /**
     * Primerak studenta pod ključem, bez prethodnog čitanja: entitet učitan pre zaključavanja u istoj sesiji se posle
     * {@code FOR UPDATE} ne osvežava, pa bi druga od dve istovremene predaje videla staro stanje.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TestPrimerak p where p.testId = :testId and p.student.id = :studentId")
    Optional<TestPrimerak> zaTestIStudentaForUpdate(@Param("testId") Long testId, @Param("studentId") Long studentId);

    boolean existsByTestIdAndStanje(Long testId, StanjePrimerka stanje);

    long countByTestId(Long testId);

    @Query("select p from TestPrimerak p where p.student.id = :studentId and p.testId in :testIds")
    List<TestPrimerak> zaStudentaITestove(@Param("studentId") Long studentId, @Param("testIds") Collection<Long> testIds);

    @Query("select p from TestPrimerak p where p.student.id = :studentId")
    List<TestPrimerak> zaStudenta(@Param("studentId") Long studentId);
}
