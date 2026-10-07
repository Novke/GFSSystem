package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Aktivnost;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.Student;

import java.util.Collection;
import java.util.List;

@Repository
public interface AktivnostRepository extends JpaRepository<Aktivnost, Long> {
    List<Aktivnost> findAllByStudentGrupaAndPredavanjePredmet(Grupa grupa, Predmet predmet);

    List<Aktivnost> findAllByStudentAndPredavanjePredmetOrderByPredavanjeDatumAsc(Student student, Predmet predmet);

    /**
     * Broj različitih prisutnih studenata po predavanju, za stranicu liste ({@code Brojaci.poId}). Svaka aktivnost
     * (prisustvo, zadatak, zvezdica) znači da je student bio prisutan. Redovi: {@code [predavanjeId, broj]}.
     */
    @Query("select a.predavanje.id, count(distinct a.student.id) from Aktivnost a where a.predavanje.id in :ids group by a.predavanje.id")
    List<Object[]> brojPrisutnihPoPredavanju(@Param("ids") Collection<Long> ids);
}
