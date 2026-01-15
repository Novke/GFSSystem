package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Aktivnost;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.Student;

import java.util.List;

@Repository
public interface AktivnostRepository extends JpaRepository<Aktivnost, Long> {
    List<Aktivnost> findAllByStudentGrupaAndPredavanjePredmet(Grupa grupa, Predmet predmet);

    List<Aktivnost> findAllByStudentAndPredavanjePredmetOrderByPredavanjeDatumAsc(Student student, Predmet predmet);
}
