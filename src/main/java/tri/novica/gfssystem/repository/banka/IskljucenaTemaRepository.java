package tri.novica.gfssystem.repository.banka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.banka.IskljucenaTema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public interface IskljucenaTemaRepository extends JpaRepository<IskljucenaTema, Long> {

    /** Sve teme predmeta, najnovije prve. */
    List<IskljucenaTema> findByPredmetIdOrderByKreiranoDesc(Long predmetId);

    boolean existsByPredmetIdAndGrupaId(Long predmetId, Long grupaId);

    /** Isključene grupe predmeta kao redovi {@code [grupaId, temaId]} (oznaka u stablu bez učitavanja grupa); teme bez grupe nisu tu. */
    @Query("select t.grupa.id, t.id from IskljucenaTema t where t.predmet.id = :predmetId and t.grupa is not null")
    List<Object[]> grupePredmeta(@Param("predmetId") Long predmetId);

    /** {@link #grupePredmeta} kao mapa id grupe -> id teme (jedan upit). */
    default Map<Long, Long> iskljuceneGrupe(Long predmetId) {
        Map<Long, Long> rezultat = new HashMap<>();
        for (Object[] red : grupePredmeta(predmetId)) {
            rezultat.put(((Number) red[0]).longValue(), ((Number) red[1]).longValue());
        }
        return rezultat;
    }

    /** Briše temu jednim upitom i vraća broj obrisanih redova (0 = nije postojala, i kad je drugi poziv brže obrisao). */
    @Modifying
    @Query("delete from IskljucenaTema t where t.id = :id")
    int obrisiPoId(@Param("id") Long id);
}
