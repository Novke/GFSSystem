package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Student;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    @Query("""
            SELECT s from Student s 
            LEFT join fetch s.grupa
            LEFT join fetch s.aktivnosti
            left join fetch s.uradjeniDomaci
            where s.id = :id""")
    Optional<Student> findByIdFetchDetails(Long id);
}
