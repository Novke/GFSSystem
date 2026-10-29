package tri.novica.gfssystem.repository.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.portal.OtvorenaTema;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface OtvorenaTemaRepository extends JpaRepository<OtvorenaTema, Long> {

    /** Zapisi zadatih grupa studenata (vežbanje: otvorene teme svih grupa studenta). */
    List<OtvorenaTema> findAllByGrupaIdIn(Collection<Long> grupaIds);

    /** Zapisi za zadate čvorove (panel "Za vežbanje": čvor, preci i potomci). */
    List<OtvorenaTema> findAllByTemaIdIn(Collection<Long> temaIds);

    /**
     * Otvara čvor grupi "ako ne postoji" (UNIQUE (grupa, čvor); postojeći zapis ostaje sa svojim izvorom i vremenom). Čvor koji
     * u međuvremenu nije postojao ne upisuje ništa (SELECT iz {@code grupe_zadataka}), pa kraj izvođenja nikad ne pada na FK.
     * Vraća broj upisanih redova (0 ili 1; MySQL za ON DUPLICATE bez izmene daje 0).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO otvorene_teme (grupa_id, grupa_zadataka_id, prvi_put, izvor, izvodjenje_id)
            SELECT :grupaId, gz.id, :sada, :izvor, :izvodjenjeId FROM grupe_zadataka gz WHERE gz.id = :temaId
            ON DUPLICATE KEY UPDATE otvorene_teme.id = otvorene_teme.id""", nativeQuery = true)
    int otvoriAkoNema(@Param("grupaId") Long grupaId, @Param("temaId") Long temaId, @Param("sada") LocalDateTime sada,
                      @Param("izvor") String izvor, @Param("izvodjenjeId") Long izvodjenjeId);

    @Modifying(flushAutomatically = true)
    @Query("delete from OtvorenaTema o where o.grupaId = :grupaId and o.temaId in :temaIds")
    int zatvori(@Param("grupaId") Long grupaId, @Param("temaIds") Collection<Long> temaIds);
}
