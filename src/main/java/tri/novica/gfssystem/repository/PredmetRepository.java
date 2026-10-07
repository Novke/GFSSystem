package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predmet;

import java.util.List;

@Repository
public interface PredmetRepository extends JpaRepository<Predmet, Long> {

    /**
     * Predmeti za kartice studenta: grupa ima bar jedno predavanje, test ili domaći, ili student ima aktivnost ili
     * polaganje. Po nazivu. {@code grupaId} null (student bez grupe) = samo njegovi tragovi.
     */
    @Query("""
            select pr from Predmet pr
            where exists (select p.id from Predavanje p where p.predmet = pr and p.grupa.id = :grupaId)
               or exists (select t.id from Test t where t.predmet = pr and t.grupa.id = :grupaId)
               or exists (select d.id from Domaci d where d.predmet = pr and d.grupa.id = :grupaId)
               or exists (select a.id from Aktivnost a where a.predavanje.predmet = pr and a.student.id = :studentId)
               or exists (select po.id from Polaganje po where po.test.predmet = pr and po.student.id = :studentId)
            order by pr.naziv, pr.id""")
    List<Predmet> predmetiStudenta(@Param("studentId") Long studentId, @Param("grupaId") Long grupaId);
}
