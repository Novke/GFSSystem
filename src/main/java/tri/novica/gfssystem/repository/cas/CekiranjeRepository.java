package tri.novica.gfssystem.repository.cas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.cas.Cekiranje;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CekiranjeRepository extends JpaRepository<Cekiranje, Long> {

    Optional<Cekiranje> findByPredavanjeIdAndStudentId(Long predavanjeId, Long studentId);

    /** Čekirani na predavanju, po vremenu, sa studentom (spisak za nastavnika). */
    @Query("select c from Cekiranje c join fetch c.student where c.predavanje.id = :predavanjeId order by c.vreme asc, c.id asc")
    List<Cekiranje> findAllByPredavanjeIdOrderByVremeAsc(@Param("predavanjeId") Long predavanjeId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Cekiranje c where c.predavanje.id = :predavanjeId and c.student.id = :studentId")
    int deleteByPredavanjeIdAndStudentId(@Param("predavanjeId") Long predavanjeId, @Param("studentId") Long studentId);

    /**
     * Najnovije čekiranje studenta od {@code od} (završeno predavanje ili ne), sa predavanjem, predmetom i grupom: stanje
     * časa na telefonu. Lista zbog {@code join fetch} (prvi element je najnoviji).
     */
    @Query("""
            select c from Cekiranje c join fetch c.predavanje p join fetch p.predmet left join fetch p.grupa
            where c.student.id = :studentId and c.vreme >= :od
            order by c.vreme desc, c.id desc""")
    List<Cekiranje> poslednjaNaCasu(@Param("studentId") Long studentId, @Param("od") LocalDateTime od,
                                    org.springframework.data.domain.Pageable pageable);

    default Optional<Cekiranje> findPoslednjeNaCasu(Long studentId, LocalDateTime od) {
        return poslednjaNaCasu(studentId, od, org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst();
    }

    /** Studenti koji su "na času" na živom predavanju: čekirani od {@code od} (brojanje u konzoli). */
    @Query("""
            select c.student.id from Cekiranje c
            where c.predavanje.id = :predavanjeId and c.vreme >= :od
              and (c.predavanje.zavrseno is null or c.predavanje.zavrseno = false)""")
    List<Long> studentiNaCasu(@Param("predavanjeId") Long predavanjeId, @Param("od") LocalDateTime od);

    /** Da li je student čekiran na predavanju od {@code od} (bodovano izvođenje sa predavanja traži čekiranje). */
    boolean existsByPredavanjeIdAndStudentIdAndVremeGreaterThanEqual(Long predavanjeId, Long studentId, LocalDateTime od);
}
