package tri.novica.gfssystem.repository.anketa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.anketa.AnketaSablon;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnketaSablonRepository extends JpaRepository<AnketaSablon, Long> {

    long countByPrecicaTrue();

    @Query("select coalesce(max(s.precicaRb), 0) from AnketaSablon s where s.precica = true")
    int najveciPrecicaRb();

    /** Najveći {@code rb} u folderu ({@code folderId} {@code null} = bez foldera); 0 kad je prazan. */
    @Query("""
            select coalesce(max(s.rb), 0) from AnketaSablon s
            where (:folderId is null and s.folderId is null) or s.folderId = :folderId""")
    int najveciRb(@Param("folderId") Long folderId);

    /** Redovi {@code [folderId, broj]} šablona po folderu. */
    @Query("select s.folderId, count(s) from AnketaSablon s where s.folderId is not null group by s.folderId")
    List<Object[]> brojPoFolderu();

    /** Pokretanje iz šablona: brojač i vreme (šablon se inače ne menja). Vraća 0 za nepostojeći šablon. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update AnketaSablon s set s.brojPokretanja = s.brojPokretanja + 1, s.poslednjePokretanje = :sada
            where s.id = :id""")
    int pokrenut(@Param("id") Long id, @Param("sada") LocalDateTime sada);

    /** Šabloni obrisanog foldera prelaze u "bez foldera", na kraj ({@code rb + pomak}). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AnketaSablon s set s.folderId = null, s.rb = s.rb + :pomak where s.folderId = :folderId")
    int bezFoldera(@Param("folderId") Long folderId, @Param("pomak") int pomak);
}
