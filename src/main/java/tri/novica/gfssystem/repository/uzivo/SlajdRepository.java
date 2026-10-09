package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlajdRepository extends JpaRepository<Slajd, Long> {

    /** Slajdovi po {@code rb}, sa slikom, pitanjem i opcijama u istom upitu (bez N+1 pri listanju). */
    @EntityGraph(attributePaths = {"slika", "pitanje", "pitanje.slika", "pitanje.opcije"})
    List<Slajd> findAllByPrezentacijaIdOrderByRbAsc(Long id);

    long countByPrezentacijaId(Long id);

    long countByPrezentacijaIdAndTip(Long id, TipSlajda tip);

    @Query("select s.prezentacija.id from Slajd s where s.id = :id")
    Optional<Long> findPrezentacijaId(@Param("id") Long slajdId);
}
