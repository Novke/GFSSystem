package tri.novica.gfssystem.repository.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.push.PushPretplata;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PushPretplataRepository extends JpaRepository<PushPretplata, Long> {

    Optional<PushPretplata> findByStudentskiUredjajId(Long uredjajId);

    Optional<PushPretplata> findByNastavnickaSesijaId(Long sesijaId);

    Optional<PushPretplata> findByEndpointHes(String endpointHes);

    boolean existsByStudentskiUredjajId(Long uredjajId);

    boolean existsByNastavnickaSesijaId(Long sesijaId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PushPretplata p where p.studentskiUredjajId = :id")
    int obrisiZaUredjaj(@Param("id") Long uredjajId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PushPretplata p where p.nastavnickaSesijaId = :id")
    int obrisiZaSesiju(@Param("id") Long sesijaId);

    /** Briše pretplate opozvanih uređaja studenta (posle "odjavi se svuda", opoziva sa kartice, raskida Moodle veze). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE p FROM push_pretplate p JOIN studentski_uredjaji u ON u.id = p.studentski_uredjaj_id
            WHERE u.student_id = :studentId AND u.opozvan IS NOT NULL""", nativeQuery = true)
    int obrisiZaOpozvaneUredjaje(@Param("studentId") Long studentId);

    /** Važeće pretplate datih studenata (uređaj nije opozvan ni istekao): parovi (id studenta, pretplata). */
    @Query("""
            select u.student.id, p from PushPretplata p, StudentskiUredjaj u
            where u.id = p.studentskiUredjajId and u.student.id in :ids and u.opozvan is null and u.istice > :sada""")
    List<Object[]> vazeceStudenata(@Param("ids") Collection<Long> studentIds, @Param("sada") LocalDateTime sada);

    /** Važeće pretplate nastavnika (sesija nije opozvana ni istekla). */
    @Query("""
            select p from PushPretplata p, NastavnickaSesija s
            where s.id = p.nastavnickaSesijaId and s.opozvana is null and s.istice > :sada""")
    List<PushPretplata> vazeceNastavnika(@Param("sada") LocalDateTime sada);

    /** Pretplata po id-ju samo dok joj uređaj ili sesija važi (pošiljalac pre svakog slanja). */
    @Query(value = """
            SELECT p.* FROM push_pretplate p
            LEFT JOIN studentski_uredjaji u ON u.id = p.studentski_uredjaj_id
            LEFT JOIN nastavnicke_sesije s ON s.id = p.nastavnicka_sesija_id
            WHERE p.id = :id
              AND ((u.id IS NOT NULL AND u.opozvan IS NULL AND u.istice > :sada)
                OR (s.id IS NOT NULL AND s.opozvana IS NULL AND s.istice > :sada))""", nativeQuery = true)
    Optional<PushPretplata> vazeca(@Param("id") Long id, @Param("sada") LocalDateTime sada);

    @Transactional
    @Modifying
    @Query("update PushPretplata p set p.uzastopneGreske = 0, p.poslednjiUspeh = :sada where p.id = :id")
    int uspeh(@Param("id") Long id, @Param("sada") LocalDateTime sada);

    @Transactional
    @Modifying
    @Query("update PushPretplata p set p.uzastopneGreske = p.uzastopneGreske + 1 where p.id = :id")
    int greska(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("delete from PushPretplata p where p.id = :id and p.uzastopneGreske >= :max")
    int obrisiPosleGresaka(@Param("id") Long id, @Param("max") int max);

    @Transactional
    @Modifying
    @Query("delete from PushPretplata p where p.id = :id")
    int obrisi(@Param("id") Long id);

    /** Čišćenje: pretplate čiji uređaj ili sesija više ne važi (opozvan ili istekao; ne može ponovo da važi). */
    @Modifying
    @Query(value = """
            DELETE p FROM push_pretplate p
            LEFT JOIN studentski_uredjaji u ON u.id = p.studentski_uredjaj_id
            LEFT JOIN nastavnicke_sesije s ON s.id = p.nastavnicka_sesija_id
            WHERE NOT ((u.id IS NOT NULL AND u.opozvan IS NULL AND u.istice > :sada)
                    OR (s.id IS NOT NULL AND s.opozvana IS NULL AND s.istice > :sada))""", nativeQuery = true)
    int obrisiNevazece(@Param("sada") LocalDateTime sada);
}
