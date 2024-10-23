package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Domaci;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;

import java.util.List;
import java.util.Optional;

@Repository
public interface DomaciRepository extends JpaRepository<Domaci, Long> {
    @Query("""
            SELECT d FROM Domaci d
                   JOIN FETCH d.predmet p
                   JOIN FETCH d.grupa g
                   LEFT JOIN FETCH d.predavanje pr
                   LEFT JOIN FETCH d.uradjeniDomaci u
                   WHERE d.id = :id""")
    Optional<Domaci> findDomaciPlusGrupaPredmetPredavanje(Long id);

    List<Domaci> findAllByGrupaAndPredmetOrderByDatumAsc(Grupa grupa, Predmet predmet);
}
