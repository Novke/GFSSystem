package tri.novica.gfssystem.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.OnboardingSesija;
import tri.novica.gfssystem.entity.StatusPrijave;

import java.util.List;
import java.util.Optional;

@Repository
public interface OnboardingSesijaRepository extends JpaRepository<OnboardingSesija, Long> {

    Optional<OnboardingSesija> findByToken(String token);

    /** Zaključava red sesije (SELECT ... FOR UPDATE) do kraja transakcije: prijave i obrade jedne sesije idu redom. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM OnboardingSesija s WHERE s.token = :token")
    Optional<OnboardingSesija> findByTokenForUpdate(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM OnboardingSesija s WHERE s.id = :id")
    Optional<OnboardingSesija> findByIdForUpdate(@Param("id") Long id);

    List<OnboardingSesija> findAllByGrupaIdOrderByKreiranoDesc(Long grupaId);

    boolean existsByToken(String token);

    /** Sesije sa bar jednom prijavom u statusu (otvorene ili ne), prvo one kojima rok ističe najranije. */
    @Query("""
        SELECT s FROM OnboardingSesija s
        WHERE EXISTS (SELECT p.id FROM Prijava p WHERE p.sesija = s AND p.status = :status)
        ORDER BY s.istice ASC, s.id ASC""")
    List<OnboardingSesija> saPrijavamaUStatusu(@Param("status") StatusPrijave status, Pageable pageable);
}
