package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Aktivnost;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.Student;

import java.util.Collection;
import java.util.List;

@Repository
public interface AktivnostRepository extends JpaRepository<Aktivnost, Long> {
    List<Aktivnost> findAllByStudentGrupaAndPredavanjePredmet(Grupa grupa, Predmet predmet);

    List<Aktivnost> findAllByStudentAndPredavanjePredmetOrderByPredavanjeDatumAsc(Student student, Predmet predmet);

    /**
     * Brojači prisutnih po predavanju, za stranicu liste, jednim prolazom. Svaka aktivnost (prisustvo, zadatak,
     * zvezdica) znači da je student bio prisutan. Redovi: {@code [predavanjeId, brojPrisutnih, brojStarijihPrisutnih]},
     * oba broja po različitim studentima; "stariji" su prisutni koji nisu u grupi predavanja (ponovci, premešteni,
     * studenti bez grupe), a za predavanje bez grupe je taj broj 0 (nema sa čim da se poredi).
     */
    @Query("""
            select p.id, count(distinct s.id),
                   count(distinct case when pg is not null and (sg is null or sg.id <> pg.id) then s.id end)
            from Aktivnost a join a.predavanje p left join p.grupa pg join a.student s left join s.grupa sg
            where p.id in :ids group by p.id""")
    List<Object[]> brojPrisutnihPoPredavanju(@Param("ids") Collection<Long> ids);

    /**
     * Broj različitih predavanja predmeta na kojima je bar jedan student grupe imao aktivnost: imenilac za normalizaciju
     * aktivnosti u ocenjivanju (isto što {@code getRezultati} broji iz aktivnosti cele grupe).
     */
    @Query("""
            select count(distinct a.predavanje.id) from Aktivnost a
            where a.student.grupa.id = :grupaId and a.predavanje.predmet.id = :predmetId""")
    long brojPredavanjaSaAktivnoscuGrupe(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);

    /**
     * Prisustvo studenata grupe (G2): redovi {@code [studentId, brojRazlicitihPredavanja]}, svako predavanje (bilo koje
     * grupe) na kojem student ima aktivnost; {@code predmetId} null = svi predmeti.
     */
    @Query("""
            select a.student.id, count(distinct a.predavanje.id) from Aktivnost a
            where a.student.grupa.id = :grupaId and (:predmetId is null or a.predavanje.predmet.id = :predmetId)
            group by a.student.id""")
    List<Object[]> prisustvoPoStudentu(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);

    /**
     * Isto kao {@link #prisustvoPoStudentu}, ali samo predavanja koja nisu predavanja grupe (druga grupa ili bez grupe):
     * ponovci i premešteni studenti. Redovi {@code [studentId, broj]}.
     */
    @Query("""
            select a.student.id, count(distinct p.id) from Aktivnost a join a.predavanje p left join p.grupa pg
            where a.student.grupa.id = :grupaId and (:predmetId is null or p.predmet.id = :predmetId)
              and (pg is null or pg.id <> :grupaId)
            group by a.student.id""")
    List<Object[]> prisustvoVanGrupePoStudentu(@Param("grupaId") Long grupaId, @Param("predmetId") Long predmetId);

    /** Ćelije matrice prisustva: redovi {@code [studentId, predavanjeId, tip]} za studente grupe na datim predavanjima. */
    @Query("""
            select a.student.id, a.predavanje.id, a.tip from Aktivnost a
            where a.predavanje.id in :predavanjaIds and a.student.grupa.id = :grupaId""")
    List<Object[]> celijePrisustva(@Param("predavanjaIds") Collection<Long> predavanjaIds, @Param("grupaId") Long grupaId);

    /** Sve aktivnosti studenta na predmetu, sa predavanjem, po datumu predavanja (kartica studenta). */
    @Query("""
            select a from Aktivnost a join fetch a.predavanje p
            where a.student.id = :studentId and p.predmet.id = :predmetId
            order by p.datum asc, p.id asc, a.id asc""")
    List<Aktivnost> aktivnostiStudentaNaPredmetu(@Param("studentId") Long studentId, @Param("predmetId") Long predmetId);
}
