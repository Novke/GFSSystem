package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.ZabranaGosta;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ZabranaGostaRepository extends JpaRepository<ZabranaGosta, Long> {

    /** Zabrane tokena koje važe za izvođenje: za njega samog, ili za njegovo predavanje. */
    @Query("""
            select z from ZabranaGosta z
            where z.tokenHash = :h and (z.izvodjenjeId = :iz or (:pred is not null and z.predavanjeId = :pred))""")
    List<ZabranaGosta> vazece(@Param("h") String tokenHash, @Param("iz") Long izvodjenjeId,
                              @Param("pred") Long predavanjeId);

    List<ZabranaGosta> findAllByTokenHash(String tokenHash);

    /** Čišćenje: kolačić živi 12 h, red se drži 2 dana. */
    @Modifying
    @Query("delete from ZabranaGosta z where z.kreirano < :pre")
    int obrisiStarije(@Param("pre") LocalDateTime pre);
}
