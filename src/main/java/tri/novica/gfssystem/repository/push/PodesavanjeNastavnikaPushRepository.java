package tri.novica.gfssystem.repository.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.push.PodesavanjeNastavnikaPush;

import java.time.LocalDateTime;

@Repository
public interface PodesavanjeNastavnikaPushRepository extends JpaRepository<PodesavanjeNastavnikaPush, String> {

    /** Upis prekidača jednim upitom (dva brza klika ne daju dupli ključ). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO podesavanja_nastavnika_push (vrsta, ukljuceno, izmenjeno) VALUES (:vrsta, :ukljuceno, :sada) AS novo
            ON DUPLICATE KEY UPDATE ukljuceno = novo.ukljuceno, izmenjeno = novo.izmenjeno""", nativeQuery = true)
    int upisi(@Param("vrsta") String vrsta, @Param("ukljuceno") boolean ukljuceno, @Param("sada") LocalDateTime sada);
}
