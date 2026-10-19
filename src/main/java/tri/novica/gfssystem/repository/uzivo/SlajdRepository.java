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

    /**
     * Id-jevi prezentacija sa bar jednim slajdom serije, rastuće (zaključavaju se tim redom). Samo id-jevi: entitet učitan
     * pre zaključavanja ostao bi u persistence context-u sa stanjem od pre čekanja na zaključavanje.
     */
    @Query("select distinct s.prezentacija.id from Slajd s where s.serija = :serija order by s.prezentacija.id")
    List<Long> findPrezentacijaIdsBySerija(@Param("serija") String serija);

    /**
     * Brojači serija ({@code SerijaService}), jedan upit: redovi {@code [serija, slajdova, najnovije izmenjeno njihovih
     * prezentacija]} (slajd nema svoje vreme); {@code predmetId == null} = svi predmeti.
     */
    @Query("""
            select s.serija, count(s), max(p.izmenjeno) from Slajd s join s.prezentacija p
            where s.serija is not null and (:predmetId is null or p.predmet.id = :predmetId) group by s.serija""")
    List<Object[]> brojaciPoSeriji(@Param("predmetId") Long predmetId);
}
