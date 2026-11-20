package tri.novica.gfssystem.repository.fajlovi;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.fajlovi.KodFajlova;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Tabela {@code slajdovi_izvodjenja} nema entitet; upis ide samo ovim native upitom. Repozitorijum je vezan za
 * {@link KodFajlova} samo da bi Spring Data prihvatio deklaraciju.
 */
public interface SlajdoviIzvodjenjaRepository extends Repository<KodFajlova, Long> {

    /**
     * Upis prikazanog slajda "ako ne postoji" (PK (izvođenje, slajd): ponovni prikaz ne menja prvi zapis), isto kao
     * {@code PrikazaniSlajdoviRepository.upisi}; bez izuzetka za duplikat. Vraća broj promenjenih redova (1 novi, 0 postojeći).
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO slajdovi_izvodjenja (izvodjenje_id, slajd_id, prvi_put)
            VALUES (:izvodjenjeId, :slajdId, :sada)
            ON DUPLICATE KEY UPDATE prvi_put = prvi_put""", nativeQuery = true)
    int upisiPrikaz(@Param("izvodjenjeId") Long izvodjenjeId, @Param("slajdId") Long slajdId,
                    @Param("sada") LocalDateTime sada);

    @Query(value = "SELECT COUNT(*) FROM slajdovi_izvodjenja WHERE izvodjenje_id = :izvodjenjeId AND slajd_id = :slajdId",
            nativeQuery = true)
    long broj(@Param("izvodjenjeId") Long izvodjenjeId, @Param("slajdId") Long slajdId);
}
