package tri.novica.gfssystem.repository.banka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.banka.IskljucenaTema;

import java.util.List;

@Repository
public interface IskljucenaTemaRepository extends JpaRepository<IskljucenaTema, Long> {

    /** Sve teme predmeta, najnovije prve. */
    List<IskljucenaTema> findByPredmetIdOrderByKreiranoDesc(Long predmetId);

    boolean existsByPredmetIdAndGrupaId(Long predmetId, Long grupaId);

    /** Isključene grupe predmeta kao redovi {@code [grupaId, temaId]} (oznaka u stablu bez učitavanja grupa); teme bez grupe nisu tu. */
    @Query("select t.grupa.id, t.id from IskljucenaTema t where t.predmet.id = :predmetId and t.grupa is not null")
    List<Object[]> grupePredmeta(@Param("predmetId") Long predmetId);
}
