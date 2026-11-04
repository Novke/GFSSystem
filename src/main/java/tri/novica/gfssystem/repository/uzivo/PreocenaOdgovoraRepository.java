package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import tri.novica.gfssystem.entity.uzivo.PreocenaOdgovora;

import java.util.Collection;
import java.util.List;

public interface PreocenaOdgovoraRepository extends JpaRepository<PreocenaOdgovora, Long> {

    List<PreocenaOdgovora> findAllByOdgovorIdInAndSerijaOrderByIdAsc(Collection<Long> odgovori, String serija);
}
