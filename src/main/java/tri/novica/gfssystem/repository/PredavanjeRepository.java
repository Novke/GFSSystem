package tri.novica.gfssystem.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predavanje;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredavanjeRepository extends JpaRepository<Predavanje, Long>, JpaSpecificationExecutor<Predavanje> {

    @Query("select max(p.rb) from Predavanje p")
    Integer findPoslednjiRB();
    List<Predavanje> findAllByGrupaIdAndPredmetIdOrderByDatumAsc(Long grupaId, Long predmetId);

    /** Poslednje predavanje po (datum, id); predlog sledećeg na kontrolnoj tabli. */
    Optional<Predavanje> findFirstByOrderByDatumDescIdDesc();

    /** Predavanja grupe, opciono jednog predmeta ({@code predmetId} null = svi). */
    @Query("select count(p) from Predavanje p where p.grupa.id = :grupaId and (:predmetId is null or p.predmet.id = :predmetId)")
    long brojPredavanjaGrupe(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);

    /**
     * Za kartice studenta: po predmetu broj predavanja grupe plus drugih predavanja na kojima je student bio (svako
     * jednom). Redovi {@code [predmetId, broj]}; {@code grupaId} null = samo ona na kojima je bio.
     */
    @Query("""
            select p.predmet.id, count(p) from Predavanje p left join p.grupa g
            where g.id = :grupaId
               or exists (select a.id from Aktivnost a where a.predavanje = p and a.student.id = :studentId)
            group by p.predmet.id""")
    List<Object[]> brojPredavanjaZaStudentaPoPredmetu(@Param("grupaId") Long grupaId, @Param("studentId") Long studentId);

    /** Predavanja predmeta za pokretanje prezentacije: nezavršena ili od {@code danas}, najnovija prva. */
    @Query("""
            select p from Predavanje p join fetch p.predmet left join fetch p.grupa
            where p.predmet.id = :predmetId
              and (p.zavrseno is null or p.zavrseno = false or p.datum = :danas)
            order by p.datum desc, p.rb desc""")
    List<Predavanje> findZaPokretanje(@Param("predmetId") Long predmetId, @Param("danas") LocalDate danas,
                                      Pageable pageable);
}
