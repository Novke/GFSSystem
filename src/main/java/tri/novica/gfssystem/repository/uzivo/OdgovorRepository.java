package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Odgovor;

import java.util.List;
import java.util.Optional;

@Repository
public interface OdgovorRepository extends JpaRepository<Odgovor, Long> {

    /** Odgovori runde sa učesnikom (bez N+1), po vremenu prijema pa id-ju (stabilan redosled za rezultat). */
    @Query("select o from Odgovor o join fetch o.ucesnik where o.runda.id = :id order by o.kreirano, o.id")
    List<Odgovor> findAllByRundaId(@Param("id") Long id);

    /** Svi odgovori izvođenja sa rundom i učesnikom (bez N+1), po vremenu prijema pa id-ju. */
    @Query("""
            select o from Odgovor o join fetch o.runda r join fetch o.ucesnik
            where r.izvodjenje.id = :id order by o.kreirano, o.id""")
    List<Odgovor> findAllByRundaIzvodjenjeId(@Param("id") Long id);

    boolean existsByRundaIdAndUcesnikId(Long r, Long u);

    long countByRundaId(Long id);

    Optional<Odgovor> findByRundaIdAndUcesnikId(Long r, Long u);

    /** Briše sve odgovore izvođenja jednim upitom ("ne čuvaj" na kraju). */
    @Modifying(flushAutomatically = true)
    @Query("delete from Odgovor o where o.runda.id in (select r.id from PitanjeRunda r where r.izvodjenje.id = :id)")
    void deleteAllByIzvodjenjeId(@Param("id") Long izvodjenjeId);
}
