package tri.novica.gfssystem.repository.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.portal.ObjavljenaOcena;

import java.util.Optional;

@Repository
public interface ObjavljenaOcenaRepository extends JpaRepository<ObjavljenaOcena, ObjavljenaOcena.Kljuc> {

    Optional<ObjavljenaOcena> findByObjavaIdAndStudentId(Long objavaId, Long studentId);

    long countByObjavaId(Long objavaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ObjavljenaOcena o where o.objavaId = :objavaId")
    int obrisiZaObjavu(@Param("objavaId") Long objavaId);
}
