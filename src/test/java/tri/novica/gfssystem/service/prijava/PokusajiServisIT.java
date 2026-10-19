package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PokusajiServis} nad pravom MySQL bazom, bez {@code @Transactional} na testu: zapis mora da preživi
 * rollback pozivaoca ({@code REQUIRES_NEW}), pa se ključevi (jedinstveni po testu) čiste ručno.
 */
@SpringBootTest
@Import(PrijavaSatConfig.class)
class PokusajiServisIT {

    @Autowired PokusajiServis servis;
    @Autowired PomerljivSat sat;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transakcije;

    String kljuc;

    @BeforeEach
    void setUp() {
        sat.postavi(Instant.now());
        kljuc = "test-" + UUID.randomUUID();
    }

    @AfterEach
    void ocisti() {
        jdbc.update("delete from pokusaji where kljuc = ?", kljuc);
    }

    @Test
    void desetZapisaDostizeLimit() {
        for (int i = 0; i < 9; i++) {
            servis.zabelezi(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc);
        }
        assertEquals(9, servis.broj(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc, Duration.ofMinutes(15)));
        assertFalse(servis.dostignut(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc, 10, Duration.ofMinutes(15)));
        servis.zabelezi(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc);
        assertTrue(servis.dostignut(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc, 10, Duration.ofMinutes(15)));
    }

    @Test
    void brojiSamoIstuVrstuIKljuc() {
        servis.zabelezi(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc);
        assertEquals(0, servis.broj(VrstaPokusaja.KOD_GRESKA_IP, kljuc, Duration.ofMinutes(15)));
        assertEquals(0, servis.broj(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc + "x", Duration.ofMinutes(15)));
    }

    @Test
    void starijiOdProzoraSeNeBroje() {
        for (int i = 0; i < 10; i++) {
            servis.zabelezi(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc);
        }
        assertTrue(servis.dostignut(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc, 10, Duration.ofMinutes(15)));
        sat.pomeri(Duration.ofMinutes(16));
        assertEquals(0, servis.broj(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc, Duration.ofMinutes(15)));
        assertFalse(servis.dostignut(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc, 10, Duration.ofMinutes(15)));
        // širi prozor ih i dalje vidi
        assertEquals(10, servis.broj(VrstaPokusaja.NASTAVNIK_GRESKA_IP, kljuc, Duration.ofHours(1)));
    }

    @Test
    void zapisOstajeKadPozivalacVratiTransakciju() {
        new TransactionTemplate(transakcije).execute(status -> {
            servis.zabelezi(VrstaPokusaja.KOD_SLANJE_IP, kljuc);
            status.setRollbackOnly();
            return null;
        });
        assertEquals(1, servis.broj(VrstaPokusaja.KOD_SLANJE_IP, kljuc, Duration.ofMinutes(15)));
        Integer uBazi = jdbc.queryForObject("select count(*) from pokusaji where kljuc = ?", Integer.class, kljuc);
        assertEquals(1, uBazi);
    }

    @Test
    void zapisOstajeKadPozivalacBaciIzuzetak() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transakcije).execute(status -> {
            servis.zabelezi(VrstaPokusaja.POMOC_IP, kljuc);
            throw new IllegalStateException("greška pozivaoca");
        }));
        assertEquals(1, servis.broj(VrstaPokusaja.POMOC_IP, kljuc, Duration.ofMinutes(15)));
    }
}
