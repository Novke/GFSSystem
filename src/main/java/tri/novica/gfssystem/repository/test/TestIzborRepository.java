package tri.novica.gfssystem.repository.test;

import org.springframework.data.jpa.repository.JpaRepository;
import tri.novica.gfssystem.entity.test.TestIzbor;

import java.util.List;

public interface TestIzborRepository extends JpaRepository<TestIzbor, Long> {
    List<TestIzbor> findByTestIdOrderByStavkaIdAscGrupaTestaAscMestoAsc(Long testId);
}
