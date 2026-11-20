package tri.novica.gfssystem.repository.fajlovi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface KodFajlovaRepository extends JpaRepository<KodFajlova, Long> {

    Optional<KodFajlova> findByAktivanKod(String aktivanKod);

    /** Da li je broj skoro izdat (red sa tim brojem važi ili je istekao posle {@code granica}): istekao pravi kod nije pogađanje. */
    boolean existsByKodAndVaziDoAfter(String kod, java.time.LocalDateTime granica);

    Optional<KodFajlova> findByPredavanjeId(Long predavanjeId);

    Optional<KodFajlova> findByIzvodjenjeId(Long izvodjenjeId);

    /**
     * Oslobađa brojeve isteklih kodova: vreme je prošlo, predavanje je završeno ili izvođenje nije AKTIVNO. Poziva se pre
     * izbora novog broja (spec fajlova, odeljak 4); {@code flushAutomatically} da vidi neupisane izmene sesije.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kodovi_fajlova k
            LEFT JOIN predavanja p ON p.id = k.predavanje_id
            LEFT JOIN izvodjenja i ON i.id = k.izvodjenje_id
            SET k.aktivan_kod = NULL
            WHERE k.aktivan_kod IS NOT NULL
              AND (k.vazi_do <= :sada
                   OR (k.predavanje_id IS NOT NULL AND COALESCE(p.zavrseno, 0) <> 0)
                   OR (k.izvodjenje_id IS NOT NULL AND i.status <> 'AKTIVNO'))""", nativeQuery = true)
    int oslobodiIstekle(@Param("sada") LocalDateTime sada);

    /**
     * Novi red koda za izvođenje bez predavanja, "ako ne postoji broj": {@code INSERT IGNORE} pa broj upisanih redova (0 =
     * broj je zauzet, drugi pokušaj), bez izuzetka koji bi pokvario sesiju.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO kodovi_fajlova (kod, aktivan_kod, predavanje_id, izvodjenje_id, kreirano, vazi_do)
            VALUES (:kod, :kod, NULL, :izvodjenjeId, :sada, :vaziDo)""", nativeQuery = true)
    int upisiZaIzvodjenje(@Param("kod") String kod, @Param("izvodjenjeId") Long izvodjenjeId,
                          @Param("sada") LocalDateTime sada, @Param("vaziDo") LocalDateTime vaziDo);

    /** Novi red za predavanje; isto kao {@link #upisiZaIzvodjenje} (0 = broj zauzet ili red predavanja već postoji). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO kodovi_fajlova (kod, aktivan_kod, predavanje_id, izvodjenje_id, kreirano, vazi_do)
            VALUES (:kod, :kod, :predavanjeId, NULL, :sada, :vaziDo)""", nativeQuery = true)
    int upisiZaPredavanje(@Param("kod") String kod, @Param("predavanjeId") Long predavanjeId,
                          @Param("sada") LocalDateTime sada, @Param("vaziDo") LocalDateTime vaziDo);

    /** Obnavlja postojeći red predavanja novim brojem; 0 = broj je zauzet (drugi pokušaj). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE IGNORE kodovi_fajlova SET kod = :kod, aktivan_kod = :kod, kreirano = :sada, vazi_do = :vaziDo
            WHERE id = :id""", nativeQuery = true)
    int obnovi(@Param("id") Long id, @Param("kod") String kod, @Param("sada") LocalDateTime sada,
               @Param("vaziDo") LocalDateTime vaziDo);
}
