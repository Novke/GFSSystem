package tri.novica.gfssystem.repository.anketa;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migracija V27 (brza anketa) nad bazom sa podacima koja je stala na V26 (kao staging i prod pre izdanja): postoje četiri
 * nove tabele i kolona {@code podesavanja_studenta.push_ankete} (postojeći red dobija 1); drugi {@code migrate()} je no-op,
 * ponovljena V27 nad bazom koja već ima njene objekte (bez istorije, kao reset staging baze) prolazi; FK: brisanje studenta
 * briše njegove glasove, predavanja i šablona ostavlja anketu ({@code SET NULL}), foldera ostavlja šablon, grupe briše njene
 * ankete i glasove; jedan glas po studentu i anketi. Radi u sopstvenoj bazi {@code <test baza>_v27}.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V27MigracijaDbTest {

    /** Poslednja verzija pre brze ankete; menja se zajedno sa brojem {@code V27__brza_anketa.sql} pri merge-u. */
    static final String PRE_ANKETE = "26";
    static final Pattern URL = Pattern.compile("^(jdbc:mysql://[^/]+/)([A-Za-z0-9_]+)(.*)$");

    @Autowired JdbcTemplate jdbc;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String korisnik;
    @Value("${spring.datasource.password:}") String lozinka;

    String baza;
    String urlBaze;

    @BeforeAll
    void napraviBazu() {
        Matcher m = URL.matcher(url);
        assertTrue(m.matches(), "spring.datasource.url nije jdbc:mysql://host/baza");
        baza = m.group(2) + "_v27";
        urlBaze = m.group(1) + baza + m.group(3);
        jdbc.execute("DROP DATABASE IF EXISTS `" + baza + "`");
        jdbc.execute("CREATE DATABASE `" + baza + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
    }

    @AfterAll
    void obrisiBazu() {
        jdbc.execute("DROP DATABASE IF EXISTS `" + baza + "`");
    }

    Flyway flyway(String target) {
        var konfig = Flyway.configure().dataSource(urlBaze, korisnik, lozinka).locations("classpath:db/migration");
        return (target == null ? konfig : konfig.target(target)).load();
    }

    @Test
    void tabeleKolonaIdempotentnostIKljucevi() {
        assertEquals(PRE_ANKETE, flyway(PRE_ANKETE).migrate().targetSchemaVersion);
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(urlBaze, korisnik, lozinka));
        db.update("insert into predmeti (naziv) values ('Migracija V27')");
        Long predmetId = db.queryForObject("select id from predmeti where naziv = 'Migracija V27'", Long.class);
        db.update("insert into grupe (naziv, godina_upisa) values ('V27', 2026)");
        Long grupaId = db.queryForObject("select id from grupe where naziv = 'V27'", Long.class);
        db.update("insert into predavanja (predmet_id, grupa_id, rb, datum, zavrseno) values (?, ?, 1, curdate(), 0)",
                predmetId, grupaId);
        Long predavanjeId = db.queryForObject("select id from predavanja where predmet_id = ?", Long.class, predmetId);
        db.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values ('Ana', 'V27', 2026, 'GD927', ?)", grupaId);
        db.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values ('Bora', 'V27', 2026, 'GD928', ?)", grupaId);
        Long ana = db.queryForObject("select id from studenti where indeks = 'GD927'", Long.class);
        Long bora = db.queryForObject("select id from studenti where indeks = 'GD928'", Long.class);
        db.update("insert into studenti (ime, prezime, godina, indeks) values ('Ceda', 'V27', 2026, 'GD929')");
        Long ceda = db.queryForObject("select id from studenti where indeks = 'GD929'", Long.class);
        db.update("insert into podesavanja_studenta (student_id, mejl_otkazan_cas, izmenjeno) values (?, b'0', now(6))", ana);

        assertTrue(flyway(null).migrate().migrationsExecuted >= 1, "V27 nije izvršena");
        for (String t : new String[]{"ankete_folderi", "ankete_sabloni", "ankete", "ankete_glasovi"}) {
            assertEquals(1, broj(db, "select count(*) from information_schema.TABLES where TABLE_SCHEMA = database() "
                    + "and TABLE_NAME = '" + t + "'"), t);
        }
        assertEquals(1, broj(db, "select count(*) from information_schema.STATISTICS where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'ankete_glasovi' and INDEX_NAME = 'uk_ankete_glasovi_anketa_student' "
                + "and NON_UNIQUE = 0 and SEQ_IN_INDEX = 1"));
        assertEquals(1, broj(db, "select count(*) from information_schema.STATISTICS where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'ankete' and INDEX_NAME = 'idx_ankete_predavanje' and SEQ_IN_INDEX = 1"));
        assertEquals(1, broj(db, "select push_ankete + 0 from podesavanja_studenta where student_id = " + ana),
                "postojeći red: push za ankete uključen");

        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        db.update("delete from flyway_schema_history where cast(version as unsigned) > " + PRE_ANKETE);
        assertTrue(flyway(null).migrate().migrationsExecuted >= 1, "ponovljena V27 nije prošla");
        assertEquals(1, broj(db, "select count(*) from information_schema.COLUMNS where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'podesavanja_studenta' and COLUMN_NAME = 'push_ankete'"));

        db.update("insert into ankete_folderi (naziv, rb, kreirano) values ('Tok časa', 1, now(6))");
        Long folderId = db.queryForObject("select id from ankete_folderi", Long.class);
        db.update("insert into ankete_sabloni (folder_id, pitanje, opcije, kreirano, izmenjeno) values (?, 'Pauza?', "
                + "'[\"Da\",\"Ne\"]', now(6), now(6))", folderId);
        Long sablonId = db.queryForObject("select id from ankete_sabloni", Long.class);
        assertEquals("NA_CASU", db.queryForObject("select publika from ankete_sabloni", String.class));
        db.update("insert into ankete (sablon_id, pitanje, opcije, predavanje_id, grupa_id, otvoreno) values (?, 'Pauza?', "
                + "'[\"Da\",\"Ne\"]', ?, ?, now(6))", sablonId, predavanjeId, grupaId);
        Long anketaId = db.queryForObject("select id from ankete", Long.class);
        assertEquals("NASTAVNIK", db.queryForObject("select izvor from ankete", String.class));
        db.update("insert into ankete_glasovi (anketa_id, student_id, opcije, vreme) values (?, ?, '0', now(6))", anketaId, ana);
        db.update("insert into ankete_glasovi (anketa_id, student_id, opcije, vreme) values (?, ?, '1', now(6))", anketaId, bora);
        db.update("insert into ankete_glasovi (anketa_id, student_id, opcije, vreme) values (?, ?, '0,1', now(6))", anketaId, ceda);
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "insert into ankete_glasovi (anketa_id, student_id, opcije, vreme) values (?, ?, '1', now(6))", anketaId, ana));

        db.update("delete from studenti where id = ?", bora);
        assertEquals(2, broj(db, "select count(*) from ankete_glasovi"), "glas obrisanog studenta nestaje");
        db.update("delete from ankete_folderi where id = ?", folderId);
        assertNull(db.queryForObject("select folder_id from ankete_sabloni where id = ?", Long.class, sablonId));
        db.update("delete from ankete_sabloni where id = ?", sablonId);
        db.update("delete from predavanja where id = ?", predavanjeId);
        assertEquals(1, broj(db, "select count(*) from ankete where sablon_id is null and predavanje_id is null"),
                "anketa ostaje u istoriji");
        db.update("delete from studenti where id = ?", ana);
        assertEquals(1, broj(db, "select count(*) from ankete_glasovi"));
        db.update("delete from grupe where id = ?", grupaId);
        assertEquals(0, broj(db, "select count(*) from ankete"));
        assertEquals(0, broj(db, "select count(*) from ankete_glasovi"));
    }

    static int broj(JdbcTemplate db, String sql) {
        return db.queryForObject(sql, Integer.class);
    }
}
