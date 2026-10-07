package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

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

    /** Isto što {@link #postojiStudent}, ali bez studenta koji se menja (izmena na sopstveni indeks i godinu je dozvoljena). */
    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Student s
        WHERE UPPER(REPLACE(s.indeks, ' ', '')) = :indeks AND s.godina = :godina AND s.id <> :izuzetId""")
    boolean postojiDrugiStudent(@Param("indeks") String normalizovanIndeks, @Param("godina") int godina,
                                @Param("izuzetId") Long izuzetId);

    long countByGrupaId(Long grupaId);

    /** Broj studenata po grupi, za stranicu liste ({@code Brojaci.poId}). Redovi: {@code [grupaId, broj]}. */
    @Query("select s.grupa.id, count(s) from Student s where s.grupa.id in :ids group by s.grupa.id")
    List<Object[]> brojStudenataPoGrupi(@Param("ids") Collection<Long> ids);

    /**
     * Studenti iz grupa sa godinom upisa manjom od {@code godinaUpisa} koji imaju bar jednu aktivnost (na predavanju)
     * ili bar jedno polaganje (testa) na predmetu: ponovci koji dolaze na nastavu. Studenti bez grupe se ne broje.
     */
    @Query("""
        select count(s) from Student s
        where s.grupa.godinaUpisa < :godinaUpisa
          and (exists (select a.id from Aktivnost a where a.student = s and a.predavanje.predmet.id = :predmetId)
            or exists (select p.id from Polaganje p where p.student = s and p.test.predmet.id = :predmetId))""")
    long brojStarijihNaPredmetu(@Param("predmetId") Long predmetId, @Param("godinaUpisa") int godinaUpisa);
}
