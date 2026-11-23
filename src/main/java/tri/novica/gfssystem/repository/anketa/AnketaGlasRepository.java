package tri.novica.gfssystem.repository.anketa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.anketa.AnketaGlas;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface AnketaGlasRepository extends JpaRepository<AnketaGlas, Long> {

    /**
     * Glas jednim upitom: nov red ili zamena postojećeg pod {@code UNIQUE(anketa_id, student_id)}, pa dvostruki dodir (dva
     * paralelna zahteva) daje jedan red.
     */
    @Modifying
    @Query(value = """
            INSERT INTO ankete_glasovi (anketa_id, student_id, opcije, vreme) VALUES (:anketaId, :studentId, :opcije, :vreme) AS novo
            ON DUPLICATE KEY UPDATE opcije = novo.opcije, vreme = novo.vreme""", nativeQuery = true)
    int upisi(@Param("anketaId") Long anketaId, @Param("studentId") Long studentId, @Param("opcije") String opcije,
              @Param("vreme") LocalDateTime vreme);

    /** Zbirovi bez studenta: redovi {@code [anketaId, opcije]} glasova datih anketa. */
    @Query("select g.anketaId, g.opcije from AnketaGlas g where g.anketaId in :ids")
    List<Object[]> opcijeGlasova(@Param("ids") Collection<Long> ids);

    /** Glasovi jednog studenta u datim anketama (njegov "mojGlas"). */
    List<AnketaGlas> findAllByStudentIdAndAnketaIdIn(Long studentId, Collection<Long> anketaIds);

    /**
     * Koliko studenata sme da glasa, po anketi: čekirani na predavanju ankete, za {@code GRUPA} i studenti grupe, plus oni koji
     * su već glasali (student kome je posle sklonjeno prisustvo ili promenjena grupa ostaje u broju svog glasa, pa
     * "još K nije glasalo" nikad nije negativno). Redovi {@code [anketaId, broj]}.
     */
    @Query(value = """
            SELECT x.anketa_id, COUNT(DISTINCT x.student_id) FROM (
              SELECT a.id AS anketa_id, c.student_id FROM ankete a JOIN cekiranja c ON c.predavanje_id = a.predavanje_id
              WHERE a.id IN (:ids)
              UNION ALL
              SELECT a.id, s.id FROM ankete a JOIN studenti s ON s.grupa_id = a.grupa_id
              WHERE a.id IN (:ids) AND a.publika = 'GRUPA'
              UNION ALL
              SELECT g.anketa_id, g.student_id FROM ankete_glasovi g WHERE g.anketa_id IN (:ids)
            ) x GROUP BY x.anketa_id""", nativeQuery = true)
    List<Object[]> brojMogucih(@Param("ids") Collection<Long> ids);
}
