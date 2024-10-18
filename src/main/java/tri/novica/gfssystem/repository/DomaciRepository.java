package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Domaci;

import java.util.Optional;

@Repository
public interface DomaciRepository extends JpaRepository<Domaci, Long> {
    @Query("""
            SELECT d FROM Domaci d
                   JOIN FETCH d.predmet p
                   JOIN FETCH d.grupa g
                   JOIN FETCH d.predavanje pr
                   LEFT JOIN FETCH d.uradjeniDomaci u
                   WHERE d.id = :id""")
    Optional<Domaci> findDomaciPlusGrupaPredmetPredavanje(Long id);
}
