package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.Pokusaj;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;

import java.time.LocalDateTime;

@Repository
public interface PokusajRepository extends JpaRepository<Pokusaj, Long> {

    /** Broj pokušaja vrste za ključ posle trenutka (indeks {@code vrsta, kljuc, vreme}). */
    long countByVrstaAndKljucAndVremeAfter(VrstaPokusaja vrsta, String kljuc, LocalDateTime posle);

    /** Briše pokušaje starije od granice (čišćenje); vraća broj obrisanih. */
    @Modifying(flushAutomatically = true)
    @Query("delete from Pokusaj p where p.vreme < :granica")
    int obrisiStarije(@Param("granica") LocalDateTime granica);
}
