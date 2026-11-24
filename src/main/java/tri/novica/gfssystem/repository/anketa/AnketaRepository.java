package tri.novica.gfssystem.repository.anketa;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.anketa.Anketa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnketaRepository extends JpaRepository<Anketa, Long> {

    /** Red ankete za izmenu (zatvaranje, prikaz rezultata). Nikad pre reda predavanja u istoj transakciji. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Anketa a where a.id = :id")
    Optional<Anketa> findByIdForUpdate(@Param("id") Long id);

    /**
     * Deljeno zaključavanje reda ankete za glas ({@code FOR SHARE}): glasovi idu paralelno, a zatvaranje (koje traži
     * isključivo zaključavanje) čeka da se upisani glasovi završe, pa glas posle zatvaranja ne prolazi.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from Anketa a where a.id = :id")
    Optional<Anketa> findByIdForShare(@Param("id") Long id);

    /**
     * Zatvara otvorene ankete na času predavanja (nova anketa na času, kraj predavanja); pozivalac drži zaključan red
     * predavanja. Anketa čiji je rok već prošao ostaje kakva jeste (efektivno je zatvorena u roku). Ne prazni kontekst
     * perzistencije: {@code zavrsiPredavanje} posle mapira još učitano predavanje (lenje kolekcije).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Anketa a set a.zatvoreno = :sada
            where a.predavanjeId = :predavanjeId and a.publika = tri.novica.gfssystem.entity.anketa.PublikaAnkete.NA_CASU
              and a.zatvoreno is null and (a.rok is null or a.rok > :sada)""")
    int zatvoriNaCasu(@Param("predavanjeId") Long predavanjeId, @Param("sada") LocalDateTime sada);

    /** Otvorene ankete za grupu (rok uvek postoji). */
    @Query("""
            select count(a) from Anketa a
            where a.grupaId = :grupaId and a.publika = tri.novica.gfssystem.entity.anketa.PublikaAnkete.GRUPA
              and a.zatvoreno is null and a.rok > :sada""")
    long brojOtvorenihZaGrupu(@Param("grupaId") Long grupaId, @Param("sada") LocalDateTime sada);

    /**
     * Kandidati za studenta, najnovije prvo: ankete predavanja na kome je čekiran (obe publike) i ankete za njegovu grupu.
     * Ankete na času tek od {@code odNaCasu}, za grupu od {@code odGrupa}; servis posle računa stanje i prozor rezultata.
     */
    @Query("""
            select a from Anketa a
            where a.otvoreno >= :odGrupa
              and (a.publika = tri.novica.gfssystem.entity.anketa.PublikaAnkete.GRUPA or a.otvoreno >= :odNaCasu)
              and ((a.predavanjeId is not null and exists (select c.id from Cekiranje c
                                                           where c.predavanje.id = a.predavanjeId and c.student.id = :studentId))
                   or (a.publika = tri.novica.gfssystem.entity.anketa.PublikaAnkete.GRUPA and a.grupaId = :grupaId))
            order by a.otvoreno desc, a.id desc""")
    List<Anketa> kandidatiZaStudenta(@Param("studentId") Long studentId, @Param("grupaId") Long grupaId,
                                     @Param("odNaCasu") LocalDateTime odNaCasu, @Param("odGrupa") LocalDateTime odGrupa);

    /** Ankete predavanja ili grupe od {@code od}, najnovije prve (projektor i panel; servis filtrira po stanju). */
    @Query("""
            select a from Anketa a
            where a.otvoreno >= :od and ((:predavanjeId is not null and a.predavanjeId = :predavanjeId)
                                         or (:grupaId is not null and a.grupaId = :grupaId))
            order by a.otvoreno desc, a.id desc""")
    List<Anketa> zaPredavanjeIliGrupu(@Param("predavanjeId") Long predavanjeId, @Param("grupaId") Long grupaId,
                                      @Param("od") LocalDateTime od);

    /** Istorija sa opcionim filterima (sort iz {@code Pageable}). */
    @Query("""
            select a from Anketa a
            where (:predavanjeId is null or a.predavanjeId = :predavanjeId) and (:grupaId is null or a.grupaId = :grupaId)""")
    Page<Anketa> istorija(@Param("predavanjeId") Long predavanjeId, @Param("grupaId") Long grupaId, Pageable pageable);

    /** Istorija za MCP: i po predmetu (predmet predavanja ankete; anketa za grupu bez predavanja nema predmet). */
    @Query("""
            select a from Anketa a
            where (:predavanjeId is null or a.predavanjeId = :predavanjeId) and (:grupaId is null or a.grupaId = :grupaId)
              and (:predmetId is null or exists (select p.id from Predavanje p
                                                 where p.id = a.predavanjeId and p.predmet.id = :predmetId))
            order by a.otvoreno desc, a.id desc""")
    List<Anketa> istorijaZaMcp(@Param("predmetId") Long predmetId, @Param("grupaId") Long grupaId,
                               @Param("predavanjeId") Long predavanjeId, Pageable pageable);

    /** Redovi {@code [predavanjeId, zavrseno, nazivPredmeta]} datih predavanja. */
    @Query("select p.id, p.zavrseno, pr.naziv from Predavanje p join p.predmet pr where p.id in :ids")
    List<Object[]> predavanja(@Param("ids") Collection<Long> ids);

    /** Redovi {@code [grupaId, naziv]} datih grupa. */
    @Query("select g.id, g.naziv from Grupa g where g.id in :ids")
    List<Object[]> naziviGrupa(@Param("ids") Collection<Long> ids);

    /** Sopstvena grupa studenta ({@code studenti.grupa_id}), prazno kad je nema. */
    @Query("select s.grupa.id from Student s where s.id = :studentId and s.grupa is not null")
    Optional<Long> grupaStudenta(@Param("studentId") Long studentId);

    @Query("select count(c) from Cekiranje c where c.predavanje.id = :predavanjeId and c.student.id = :studentId")
    long brojCekiranja(@Param("predavanjeId") Long predavanjeId, @Param("studentId") Long studentId);

    default boolean cekiran(Long predavanjeId, Long studentId) {
        return brojCekiranja(predavanjeId, studentId) > 0;
    }

    /** Čekirani na predavanju (primaoci ankete na času). */
    @Query("select c.student.id from Cekiranje c where c.predavanje.id = :predavanjeId")
    List<Long> cekirani(@Param("predavanjeId") Long predavanjeId);

    /** Studenti grupe i čekirani na predavanju ({@code predavanjeId} sme biti {@code null}): primaoci ankete za grupu. */
    @Query(value = """
            SELECT id FROM studenti WHERE grupa_id = :grupaId
            UNION SELECT student_id FROM cekiranja WHERE predavanje_id = :predavanjeId""", nativeQuery = true)
    List<Long> studentiGrupeIPredavanja(@Param("grupaId") Long grupaId, @Param("predavanjeId") Long predavanjeId);

    /** Živa predavanja: današnja i nezavršena, sa predmetom i grupom, najnovija prva (podrazumevano za Kloda). */
    @Query("""
            select p from Predavanje p join fetch p.predmet left join fetch p.grupa
            where p.datum = :danas and (p.zavrseno is null or p.zavrseno = false)
            order by p.id desc""")
    List<Predavanje> zivaPredavanja(@Param("danas") LocalDate danas);
}
