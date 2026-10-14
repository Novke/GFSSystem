package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PitanjeRundaRepository extends JpaRepository<PitanjeRunda, Long> {

    List<PitanjeRunda> findAllByIzvodjenjeIdOrderByOtvorenoAsc(Long id);

    /** Runde izvođenja po redu otvaranja (id razrešava isti trenutak). */
    List<PitanjeRunda> findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(Long id);

    /** Poslednja runda tog slajda u izvođenju (pitanje se može otvoriti ponovo). */
    Optional<PitanjeRunda> findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(Long izvodjenjeId, Long slajdId);

    Optional<PitanjeRunda> findByIdAndIzvodjenjeId(Long id, Long izvodjenjeId);

    /** Broj rundi po izvođenju: redovi {@code [izvodjenjeId, broj]}. */
    @Query("select r.izvodjenje.id, count(r) from PitanjeRunda r where r.izvodjenje.id in :ids group by r.izvodjenje.id")
    List<Object[]> brojPoIzvodjenju(@Param("ids") Collection<Long> ids);

    /** Briše sve runde izvođenja jednim upitom (posle odgovora; "ne čuvaj" na kraju). */
    @Modifying(flushAutomatically = true)
    @Query("delete from PitanjeRunda r where r.izvodjenje.id = :id")
    void deleteAllByIzvodjenjeId(@Param("id") Long id);
}
