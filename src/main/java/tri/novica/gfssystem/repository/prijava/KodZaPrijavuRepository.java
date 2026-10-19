package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.KodZaPrijavu;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KodZaPrijavuRepository extends JpaRepository<KodZaPrijavu, Long> {

    /** Kodovi studenta koji još važe (neiskorišćeni, neponišteni, neistekli), najnoviji prvi. */
    @Query("""
            select k from KodZaPrijavu k
            where k.student.id = :studentId and k.iskoriscen is null and k.ponisten is null and k.istice > :sada
            order by k.kreirano desc, k.id desc""")
    List<KodZaPrijavu> findAktivne(@Param("studentId") Long studentId, @Param("sada") LocalDateTime sada);

    /** Broj kodova napravljenih studentu posle trenutka (bez obzira na stanje koda). */
    long countByStudentIdAndKreiranoAfter(Long studentId, LocalDateTime posle);
}
