package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.SpoljniIdentitet;
import tri.novica.gfssystem.entity.prijava.VrstaIdentiteta;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpoljniIdentitetRepository extends JpaRepository<SpoljniIdentitet, Long> {

    /** Identitet po jedinstvenoj trojci (za LTI: {@code iss} i {@code sub}). */
    Optional<SpoljniIdentitet> findByVrstaAndIzdavacAndSubjekat(VrstaIdentiteta vrsta, String izdavac, String subjekat);

    List<SpoljniIdentitet> findAllByStudentId(Long studentId);

    /** Briše sve spoljne identitete studenta (raskid veze sa Moodle-om); vraća broj obrisanih. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SpoljniIdentitet i where i.student.id = :studentId")
    int obrisiZaStudenta(@Param("studentId") Long studentId);
}
