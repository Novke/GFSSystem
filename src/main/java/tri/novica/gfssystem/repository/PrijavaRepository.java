package tri.novica.gfssystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.Prijava;
import tri.novica.gfssystem.entity.StatusPrijave;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrijavaRepository extends JpaRepository<Prijava, Long> {

    List<Prijava> findAllBySesijaIdOrderByPodnetoAsc(Long sesijaId);

    List<Prijava> findAllBySesijaIdAndStatusOrderByPodnetoAsc(Long sesijaId, StatusPrijave status);

    Optional<Prijava> findByIdAndSesijaId(Long id, Long sesijaId);

    long countBySesijaId(Long sesijaId);

    long countByStatus(StatusPrijave status);

    long countBySesijaIdAndStatus(Long sesijaId, StatusPrijave status);

    boolean existsBySesijaIdAndIndeksAndGodinaAndStatus(Long sesijaId, String indeks, int godina, StatusPrijave status);

    boolean existsBySesijaIdAndIndeksAndGodinaAndStatusAndIdNot(Long sesijaId, String indeks, int godina,
                                                               StatusPrijave status, Long id);
}
