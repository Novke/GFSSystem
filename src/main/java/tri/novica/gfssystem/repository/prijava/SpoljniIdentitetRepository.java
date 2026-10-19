package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.SpoljniIdentitet;
import tri.novica.gfssystem.entity.prijava.VrstaIdentiteta;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpoljniIdentitetRepository extends JpaRepository<SpoljniIdentitet, Long> {

    /** Identitet po jedinstvenoj trojci (za LTI: {@code iss} i {@code sub}). */
    Optional<SpoljniIdentitet> findByVrstaAndIzdavacAndSubjekat(VrstaIdentiteta vrsta, String izdavac, String subjekat);

    List<SpoljniIdentitet> findAllByStudentId(Long studentId);
}
