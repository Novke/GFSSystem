package tri.novica.gfssystem.repository.uzivo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;

import java.util.List;
import java.util.Optional;

@Repository
public interface IzvodjenjeRepository extends JpaRepository<Izvodjenje, Long> {

    /** Zaključava red izvođenja (SELECT ... FOR UPDATE) do kraja transakcije: komande i odgovori idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Izvodjenje i where i.id = :id")
    Optional<Izvodjenje> findByIdForUpdate(@Param("id") Long id);

    Optional<Izvodjenje> findByAktivanKod(String kod);

    boolean existsByAktivanKod(String kod);

    List<Izvodjenje> findAllByStatus(StatusIzvodjenja s);

    List<Izvodjenje> findAllByPrezentacijaIdOrderByPocetakDesc(Long id);

    List<Izvodjenje> findAllByOrderByPocetakDesc();

    boolean existsByPrezentacijaIdAndStatus(Long id, StatusIzvodjenja s);

    long countByPrezentacijaId(Long id);
}
