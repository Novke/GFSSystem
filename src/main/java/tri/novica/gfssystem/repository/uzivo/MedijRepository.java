package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Medij;

import java.util.Collection;

@Repository
public interface MedijRepository extends JpaRepository<Medij, String> {

    /** Koliko od zadatih id-jeva postoji (jedan upit za proveru slika platna). */
    long countByIdIn(Collection<String> ids);
}
