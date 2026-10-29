package tri.novica.gfssystem.repository.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.portal.PodesavanjaStudenta;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface PodesavanjaStudentaRepository extends JpaRepository<PodesavanjaStudenta, Long> {

    /** Upis izbora jednim upitom (dva brza klika ne daju dupli ključ). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO podesavanja_studenta (student_id, mejl_otkazan_cas, izmenjeno) VALUES (:studentId, :mejl, :sada) AS novo
            ON DUPLICATE KEY UPDATE mejl_otkazan_cas = novo.mejl_otkazan_cas, izmenjeno = novo.izmenjeno""", nativeQuery = true)
    int upisi(@Param("studentId") Long studentId, @Param("mejl") boolean mejlOtkazanCas, @Param("sada") LocalDateTime sada);

    /** Od datih studenata oni koji su isključili mejl o otkazanom času (bez reda = uključeno). */
    @Query("select p.studentId from PodesavanjaStudenta p where p.studentId in :ids and p.mejlOtkazanCas = false")
    List<Long> bezMejlaOtkazanogCasa(@Param("ids") Collection<Long> studentIds);
}
