package tri.novica.gfssystem.repository.test;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tri.novica.gfssystem.entity.test.TestSmena;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TestSmenaRepository extends JpaRepository<TestSmena, Long> {

    /** Prva (u fazi 1 jedina) smena testa. */
    Optional<TestSmena> findFirstByTestIdOrderByRb(Long testId);

    List<TestSmena> findByTestIdIn(java.util.Collection<Long> testIds);

    /** Pokrenute, nezatvorene smene čiji je zajednički rok prošao pre {@code pre}: kandidati za zatvaranje po isteku. */
    @Query("select s from TestSmena s where s.pokrenuta is not null and s.zatvorena is null and s.rok < :pre")
    List<TestSmena> istekle(@Param("pre") LocalDateTime pre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TestSmena s where s.id = :id")
    Optional<TestSmena> findByIdForUpdate(@Param("id") Long id);
}
