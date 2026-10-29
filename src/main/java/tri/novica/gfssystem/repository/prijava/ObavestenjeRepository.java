package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.Obavestenje;
import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;

import java.time.LocalDateTime;

@Repository
public interface ObavestenjeRepository extends JpaRepository<Obavestenje, Long> {

    /** Otvorena (nerešena) obaveštenja; redosled daje {@code pageable}. Student se učitava odmah (ime u listi). */
    @EntityGraph(attributePaths = "student")
    Page<Obavestenje> findAllByResenoIsNull(Pageable pageable);

    /** Sva obaveštenja, rešena i otvorena; student se učitava odmah. */
    @Override
    @EntityGraph(attributePaths = "student")
    Page<Obavestenje> findAll(Pageable pageable);

    long countByProcitanoIsNull();

    long countByResenoIsNull();

    /** Da li studentu već postoji obaveštenje te vrste, napravljeno posle granice (rešeno ili ne). */
    boolean existsByVrstaAndStudentIdAndKreiranoAfter(VrstaObavestenja vrsta, Long studentId, LocalDateTime posle);

    /** Da li obaveštenje sa ključem postoji (otvoreno ili rešeno): {@code MNOGO_NAJAVA} se pravi jednom po terminu. */
    boolean existsByKljuc(String kljuc);

    /** Rešava otvoreno obaveštenje sa ključem (i označava ga pročitanim); vraća broj izmenjenih (0 ili 1). */
    @Modifying(flushAutomatically = true)
    @Query("update Obavestenje o set o.reseno = :sada, o.procitano = coalesce(o.procitano, :sada) "
            + "where o.kljuc = :kljuc and o.reseno is null")
    int resiPoKljucu(@Param("kljuc") String kljuc, @Param("sada") LocalDateTime sada);

    /** Označava sva nepročitana kao pročitana; vraća broj izmenjenih. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Obavestenje o set o.procitano = :sada where o.procitano is null")
    int procitajSve(@Param("sada") LocalDateTime sada);

    /** Rešava otvorena obaveštenja studenta (i označava ih pročitanim); vraća broj izmenjenih. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Obavestenje o set o.reseno = :sada, o.procitano = coalesce(o.procitano, :sada) "
            + "where o.student.id = :studentId and o.reseno is null")
    int resiZaStudenta(@Param("studentId") Long studentId, @Param("sada") LocalDateTime sada);
}
