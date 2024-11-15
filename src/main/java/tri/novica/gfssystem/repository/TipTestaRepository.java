package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.TipTesta;

import java.util.List;

@Repository
public interface TipTestaRepository extends JpaRepository<TipTesta, Long> {
    List<TipTesta> findAllByPredmetAndAktivanTrue(Predmet predmet);
}
