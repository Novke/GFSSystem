package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.KljucAplikacije;

import java.util.Optional;

@Repository
public interface KljucAplikacijeRepository extends JpaRepository<KljucAplikacije, Long> {

    Optional<KljucAplikacije> findByNaziv(String naziv);
}
