package tri.novica.gfssystem.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predavanje;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredavanjeRepository extends JpaRepository<Predavanje, Long>, JpaSpecificationExecutor<Predavanje> {

    /**
     * Zaključava red predavanja (SELECT ... FOR UPDATE) do kraja transakcije: čekiranje i ručne izmene aktivnosti idu
     * redom (spec studentskog dela 4.5). Nikad posle zaključanog reda izvođenja.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Predavanje p where p.id = :id")
    Optional<Predavanje> findByIdForUpdate(@Param("id") Long id);

    /**
     * Nova tajna QR-a za čekiranje, samo ako predavanje nije završeno i tajne nema ili je napravljena pre {@code granica}
     * (compare-and-set: dva prozora u istoj sekundi ne prave dve tajne). Vraća broj izmenjenih redova (0 ili 1).
     */
    @Modifying
    @Query("""
            update Predavanje p set p.cekiranjeTajna = :tajna, p.cekiranjeTajnaOd = :sada
            where p.id = :id and (p.zavrseno is null or p.zavrseno = false)
              and (p.cekiranjeTajna is null or p.cekiranjeTajnaOd is null or p.cekiranjeTajnaOd < :granica)""")
    int postaviTajnu(@Param("id") Long id, @Param("tajna") String tajna, @Param("sada") LocalDateTime sada,
                     @Param("granica") LocalDateTime granica);

    /** Trenutna tajna iz baze (posle neuspelog {@link #postaviTajnu}: drugi zahtev ju je upravo postavio). */
    @Query("select p.cekiranjeTajna from Predavanje p where p.id = :id and p.cekiranjeTajna is not null")
    Optional<String> tajna(@Param("id") Long id);

    @Query("select max(p.rb) from Predavanje p")
    Integer findPoslednjiRB();
    List<Predavanje> findAllByGrupaIdAndPredmetIdOrderByDatumAsc(Long grupaId, Long predmetId);

    /** Poslednje predavanje po (datum, id); predlog sledećeg na kontrolnoj tabli. */
    Optional<Predavanje> findFirstByOrderByDatumDescIdDesc();

    /** Predavanja grupe, opciono jednog predmeta ({@code predmetId} null = svi). */
    @Query("select count(p) from Predavanje p where p.grupa.id = :grupaId and (:predmetId is null or p.predmet.id = :predmetId)")
    long brojPredavanjaGrupe(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);

    /**
     * Za kartice studenta: po predmetu broj predavanja grupe plus drugih predavanja na kojima je student bio (svako
     * jednom). Redovi {@code [predmetId, broj]}; {@code grupaId} null = samo ona na kojima je bio.
     */
    @Query("""
            select p.predmet.id, count(p) from Predavanje p left join p.grupa g
            where g.id = :grupaId
               or exists (select a.id from Aktivnost a where a.predavanje = p and a.student.id = :studentId)
            group by p.predmet.id""")
    List<Object[]> brojPredavanjaZaStudentaPoPredmetu(@Param("grupaId") Long grupaId, @Param("studentId") Long studentId);

    /**
     * Predavanja sa grupom u prozoru, po (predmet, grupa, datum): redovi {@code [predmetId, grupaId, datum, najveći id]}
     * (termin rasporeda ima predavanje kad postoji red istog predmeta, grupe i datuma).
     */
    @Query("""
            select p.predmet.id, p.grupa.id, p.datum, max(p.id) from Predavanje p
            where p.grupa is not null and p.datum between :od and :doDatuma
            group by p.predmet.id, p.grupa.id, p.datum""")
    List<Object[]> poPredmetuGrupiIDatumu(@Param("od") LocalDate od, @Param("doDatuma") LocalDate doDatuma);

    /**
     * Predavanja "u toku" za telefon studenta: današnja nezavršena predavanja sa grupom, gde je to studentova grupa ili
     * (starija generacija koja sluša sa mlađom, isto pravilo kao stavke rasporeda) student već ima aktivnost ili
     * čekiranje na predavanju istog predmeta i grupe, a na ovom predavanju još nije čekiran. Najnovije prvo.
     */
    @Query("""
            select p from Predavanje p join fetch p.predmet join fetch p.grupa g
            where p.datum = :danas and (p.zavrseno is null or p.zavrseno = false)
              and (g.id = (select s.grupa.id from Student s where s.id = :studentId)
                   or exists (select a.id from Aktivnost a where a.student.id = :studentId
                              and a.predavanje.grupa = g and a.predavanje.predmet = p.predmet)
                   or exists (select c.id from Cekiranje c where c.student.id = :studentId
                              and c.predavanje.grupa = g and c.predavanje.predmet = p.predmet))
              and not exists (select c2.id from Cekiranje c2 where c2.predavanje = p and c2.student.id = :studentId)
            order by p.id desc""")
    List<Predavanje> uTokuZaStudenta(@Param("studentId") Long studentId, @Param("danas") LocalDate danas,
                                     Pageable pageable);

    /** Predavanja predmeta za pokretanje prezentacije: nezavršena ili od {@code danas}, najnovija prva. */
    @Query("""
            select p from Predavanje p join fetch p.predmet left join fetch p.grupa
            where p.predmet.id = :predmetId
              and (p.zavrseno is null or p.zavrseno = false or p.datum = :danas)
            order by p.datum desc, p.rb desc""")
    List<Predavanje> findZaPokretanje(@Param("predmetId") Long predmetId, @Param("danas") LocalDate danas,
                                      Pageable pageable);
}
