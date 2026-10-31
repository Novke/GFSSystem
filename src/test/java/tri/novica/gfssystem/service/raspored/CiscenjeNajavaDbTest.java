package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Privatnost (pregled posle PR-a, tačka 5): razlog najave se briše 30 dana posle termina, red najave ostaje; mlađe najave
 * zadržavaju razlog.
 */
@SpringBootTest
@Import(RasporedSatConfig.class)
class CiscenjeNajavaDbTest {

    /** Sreda 6. 11. 2030; čas sredom. */
    static final LocalDateTime SADA = LocalDateTime.of(2030, 11, 6, 8, 0);

    @Autowired RasporedService rasporedi;
    @Autowired CiscenjeNajava ciscenje;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    RasporedPodaci d;

    @BeforeEach
    void setUp() {
        sat.postavi(SADA);
        d = new RasporedPodaci(jdbc, rasporedi);
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    void najava(Long stavka, LocalDate datum, Long student, String razlog) {
        jdbc.update("insert into najave_odsustva (stavka_id, datum, student_id, razlog, vreme) values (?, ?, ?, ?, ?)",
                stavka, datum, student, razlog, datum.atStartOfDay());
    }

    String razlog(Long stavka, LocalDate datum, Long student) {
        return jdbc.queryForObject("select razlog from najave_odsustva where stavka_id = ? and datum = ? and student_id = ?",
                String.class, stavka, datum, student);
    }

    @Test
    void razlogSeBrise30DanaPosleTerminaRedOstaje() {
        Long grupa = d.cas.grupa(2030);
        Long ana = d.cas.student(grupa, "Ana");
        Long stavka = d.period(LocalDate.of(2030, 9, 1), LocalDate.of(2031, 1, 31), 5,
                d.stavka(grupa, 3, "10:00", "13:00")).stavke().get(0).id();
        LocalDate star = LocalDate.of(2030, 10, 2);     // 35 dana pre SADA
        LocalDate granica = LocalDate.of(2030, 10, 7);  // tačno 30 dana pre (granica uključena)
        LocalDate mlad = LocalDate.of(2030, 10, 30);    // 7 dana pre
        najava(stavka, star, ana, "bolest");
        najava(stavka, granica, ana, "put");
        najava(stavka, mlad, ana, "lekar");

        int ocisceno = ciscenje.ocisti();

        assertTrue(ocisceno >= 2, "bar dve najave ovih podataka (u bazi mogu biti i stariji redovi drugih testova)");
        assertNull(razlog(stavka, star, ana));
        assertNull(razlog(stavka, granica, ana));
        assertEquals("lekar", razlog(stavka, mlad, ana));
        assertEquals(3, (int) jdbc.queryForObject("select count(*) from najave_odsustva where stavka_id = ?", Integer.class,
                stavka), "redovi najava ostaju");
        assertEquals(0, ciscenje.ocisti(), "drugi prolaz nema šta da očisti");
        assertEquals("lekar", razlog(stavka, mlad, ana));
    }
}
