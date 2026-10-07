package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UcesnikRepository extends JpaRepository<Ucesnik, Long> {

    Optional<Ucesnik> findByTokenHash(String h);

    List<Ucesnik> findAllByIzvodjenjeIdOrderByKreiranoAsc(Long id);

    long countByIzvodjenjeIdAndIzbacenFalse(Long id);

    Optional<Ucesnik> findByIdAndIzvodjenjeId(Long id, Long izvodjenjeId);

    /** Broj neizbačenih učesnika po izvođenju: redovi {@code [izvodjenjeId, broj]}. */
    @Query("""
            select u.izvodjenje.id, count(u) from Ucesnik u
            where u.izvodjenje.id in :ids and u.izbacen = false group by u.izvodjenje.id""")
    List<Object[]> brojPoIzvodjenju(@Param("ids") Collection<Long> ids);

    /** Briše sve učesnike izvođenja jednim upitom (posle odgovora; "ne čuvaj" na kraju). */
    @Modifying(flushAutomatically = true)
    @Query("delete from Ucesnik u where u.izvodjenje.id = :id")
    void deleteAllByIzvodjenjeId(@Param("id") Long id);
}
