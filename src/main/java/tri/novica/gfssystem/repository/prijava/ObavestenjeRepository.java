package tri.novica.gfssystem.repository.prijava;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.prijava.Obavestenje;

@Repository
public interface ObavestenjeRepository extends JpaRepository<Obavestenje, Long> {

    /** Otvorena (nerešena) obaveštenja; redosled daje {@code pageable}. */
    Page<Obavestenje> findAllByResenoIsNull(Pageable pageable);

    long countByProcitanoIsNull();

    long countByResenoIsNull();
}
