package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PitanjeRundaRepository extends JpaRepository<PitanjeRunda, Long> {

    List<PitanjeRunda> findAllByIzvodjenjeIdOrderByOtvorenoAsc(Long id);

    /** Runde izvođenja po redu otvaranja (id razrešava isti trenutak). */
    List<PitanjeRunda> findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(Long id);

    /** Poslednja runda tog slajda u izvođenju (pitanje se može otvoriti ponovo). */
    Optional<PitanjeRunda> findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(Long izvodjenjeId, Long slajdId);

    Optional<PitanjeRunda> findByIdAndIzvodjenjeId(Long id, Long izvodjenjeId);

    /** Broj rundi po izvođenju: redovi {@code [izvodjenjeId, broj]}. */
    @Query("select r.izvodjenje.id, count(r) from PitanjeRunda r where r.izvodjenje.id in :ids group by r.izvodjenje.id")
    List<Object[]> brojPoIzvodjenju(@Param("ids") Collection<Long> ids);

    /**
     * Da li je varijanta u snimku neke runde ({@code varijante[].varijantaId} snimka Zadatak slajda), tj. izvučena na času
     * makar nikome nije dodeljena (izvučeno je više varijanti nego što ima učesnika, ili učesnik tek dolazi). Dodelu štiti
     * FK RESTRICT, ovu izvučenu varijantu samo ova provera. {@code JSON_TABLE} čita snimak kao JSON (redosled ključeva i
     * razmaci nisu bitni), pa tekst koji liči na ključ ne računa; parsiraju se samo redovi koji sadrže {@code "varijante"}.
     * Skenira sve runde, pa se zove samo pri retkom brisanju.
     */
    default boolean varijantaUSnimku(Long varijantaId) {
        return snimciSaVarijantom(varijantaId) > 0;
    }

    /** Kao {@link #varijantaUSnimku}, za bilo koju varijantu grupe (jedan prolaz, ne po varijanti). */
    default boolean varijanteGrupeUSnimku(Long grupaId) {
        return snimciSaVarijantamaGrupe(grupaId) > 0;
    }

    /** 1 kad postoji snimak sa varijantom, inače 0 ({@code exists} u MySQL-u vraća broj, ne boolean). */
    @Query(value = """
            select exists (
                select 1 from pitanje_runde r,
                    json_table(r.snimak, '$.varijante[*]' columns (vid bigint path '$.varijantaId')) j
                where r.snimak like '%"varijante"%' and j.vid = :varijantaId)""", nativeQuery = true)
    long snimciSaVarijantom(@Param("varijantaId") Long varijantaId);

    /** 1 kad postoji snimak sa nekom varijantom grupe, inače 0. */
    @Query(value = """
            select exists (
                select 1 from pitanje_runde r,
                    json_table(r.snimak, '$.varijante[*]' columns (vid bigint path '$.varijantaId')) j
                where r.snimak like '%"varijante"%'
                  and j.vid in (select v.id from varijante v where v.grupa_id = :grupaId))""", nativeQuery = true)
    long snimciSaVarijantamaGrupe(@Param("grupaId") Long grupaId);

    /** Briše sve runde izvođenja jednim upitom (posle odgovora; "ne čuvaj" na kraju). */
    @Modifying(flushAutomatically = true)
    @Query("delete from PitanjeRunda r where r.izvodjenje.id = :id")
    void deleteAllByIzvodjenjeId(@Param("id") Long id);
}
