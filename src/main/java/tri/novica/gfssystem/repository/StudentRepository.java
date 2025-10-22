package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    @Query("""
        SELECT s from Student s 
        LEFT JOIN FETCH s.grupa
        LEFT JOIN FETCH s.aktivnosti a
        LEFT JOIN FETCH a.predavanje
        LEFT JOIN FETCH s.uradjeniDomaci
        LEFT JOIN FETCH s.polaganja
        WHERE s.id = :id""")
    Optional<Student> findByIdFetchDetails(Long id);

    List<Student> findByGrupa(Grupa grupa);
}