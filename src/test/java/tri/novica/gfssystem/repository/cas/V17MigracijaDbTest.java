package tri.novica.gfssystem.repository.cas;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migracija V17 (čekiranje) nad bazom sa podacima koja je stala na V16 (kao staging i prod pre izdanja): postoje nove
 * tabele i kolone predavanja, postojeće predavanje ima {@code NULL} tajnu; drugi {@code migrate()} je no-op, ponovljena V17
 * nad bazom koja već ima njene objekte (bez istorije, kao reset staging baze) prolazi; brisanje predavanja ili studenta
 * briše njihova čekiranja (FK CASCADE). Radi u sopstvenoj bazi {@code <test baza>_v17}.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V17MigracijaDbTest {

    /** Poslednja verzija pre čekiranja; menja se zajedno sa brojem {@code V17__cekiranje.sql} pri merge-u. */
    static final String PRE_CEKIRANJA = "16";
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
        baza = m.group(2) + "_v17";
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
    void tabeleKoloneIdempotentnostIKaskada() {
        assertEquals(PRE_CEKIRANJA, flyway(PRE_CEKIRANJA).migrate().targetSchemaVersion);
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(urlBaze, korisnik, lozinka));
        db.update("insert into predmeti (naziv) values ('Migracija V17')");
        Long predmetId = db.queryForObject("select id from predmeti where naziv = 'Migracija V17'", Long.class);
        db.update("insert into grupe (naziv, godina_upisa) values ('V17', 2026)");
        Long grupaId = db.queryForObject("select id from grupe where naziv = 'V17'", Long.class);
        db.update("insert into predavanja (predmet_id, grupa_id, rb, datum, zavrseno) values (?, ?, 1, curdate(), 0)",
                predmetId, grupaId);
        Long predavanjeId = db.queryForObject("select id from predavanja where predmet_id = ?", Long.class, predmetId);

        assertTrue(flyway(null).migrate().migrationsExecuted >= 1, "V17 nije izvršena");
        assertEquals(1, broj(db, "select count(*) from information_schema.STATISTICS where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'cekiranja' and INDEX_NAME = 'uk_cekiranja_predavanje_student' and SEQ_IN_INDEX = 1"));
        assertEquals(1, broj(db, "select count(*) from information_schema.STATISTICS where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'cekiranja' and INDEX_NAME = 'idx_cekiranja_student_vreme' and SEQ_IN_INDEX = 1"));
        assertEquals(1, broj(db, "select count(*) from information_schema.TABLES where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'cekiranja_na_cekanju'"));
        assertEquals(2, broj(db, "select count(*) from information_schema.COLUMNS where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'predavanja' and COLUMN_NAME in ('cekiranje_tajna', 'cekiranje_tajna_od')"));
        assertNull(db.queryForObject("select cekiranje_tajna from predavanja where id = ?", String.class, predavanjeId));

        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        db.update("delete from flyway_schema_history where cast(version as unsigned) > " + PRE_CEKIRANJA);
        assertTrue(flyway(null).migrate().migrationsExecuted >= 1, "ponovljena V17 nije prošla");

        db.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values ('Ana', 'V17', 2026, 'GD917', ?)", grupaId);
        db.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values ('Bora', 'V17', 2026, 'GD918', ?)", grupaId);
        Long ana = db.queryForObject("select id from studenti where indeks = 'GD917'", Long.class);
        Long bora = db.queryForObject("select id from studenti where indeks = 'GD918'", Long.class);
        db.update("insert into cekiranja (predavanje_id, student_id, vreme) values (?, ?, now(6))", predavanjeId, ana);
        db.update("insert into cekiranja (predavanje_id, student_id, vreme) values (?, ?, now(6))", predavanjeId, bora);
        db.update("insert into cekiranja_na_cekanju (kljuc_hash, predavanje_id, skenirano, istice) values (repeat('a', 64), ?, now(6), now(6))",
                predavanjeId);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> db.update(
                "insert into cekiranja (predavanje_id, student_id, vreme) values (?, ?, now(6))", predavanjeId, ana));

        db.update("delete from studenti where id = ?", bora);
        assertEquals(1, broj(db, "select count(*) from cekiranja"));
        db.update("delete from predavanja where id = ?", predavanjeId);
        assertEquals(0, broj(db, "select count(*) from cekiranja"));
        assertEquals(0, broj(db, "select count(*) from cekiranja_na_cekanju"));
    }

    static int broj(JdbcTemplate db, String sql) {
        return db.queryForObject(sql, Integer.class);
    }
}
