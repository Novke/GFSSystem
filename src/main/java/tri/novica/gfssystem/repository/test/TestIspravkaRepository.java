package tri.novica.gfssystem.repository.test;

import org.springframework.data.jpa.repository.JpaRepository;
import tri.novica.gfssystem.entity.test.TestIspravka;

import java.util.List;
import java.util.Optional;

public interface TestIspravkaRepository extends JpaRepository<TestIspravka, Long> {

    /** Sve ispravke testa, i vraćene, redom upisa (poslednja KLJUC ispravka pobeđuje). */
    List<TestIspravka> findByTestIdOrderByIdAsc(Long testId);

    List<TestIspravka> findByTestIdAndVracenaIsNullOrderByIdAsc(Long testId);

    Optional<TestIspravka> findByIdAndTestId(Long id, Long testId);
}
