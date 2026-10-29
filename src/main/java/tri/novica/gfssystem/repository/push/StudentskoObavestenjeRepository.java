package tri.novica.gfssystem.repository.push;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.push.StudentskoObavestenje;
import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface StudentskoObavestenjeRepository extends JpaRepository<StudentskoObavestenje, Long> {

    /** Red jednog događaja za jednog studenta; postojeći ključ se tiho preskače ({@code UNIQUE(student_id, kljuc)}). */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO studentska_obavestenja (student_id, vrsta, kljuc, naslov, tekst, putanja, oznaka, kreirano)
            VALUES (:studentId, :vrsta, :kljuc, :naslov, :tekst, :putanja, :oznaka, :kreirano)""", nativeQuery = true)
    int upisi(@Param("studentId") Long studentId, @Param("vrsta") String vrsta, @Param("kljuc") String kljuc,
              @Param("naslov") String naslov, @Param("tekst") String tekst, @Param("putanja") String putanja,
              @Param("oznaka") String oznaka, @Param("kreirano") LocalDateTime kreirano);

    List<StudentskoObavestenje> findAllByKljucAndStudentIdIn(String kljuc, Collection<Long> studentIds);

    /** Stavke zvona: date vrste, najnovije prvo. */
    @Query("""
            select o from StudentskoObavestenje o where o.studentId = :studentId and o.vrsta in :vrste
            order by o.kreirano desc, o.id desc""")
    List<StudentskoObavestenje> zaZvono(@Param("studentId") Long studentId,
                                        @Param("vrste") Collection<VrstaStudentskogObavestenja> vrste, Pageable strana);

    @Modifying
    @Query("delete from StudentskoObavestenje o where o.kreirano < :granica")
    int obrisiStarije(@Param("granica") LocalDateTime granica);
}
