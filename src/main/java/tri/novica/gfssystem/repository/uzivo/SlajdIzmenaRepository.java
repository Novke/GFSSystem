package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.uzivo.SlajdIzmena;
import tri.novica.gfssystem.entity.uzivo.VrstaIzmene;

import java.util.List;

public interface SlajdIzmenaRepository extends JpaRepository<SlajdIzmena, Long> {

    List<SlajdIzmena> findBySlajdIdOrderByIdDesc(Long slajdId, Pageable pageable);

    List<SlajdIzmena> findByPrezentacijaIdOrderByIdDesc(Long prezentacijaId, Pageable pageable);

    boolean existsBySlajdId(Long slajdId);

    /** Id-jevi unosa slajda od najnovijeg, bez {@code osim} (brisanje se nikad ne čisti). */
    @Query("select i.id from SlajdIzmena i where i.slajdId = :slajdId and i.vrsta <> :osim order by i.id desc")
    List<Long> idsZaCiscenje(@Param("slajdId") Long slajdId, @Param("osim") VrstaIzmene osim, Pageable pageable);

    /** Obnovljen slajd dobija nov id; njegova istorija ide za njim. */
    @Modifying
    @Query("update SlajdIzmena i set i.slajdId = :novi where i.slajdId = :stari")
    int preusmeri(@Param("stari") Long stari, @Param("novi") Long novi);
}
