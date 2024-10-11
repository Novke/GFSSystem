package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;

import java.util.Optional;

@Repository
public interface GrupaRepository extends JpaRepository<Grupa, Long> {

    @Query("SELECT g FROM Grupa g LEFT JOIN fetch g.studenti where g.id = :id")
    Optional<Grupa> findByIdFetchStudents(@Param("id") Long id);

}
