package tri.novica.gfssystem.repository.test;

import org.springframework.data.jpa.repository.JpaRepository;
import tri.novica.gfssystem.entity.test.TestStavka;

import java.util.List;

public interface TestStavkaRepository extends JpaRepository<TestStavka, Long> {
    List<TestStavka> findByTestIdOrderByRb(Long testId);
}
