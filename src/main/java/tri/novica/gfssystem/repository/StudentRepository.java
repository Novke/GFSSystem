package tri.novica.gfssystem.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.utility.IndeksUtil;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
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

    /** Svi studenti sa godinom upisa {@code godina} (jedna generacija: desetine redova). */
    List<Student> findAllByGodina(int godina);

    /**
     * Studenti sa tačno ovim parom (kanonski indeks, godina upisa), najnoviji id prvi. Kolona se svodi na kanonski oblik u
     * Javi ({@link IndeksUtil#kanonski}: stari redovi mogu imati razmake, mala slova, vodeće nule ili sufiks
     * {@code /<godina>}), i poredi binarno: MySQL {@code utf8mb4_0900_ai_ci} bi izjednačio akcente i punu širinu.
     */
    default List<Student> findAllPoIndeksuIGodini(String kanonskiIndeks, int godina) {
        if (kanonskiIndeks == null) {
            return List.of();
        }
        return findAllByGodina(godina).stream()
                .filter(s -> kanonskiIndeks.equals(IndeksUtil.kanonski(s.getIndeks(), s.getGodina())))
                .sorted(Comparator.comparing(Student::getId).reversed())
                .toList();
    }

    /** Postoji li student sa parom (kanonski indeks, godina upisa); vidi {@link #findAllPoIndeksuIGodini}. */
    default boolean postojiStudent(String kanonskiIndeks, int godina) {
        return !findAllPoIndeksuIGodini(kanonskiIndeks, godina).isEmpty();
    }

    /** Isto što {@link #postojiStudent}, ali bez studenta koji se menja (izmena na sopstveni indeks i godinu je dozvoljena). */
    default boolean postojiDrugiStudent(String kanonskiIndeks, int godina, Long izuzetId) {
        return findAllPoIndeksuIGodini(kanonskiIndeks, godina).stream().anyMatch(s -> !s.getId().equals(izuzetId));
    }

    /**
     * Studenti sa normalizovanim indeksom (velika slova, bez razmaka, kao {@code IndeksUtil.normalizuj}), najnoviji upis
     * prvi ({@code godina} DESC, pa {@code id} DESC): isti indeks se ponavlja kroz godine upisa. PRIVREMENO: samo za stari
     * zahtev za kod bez godine (keširan SPA); ukloniti zajedno sa {@code IndeksUtil.razdvoji}.
     */
    @Query("""
        SELECT s FROM Student s
        WHERE UPPER(REPLACE(s.indeks, ' ', '')) = :indeks
        ORDER BY s.godina DESC, s.id DESC""")
    List<Student> findAllPoNormalizovanomIndeksu(@Param("indeks") String normalizovanIndeks, Pageable pageable);

    /** Svi studenti sa normalizovanim indeksom, najnoviji upis prvi (vidi gornji upit). */
    default List<Student> findAllPoNormalizovanomIndeksu(String normalizovanIndeks) {
        return findAllPoNormalizovanomIndeksu(normalizovanIndeks, Pageable.unpaged());
    }

    /** Zaključava red studenta (SELECT ... FOR UPDATE) do kraja transakcije: kodovi i limiti jednog studenta idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Student s WHERE s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);

    long countByGrupaId(Long grupaId);

    /** Broj studenata po grupi, za stranicu liste ({@code Brojaci.poId}). Redovi: {@code [grupaId, broj]}. */
    @Query("select s.grupa.id, count(s) from Student s where s.grupa.id in :ids group by s.grupa.id")
    List<Object[]> brojStudenataPoGrupi(@Param("ids") Collection<Long> ids);

    /**
     * Studenti iz grupa sa godinom upisa manjom od {@code godinaUpisa} koji imaju bar jednu aktivnost (predavanje sa
     * datumom u [{@code od}, {@code doDatuma}]) ili bar jedno polaganje (test sa datumom u tom opsegu) na predmetu:
     * ponovci koji dolaze na nastavu (kontrolna tabla daje opseg tekuće školske godine). Studenti bez grupe se ne broje.
     */
    @Query("""
        select count(s) from Student s
        where s.grupa.godinaUpisa < :godinaUpisa
          and (exists (select a.id from Aktivnost a where a.student = s and a.predavanje.predmet.id = :predmetId
                         and a.predavanje.datum between :od and :doDatuma)
            or exists (select p.id from Polaganje p where p.student = s and p.test.predmet.id = :predmetId
                         and p.test.datum between :od and :doDatuma))""")
    long brojStarijihNaPredmetu(@Param("predmetId") Long predmetId, @Param("godinaUpisa") int godinaUpisa,
                                @Param("od") LocalDate od, @Param("doDatuma") LocalDate doDatuma);
}
