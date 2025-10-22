package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.KoeficijentTipTesta;
import tri.novica.gfssystem.entity.KoeficijentiOcenjivanja;
import tri.novica.gfssystem.entity.TipTesta;

import java.util.List;
import java.util.Optional;

@Repository
public interface KoeficijentTipTestaRepository extends JpaRepository<KoeficijentTipTesta, Long> {

    List<KoeficijentTipTesta> findAllByKoeficijenti(KoeficijentiOcenjivanja koeficijenti);

    Optional<KoeficijentTipTesta> findByKoeficijentiAndTipTesta(KoeficijentiOcenjivanja koeficijenti, TipTesta tipTesta);

    void deleteAllByKoeficijenti(KoeficijentiOcenjivanja koeficijenti);
}
