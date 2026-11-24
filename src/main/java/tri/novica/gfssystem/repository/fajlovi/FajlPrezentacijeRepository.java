package tri.novica.gfssystem.repository.fajlovi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface FajlPrezentacijeRepository extends JpaRepository<FajlPrezentacije, Long> {

    /** Prezentacija koja je održana (prvo završeno izvođenje) grupama studenta, za nalog. */
    interface Odrzana {
        Long getPrezentacijaId();

        LocalDateTime getOdrzana();
    }

    List<FajlPrezentacije> findAllByPrezentacijaIdOrderByRbAscIdAsc(Long prezentacijaId);

    List<FajlPrezentacije> findAllBySlajdId(Long slajdId);

    long countByPrezentacijaId(Long prezentacijaId);

    /** Broj fajlova po prezentaciji, jednim upitom za celu listu: redovi {@code [prezentacijaId, broj]}, bez redova sa 0. */
    @Query("select f.prezentacija.id, count(f) from FajlPrezentacije f where f.prezentacija.id in :ids group by f.prezentacija.id")
    List<Object[]> brojPoPrezentaciji(@Param("ids") Collection<Long> ids);

    boolean existsByMedijId(String medijId);

    /** Najveći {@code rb} u prezentaciji, 0 kad nema fajlova (novi fajl dobija max + 1). */
    @Query("select coalesce(max(f.rb), 0) from FajlPrezentacije f where f.prezentacija.id = :prezentacijaId")
    int najveciRb(@Param("prezentacijaId") Long prezentacijaId);

    /**
     * Fajlovi koda predavanja: prezentacije izvođene na predavanju, fajl bez slajda ili sa slajdom koji je prikazan nekom
     * izvođenju tog predavanja. Redosled: prezentacija po prvom početku izvođenja, pa {@code rb}, pa id.
     */
    @Query(value = """
            SELECT f.* FROM fajlovi_prezentacije f
            JOIN (SELECT i.prezentacija_id AS pid, MIN(i.pocetak) AS prvi FROM izvodjenja i
                  WHERE i.predavanje_id = :predavanjeId GROUP BY i.prezentacija_id) x ON x.pid = f.prezentacija_id
            WHERE f.slajd_id IS NULL
               OR EXISTS (SELECT 1 FROM slajdovi_izvodjenja si JOIN izvodjenja i2 ON i2.id = si.izvodjenje_id
                          WHERE si.slajd_id = f.slajd_id AND i2.predavanje_id = :predavanjeId)
            ORDER BY x.prvi, f.prezentacija_id, f.rb, f.id""", nativeQuery = true)
    List<FajlPrezentacije> zaPredavanje(@Param("predavanjeId") Long predavanjeId);

    /** Fajlovi koda izvođenja bez predavanja: prezentacija tog izvođenja, ista pravila za slajd kao {@link #zaPredavanje}. */
    @Query(value = """
            SELECT f.* FROM fajlovi_prezentacije f
            JOIN izvodjenja i ON i.prezentacija_id = f.prezentacija_id AND i.id = :izvodjenjeId
            WHERE f.slajd_id IS NULL
               OR EXISTS (SELECT 1 FROM slajdovi_izvodjenja si WHERE si.slajd_id = f.slajd_id AND si.izvodjenje_id = i.id)
            ORDER BY f.rb, f.id""", nativeQuery = true)
    List<FajlPrezentacije> zaIzvodjenje(@Param("izvodjenjeId") Long izvodjenjeId);

    /** Da li je fajl na spisku nekog važećeg koda predavanja (kod ne istekao, predavanje nije završeno). */
    @Query(value = """
            SELECT COUNT(*) FROM fajlovi_prezentacije f
            JOIN kodovi_fajlova k ON k.aktivan_kod IS NOT NULL AND k.vazi_do > :sada AND k.predavanje_id IS NOT NULL
            JOIN predavanja p ON p.id = k.predavanje_id AND COALESCE(p.zavrseno, 0) = 0
            WHERE f.id = :fajlId
              AND EXISTS (SELECT 1 FROM izvodjenja i WHERE i.predavanje_id = k.predavanje_id AND i.prezentacija_id = f.prezentacija_id)
              AND (f.slajd_id IS NULL
                   OR EXISTS (SELECT 1 FROM slajdovi_izvodjenja si JOIN izvodjenja i2 ON i2.id = si.izvodjenje_id
                              WHERE si.slajd_id = f.slajd_id AND i2.predavanje_id = k.predavanje_id))""", nativeQuery = true)
    long naKoduPredavanja(@Param("fajlId") Long fajlId, @Param("sada") LocalDateTime sada);

    /** Isto za važeće kodove izvođenja bez predavanja (izvođenje je AKTIVNO). */
    @Query(value = """
            SELECT COUNT(*) FROM fajlovi_prezentacije f
            JOIN kodovi_fajlova k ON k.aktivan_kod IS NOT NULL AND k.vazi_do > :sada AND k.izvodjenje_id IS NOT NULL
            JOIN izvodjenja i ON i.id = k.izvodjenje_id AND i.status = 'AKTIVNO' AND i.prezentacija_id = f.prezentacija_id
            WHERE f.id = :fajlId
              AND (f.slajd_id IS NULL
                   OR EXISTS (SELECT 1 FROM slajdovi_izvodjenja si WHERE si.slajd_id = f.slajd_id AND si.izvodjenje_id = i.id))""",
            nativeQuery = true)
    long naKoduIzvodjenja(@Param("fajlId") Long fajlId, @Param("sada") LocalDateTime sada);

    /** Kao {@link #naKoduPredavanja}, ali samo kod predavanja čija je grupa među {@code grupe} (mejl uživo: studenti te grupe). */
    @Query(value = """
            SELECT COUNT(*) FROM fajlovi_prezentacije f
            JOIN kodovi_fajlova k ON k.aktivan_kod IS NOT NULL AND k.vazi_do > :sada AND k.predavanje_id IS NOT NULL
            JOIN predavanja p ON p.id = k.predavanje_id AND COALESCE(p.zavrseno, 0) = 0 AND p.grupa_id IN (:grupe)
            WHERE f.id = :fajlId
              AND EXISTS (SELECT 1 FROM izvodjenja i WHERE i.predavanje_id = k.predavanje_id AND i.prezentacija_id = f.prezentacija_id)
              AND (f.slajd_id IS NULL
                   OR EXISTS (SELECT 1 FROM slajdovi_izvodjenja si JOIN izvodjenja i2 ON i2.id = si.izvodjenje_id
                              WHERE si.slajd_id = f.slajd_id AND i2.predavanje_id = k.predavanje_id))""", nativeQuery = true)
    long naKoduPredavanjaZaGrupe(@Param("fajlId") Long fajlId, @Param("sada") LocalDateTime sada,
                                 @Param("grupe") Collection<Long> grupe);

    /** Kao {@link #naKoduIzvodjenja}, ali samo izvođenje čija je grupa među {@code grupe} (izvođenje bez grupe nikad). */
    @Query(value = """
            SELECT COUNT(*) FROM fajlovi_prezentacije f
            JOIN kodovi_fajlova k ON k.aktivan_kod IS NOT NULL AND k.vazi_do > :sada AND k.izvodjenje_id IS NOT NULL
            JOIN izvodjenja i ON i.id = k.izvodjenje_id AND i.status = 'AKTIVNO' AND i.prezentacija_id = f.prezentacija_id
                             AND i.grupa_id IN (:grupe)
            WHERE f.id = :fajlId
              AND (f.slajd_id IS NULL
                   OR EXISTS (SELECT 1 FROM slajdovi_izvodjenja si WHERE si.slajd_id = f.slajd_id AND si.izvodjenje_id = i.id))""",
            nativeQuery = true)
    long naKoduIzvodjenjaZaGrupe(@Param("fajlId") Long fajlId, @Param("sada") LocalDateTime sada,
                                 @Param("grupe") Collection<Long> grupe);

    /** Da li prezentacija ima završeno izvođenje sa nekom od grupa (pravilo naloga). */
    @Query(value = """
            SELECT COUNT(*) FROM izvodjenja i
            WHERE i.prezentacija_id = :prezentacijaId AND i.status = 'ZAVRSENO' AND i.grupa_id IN (:grupe)""", nativeQuery = true)
    long zavrsenaZaGrupe(@Param("prezentacijaId") Long prezentacijaId, @Param("grupe") Collection<Long> grupe);

    /** Fajlovi prezentacija predmeta koje pravilo naloga otvara, od najnovije održane. */
    @Query(value = """
            SELECT f.* FROM fajlovi_prezentacije f
            JOIN prezentacije pz ON pz.id = f.prezentacija_id AND pz.predmet_id = :predmetId
            JOIN (SELECT i.prezentacija_id AS pid, MIN(i.pocetak) AS odrzana FROM izvodjenja i
                  WHERE i.status = 'ZAVRSENO' AND i.grupa_id IN (:grupe) GROUP BY i.prezentacija_id) x ON x.pid = pz.id
            ORDER BY x.odrzana DESC, pz.id DESC, f.rb, f.id""", nativeQuery = true)
    List<FajlPrezentacije> zaNalog(@Param("predmetId") Long predmetId, @Param("grupe") Collection<Long> grupe);

    /** Kad je koja prezentacija predmeta održana grupama studenta (samo one koje imaju fajl), od najnovije. */
    @Query(value = """
            SELECT pz.id AS prezentacijaId, x.odrzana AS odrzana
            FROM prezentacije pz
            JOIN (SELECT i.prezentacija_id AS pid, MIN(i.pocetak) AS odrzana FROM izvodjenja i
                  WHERE i.status = 'ZAVRSENO' AND i.grupa_id IN (:grupe) GROUP BY i.prezentacija_id) x ON x.pid = pz.id
            WHERE pz.predmet_id = :predmetId AND EXISTS (SELECT 1 FROM fajlovi_prezentacije f WHERE f.prezentacija_id = pz.id)
            ORDER BY x.odrzana DESC, pz.id DESC""", nativeQuery = true)
    List<Odrzana> odrzane(@Param("predmetId") Long predmetId, @Param("grupe") Collection<Long> grupe);
}
