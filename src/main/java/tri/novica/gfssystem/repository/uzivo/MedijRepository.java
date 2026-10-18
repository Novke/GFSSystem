package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;

import java.util.Collection;
import java.util.List;

@Repository
public interface MedijRepository extends JpaRepository<Medij, String> {

    /** Vrste postojećih medija od zadatih id-jeva, jedna po nađenom redu (jedan upit za proveru slika platna). */
    @Query("select m.vrsta from Medij m where m.id in :ids")
    List<VrstaMedija> vrste(@Param("ids") Collection<String> ids);
}
