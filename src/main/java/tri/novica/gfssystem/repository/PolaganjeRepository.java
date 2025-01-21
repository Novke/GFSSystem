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
}
