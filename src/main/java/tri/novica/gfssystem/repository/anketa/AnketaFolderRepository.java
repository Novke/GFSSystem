package tri.novica.gfssystem.repository.anketa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.anketa.AnketaFolder;

import java.util.List;

@Repository
public interface AnketaFolderRepository extends JpaRepository<AnketaFolder, Long> {

    List<AnketaFolder> findAllByOrderByRbAscIdAsc();

    @Query("select coalesce(max(f.rb), 0) from AnketaFolder f")
    int najveciRb();
}
