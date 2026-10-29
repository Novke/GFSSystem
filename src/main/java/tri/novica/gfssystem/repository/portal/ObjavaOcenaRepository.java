package tri.novica.gfssystem.repository.portal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.portal.ObjavaOcena;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ObjavaOcenaRepository extends JpaRepository<ObjavaOcena, Long> {

    Optional<ObjavaOcena> findByPredmetIdAndGrupaId(Long predmetId, Long grupaId);

    /**
     * Red objave "ako ne postoji" ({@code UNIQUE (predmet, grupa)}), pre zaključavanja: {@code SELECT ... FOR UPDATE} nad
     * redom koga još nema uzima samo gap lock, pa bi dupli klik na prvu objavu dao deadlock ili dupli ključ. Postojeći red se
     * ne menja (ali ostaje zaključan do kraja transakcije).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO objave_ocena (predmet_id, grupa_id, objavljeno) VALUES (:predmetId, :grupaId, :sada)
            ON DUPLICATE KEY UPDATE id = id""", nativeQuery = true)
    int obezbedi(@Param("predmetId") Long predmetId, @Param("grupaId") Long grupaId, @Param("sada") LocalDateTime sada);

    /** Isto, zaključano do kraja transakcije (objava i povlačenje iste grupe idu jedno za drugim). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ObjavaOcena o where o.predmetId = :predmetId and o.grupaId = :grupaId")
    Optional<ObjavaOcena> findForUpdate(@Param("predmetId") Long predmetId, @Param("grupaId") Long grupaId);
}
