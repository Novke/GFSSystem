package tri.novica.gfssystem.repository.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.portal.PrikazanSlajd;

import java.time.LocalDateTime;

@Repository
public interface PrikazaniSlajdoviRepository extends JpaRepository<PrikazanSlajd, Long> {

    /**
     * Upis prikazanog slajda "ako ne postoji" (UNIQUE (grupa, slajd, izvođenje): ponovni prikaz u istom izvođenju ne menja
     * prvi zapis). Bez izuzetka za duplikat, pa komanda konzole nikad ne pada zbog ovoga.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO prikazani_slajdovi (grupa_id, slajd_id, izvodjenje_id, prvi_put)
            VALUES (:grupaId, :slajdId, :izvodjenjeId, :sada)
            ON DUPLICATE KEY UPDATE id = id""", nativeQuery = true)
    int upisi(@Param("grupaId") Long grupaId, @Param("slajdId") Long slajdId, @Param("izvodjenjeId") Long izvodjenjeId,
              @Param("sada") LocalDateTime sada);
}
