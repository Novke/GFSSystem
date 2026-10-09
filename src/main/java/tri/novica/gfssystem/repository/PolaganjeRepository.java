package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.TipTesta;

import java.util.Collection;
import java.util.List;

@Repository
public interface PolaganjeRepository extends JpaRepository<Polaganje, Long> {

    List<Polaganje> findAllByTestTipTestaAndTestGrupa(TipTesta tipTesta, Grupa grupa);

    @Query("SELECT MAX(p.ostvareniPoeni) " +
            "FROM Polaganje p " +
            "WHERE p.student.id = :studentId " +
            "AND p.test.tipTesta.id = :tipTestaId")
    Double findMaxPoeniByStudentAndTipTesta(@Param("studentId") Long studentId,
                                            @Param("tipTestaId") Long tipTestaId);

    @Query("SELECT p.ostvareniPoeni FROM Polaganje p " +
            "WHERE p.student.id = :studentId AND p.test.tipTesta.id = :tipTestaId " +
            "ORDER BY p.test.datum DESC LIMIT 1")
    Double findPoslednjiPoeniByStudentAndTipTesta(@Param("studentId") Long studentId,
                                                   @Param("tipTestaId") Long tipTestaId);

    @Query("SELECT t.maxPoena FROM Test t " +
            "WHERE t.tipTesta.id = :tipTestaId " +
            "ORDER BY t.datum DESC LIMIT 1")
    Double findMaxPoenaTestByTipTesta(@Param("tipTestaId") Long tipTestaId);

    @Query("SELECT p FROM Polaganje p " +
            "JOIN FETCH p.test t " +
            "JOIN FETCH t.tipTesta " +
            "WHERE p.student.id = :studentId AND t.predmet.id = :predmetId " +
            "ORDER BY t.datum ASC")
    List<Polaganje> findAllByStudentAndPredmet(@Param("studentId") Long studentId,
                                                @Param("predmetId") Long predmetId);

    /**
     * Statistika polaganja po testu, za stranicu liste. Redovi: {@code [testId, brojPolaganja, brojSaPoenima,
     * prosekPoena, brojPolozenih]}; prosek i položeni računaju samo polaganja sa {@code ostvareniPoeni != null}
     * (prolaz po {@link tri.novica.gfssystem.utility.Prolaz}: test sa pragom, poeni >= prag, bez prepisivanja; {@code polozio}
     * se ignoriše), a testovi bez polaganja se ne vraćaju.
     */
    @Query("""
            select t.id, count(p), count(p.ostvareniPoeni), avg(p.ostvareniPoeni),
                   coalesce(sum(case when p.ostvareniPoeni is not null and t.pragProlaza is not null
                                      and p.ostvareniPoeni >= t.pragProlaza
                                      and (p.prepisivao is null or p.prepisivao = false)
                                     then 1 else 0 end), 0)
            from Polaganje p join p.test t where t.id in :ids group by t.id""")
    List<Object[]> statistikaPoTestu(@Param("ids") Collection<Long> ids);

    /**
     * Poslednje polaganje (po datumu testa, pa id-ju testa) svakog studenta grupe, opciono na jednom predmetu. Redovi
     * {@code [studentId, testId, nazivTipa, datum, poeni, maxPoena]}, prvi red studenta je poslednji (testovi bez datuma
     * i dva polaganja istog testa mogu dati više redova, zato redosled).
     */
    @Query("""
            select p.student.id, t.id, tt.naziv, t.datum, p.ostvareniPoeni, t.maxPoena
            from Polaganje p join p.test t join t.tipTesta tt
            where p.student.grupa.id = :grupaId and (:predmetId is null or t.predmet.id = :predmetId)
              and not exists (select p2.id from Polaganje p2 join p2.test t2
                              where p2.student = p.student and (:predmetId is null or t2.predmet.id = :predmetId)
                                and (t2.datum > t.datum or (t2.datum = t.datum and t2.id > t.id)))
            order by p.student.id, t.datum desc, t.id desc, p.id desc""")
    List<Object[]> poslednjaPolaganjaGrupe(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);
}
