package tri.novica.gfssystem.repository.fajlovi;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Dva jeftina upita za uživo stanje (šalje se do 4 puta u sekundi): fajlovi prezentacije i važeći kod izvođenja.
 * Kod se čita native upitom bez entiteta, jer {@code KodoviFajlova} menja redove native upisima pa bi keš sesije bio zastareo.
 */
public interface FajloviStanjaRepository extends Repository<FajlPrezentacije, Long> {

    /**
     * Svi fajlovi prezentacije, redom {@code rb}, id; medij je u istom upitu (veličina). Mesto (slajd ili prijava) bira
     * pozivalac u memoriji, jer i pravilo trake gleda ima li fajlova uz celu prezentaciju (uz prezentaciju ih je malo).
     * Slajd se namerno ne učitava (PLATNO kolona do 1 MB): pozivalac čita samo {@code getSlajd().getId()} iz proksija.
     */
    @Query("""
            select f from FajlPrezentacije f join fetch f.medij
            where f.prezentacija.id = :prezentacijaId
            order by f.rb, f.id""")
    List<FajlPrezentacije> zaPrezentaciju(@Param("prezentacijaId") Long prezentacijaId);

    /**
     * Važeći kod za izvođenje (kod njegovog predavanja koje nije završeno, ili njegov kod); da li je izvođenje AKTIVNO
     * proverava pozivalac.
     */
    @Query(value = """
            SELECT k.aktivan_kod FROM kodovi_fajlova k
            LEFT JOIN predavanja p ON p.id = k.predavanje_id
            WHERE k.aktivan_kod IS NOT NULL AND k.vazi_do > :sada
              AND ((k.predavanje_id = :predavanjeId AND COALESCE(p.zavrseno, 0) = 0) OR k.izvodjenje_id = :izvodjenjeId)
            LIMIT 1""", nativeQuery = true)
    Optional<String> vazeciKod(@Param("predavanjeId") Long predavanjeId, @Param("izvodjenjeId") Long izvodjenjeId,
                               @Param("sada") LocalDateTime sada);
}
