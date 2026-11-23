package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.CrtezIzvodjenja;
import tri.novica.gfssystem.entity.uzivo.PozadinaCrteza;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrtezIzvodjenjaRepository extends JpaRepository<CrtezIzvodjenja, Long> {

    /** Upis zaključava red izvođenja, pa jedna strana nema dva upisa u isto vreme. */
    Optional<CrtezIzvodjenja> findByIzvodjenjeIdAndSlajdIdAndPozadina(Long izvodjenjeId, Long slajdId, PozadinaCrteza pozadina);

    /** Verzija strane za snimak nastavnika (bez čitanja elemenata). */
    @Query("""
            select c.verzija from CrtezIzvodjenja c
            where c.izvodjenjeId = :izvodjenjeId and c.slajdId = :slajdId and c.pozadina = :pozadina""")
    Optional<Integer> findVerzija(@Param("izvodjenjeId") Long izvodjenjeId, @Param("slajdId") Long slajdId,
                                  @Param("pozadina") PozadinaCrteza pozadina);

    /** Ne-prazne strane izvođenja (pregled). */
    @Query("select c from CrtezIzvodjenja c where c.izvodjenjeId = :izvodjenjeId and c.elementi <> '[]'")
    List<CrtezIzvodjenja> findNeprazneByIzvodjenjeId(@Param("izvodjenjeId") Long izvodjenjeId);

    /**
     * Ne-prazne strane slajda {@code slajdId} iz drugih izvođenja iste prezentacije (i bez čuvanja), najnovije prve. Slajd
     * pripada jednoj prezentaciji, pa je uslov po slajdu dovoljan; tekuće izvođenje se preskače.
     */
    @Query("""
            select new tri.novica.gfssystem.repository.uzivo.RanijiCrtezRed(
                i.id, i.pocetak, g.naziv, c.pozadina, c.elementi)
            from CrtezIzvodjenja c, Izvodjenje i left join i.grupa g
            where i.id = c.izvodjenjeId and c.slajdId = :slajdId and c.izvodjenjeId <> :izvodjenjeId
              and c.elementi <> '[]'
            order by i.pocetak desc, i.id desc""")
    List<RanijiCrtezRed> findRanije(@Param("slajdId") Long slajdId, @Param("izvodjenjeId") Long izvodjenjeId);
}
