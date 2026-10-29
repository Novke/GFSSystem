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

    /**
     * Upis izbora jednim upitom (dva brza klika ne daju dupli ključ). Prekidači push-a i zvona su {@code 1} (da), {@code 0} (ne) ili {@code -1} (ne menja;
     * nov red tada dobija podrazumevano "da"). {@code zvono_otvoreno} se ne dira.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO podesavanja_studenta (student_id, mejl_otkazan_cas, push_podsetnik, push_otkazan_cas, push_ocene,
                                              push_posle_casa, zvono_ocene, zvono_posle_casa, izmenjeno)
            VALUES (:studentId, :mejl, IF(:pp < 0, 1, :pp), IF(:poc < 0, 1, :poc), IF(:po < 0, 1, :po), IF(:ppc < 0, 1, :ppc),
                    IF(:zo < 0, 1, :zo), IF(:zpc < 0, 1, :zpc), :sada) AS novo
            ON DUPLICATE KEY UPDATE mejl_otkazan_cas = novo.mejl_otkazan_cas,
                push_podsetnik = IF(:pp < 0, podesavanja_studenta.push_podsetnik, novo.push_podsetnik),
                push_otkazan_cas = IF(:poc < 0, podesavanja_studenta.push_otkazan_cas, novo.push_otkazan_cas),
                push_ocene = IF(:po < 0, podesavanja_studenta.push_ocene, novo.push_ocene),
                push_posle_casa = IF(:ppc < 0, podesavanja_studenta.push_posle_casa, novo.push_posle_casa),
                zvono_ocene = IF(:zo < 0, podesavanja_studenta.zvono_ocene, novo.zvono_ocene),
                zvono_posle_casa = IF(:zpc < 0, podesavanja_studenta.zvono_posle_casa, novo.zvono_posle_casa),
                izmenjeno = novo.izmenjeno""", nativeQuery = true)
    int upisiSve(@Param("studentId") Long studentId, @Param("mejl") boolean mejlOtkazanCas, @Param("pp") int pushPodsetnik,
                 @Param("poc") int pushOtkazanCas, @Param("po") int pushOcene, @Param("ppc") int pushPosleCasa,
                 @Param("zo") int zvonoOcene, @Param("zpc") int zvonoPosleCasa, @Param("sada") LocalDateTime sada);

    /**
     * Student je otvorio zvono: {@code zvono_otvoreno = sada}; red bez izbora se pravi sa podrazumevanim vrednostima.
     * {@code izmenjeno} (vreme izmene izbora) se ne dira kad red postoji.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO podesavanja_studenta (student_id, mejl_otkazan_cas, zvono_otvoreno, izmenjeno)
            VALUES (:studentId, 1, :sada, :sada) AS novo
            ON DUPLICATE KEY UPDATE zvono_otvoreno = novo.zvono_otvoreno""", nativeQuery = true)
    int zvonoOtvoreno(@Param("studentId") Long studentId, @Param("sada") LocalDateTime sada);

    /** Od datih studenata oni koji su isključili mejl o otkazanom času (bez reda = uključeno). */
    @Query("select p.studentId from PodesavanjaStudenta p where p.studentId in :ids and p.mejlOtkazanCas = false")
    List<Long> bezMejlaOtkazanogCasa(@Param("ids") Collection<Long> studentIds);
}
