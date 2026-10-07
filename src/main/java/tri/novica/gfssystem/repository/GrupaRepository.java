package tri.novica.gfssystem.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupaRepository extends JpaRepository<Grupa, Long> {

    @Query("SELECT g FROM Grupa g LEFT JOIN fetch g.studenti where g.id = :id")
    Optional<Grupa> findByIdFetchStudents(@Param("id") Long id);

    boolean existsByNazivIgnoreCase(String naziv);

    boolean existsByNazivIgnoreCaseAndIdNot(String naziv, Long id);

    /** {@code Containing}: Spring Data escape-uje {@code %} i {@code _} iz upita. */
    List<Grupa> findByNazivContainingIgnoreCase(String naziv, Pageable pageable);

}
