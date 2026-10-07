package tri.novica.gfssystem.repository.uzivo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;

import java.util.List;
import java.util.Optional;

@Repository
public interface UcesnikRepository extends JpaRepository<Ucesnik, Long> {

    Optional<Ucesnik> findByTokenHash(String h);

    List<Ucesnik> findAllByIzvodjenjeIdOrderByKreiranoAsc(Long id);

    long countByIzvodjenjeIdAndIzbacenFalse(Long id);

    void deleteAllByIzvodjenjeId(Long id);
}
