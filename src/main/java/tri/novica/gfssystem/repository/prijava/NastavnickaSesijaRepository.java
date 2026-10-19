package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.NastavnickaSesija;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NastavnickaSesijaRepository extends JpaRepository<NastavnickaSesija, Long> {

    /** Sesija po SHA-256 tokena iz kolačića (UNIQUE ključ, bez petlje); važenje proverava servis. */
    Optional<NastavnickaSesija> findByTokenHes(String tokenHes);

    /** Važeće sesije (nisu opozvane ni istekle), poslednje korišćena prva. */
    List<NastavnickaSesija> findAllByOpozvanaIsNullAndIsticeAfterOrderByPoslednjiUlazDesc(LocalDateTime sada);
}
