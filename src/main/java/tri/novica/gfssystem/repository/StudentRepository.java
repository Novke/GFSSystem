package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Stari redovi mogu imati razmake ili mala slova u indeksu, zato se normalizuje i kolona. */
    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Student s
        WHERE UPPER(REPLACE(s.indeks, ' ', '')) = :indeks AND s.godina = :godina""")
    boolean postojiStudent(@Param("indeks") String normalizovanIndeks, @Param("godina") int godina);

    long countByGrupaId(Long grupaId);
}