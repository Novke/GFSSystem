package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.KoeficijentiOcenjivanja;
import tri.novica.gfssystem.entity.Predmet;

import java.util.Optional;

@Repository
public interface KoeficijentiOcenjivanjaRepository extends JpaRepository<KoeficijentiOcenjivanja, Long> {

    Optional<KoeficijentiOcenjivanja> findByPredmet(Predmet predmet);

    Optional<KoeficijentiOcenjivanja> findByPredmetId(Long predmetId);

    @Query("SELECT k FROM KoeficijentiOcenjivanja k " +
            "LEFT JOIN FETCH k.koeficijentiTipova kt " +
            "LEFT JOIN FETCH kt.tipTesta " +
            "WHERE k.predmet.id = :predmetId")
    Optional<KoeficijentiOcenjivanja> findByPredmetIdFetchTipovi(@Param("predmetId") Long predmetId);
}
