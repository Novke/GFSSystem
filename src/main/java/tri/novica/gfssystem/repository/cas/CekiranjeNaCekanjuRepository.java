package tri.novica.gfssystem.repository.cas;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tri.novica.gfssystem.entity.cas.CekiranjeNaCekanju;

import java.util.Optional;

@Repository
public interface CekiranjeNaCekanjuRepository extends JpaRepository<CekiranjeNaCekanju, String> {

    /** Zaključava red ključa do kraja transakcije: ključ se troši tačno jednom i kad dva zahteva stignu istovremeno. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from CekiranjeNaCekanju k where k.kljucHash = :h")
    Optional<CekiranjeNaCekanju> findByKljucHashForUpdate(@Param("h") String kljucHash);

    /** Briše neiskorišćene ključeve jednog telefona za predavanje: novo skeniranje istim telefonom poništava prethodni ključ. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CekiranjeNaCekanju k where k.predavanje.id = :p and k.uredjajHash = :u and k.iskorisceno is null")
    int obrisiNeiskorisceneZaUredjaj(@Param("p") Long predavanjeId, @Param("u") String uredjajHash);
}
