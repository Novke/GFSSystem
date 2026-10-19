package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.ZadatakDodela;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZadatakDodelaRepository extends JpaRepository<ZadatakDodela, Long> {

    Optional<ZadatakDodela> findByRundaIdAndUcesnikId(Long rundaId, Long ucesnikId);

    boolean existsByRundaIdAndUcesnikId(Long rundaId, Long ucesnikId);

    /** Da li je varijanta ikad dodeljena na času ("korišćeno na času": brisanje traži arhiviranje). */
    boolean existsByVarijantaId(Long varijantaId);

    /** Da li je neka varijanta grupe ikad dodeljena na času. */
    boolean existsByVarijantaGrupaId(Long grupaId);

    /** Dodele runde: redovi {@code [ucesnikId, varijantaId]}. */
    @Query("select d.ucesnik.id, d.varijanta.id from ZadatakDodela d where d.runda.id = :rundaId")
    List<Object[]> parovi(@Param("rundaId") Long rundaId);

    /** Dodele svih rundi izvođenja: redovi {@code [rundaId, ucesnikId, varijantaId]}. */
    @Query("select d.runda.id, d.ucesnik.id, d.varijanta.id from ZadatakDodela d where d.runda.izvodjenje.id = :izvodjenjeId")
    List<Object[]> paroviIzvodjenja(@Param("izvodjenjeId") Long izvodjenjeId);
}
