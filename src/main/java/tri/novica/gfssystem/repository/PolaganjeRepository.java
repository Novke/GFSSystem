package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.TipTesta;

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
}
