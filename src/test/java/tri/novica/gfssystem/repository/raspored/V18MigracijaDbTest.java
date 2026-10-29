package tri.novica.gfssystem.repository.raspored;

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
 * Migracija V18 (raspored i najava odsustva) nad bazom sa podacima koja je stala na V17 (kao staging i prod pre
 * izdanja): nove tabele, {@code obavestenja.kljuc} sa jedinstvenim ključem (postojeća obaveštenja imaju NULL, više NULL
 * vrednosti je dozvoljeno); drugi {@code migrate()} je no-op, ponovljena V18 bez istorije prolazi; brisanje perioda ili
 * studenta briše stavke, otkazivanja i najave (FK CASCADE). Radi u sopstvenoj bazi {@code <test baza>_v18}.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V18MigracijaDbTest {

    /** Poslednja verzija pre rasporeda; menja se zajedno sa brojem {@code V18__raspored_najave.sql} pri merge-u. */
    static final String PRE_RASPOREDA = "17";
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
        baza = m.group(2) + "_v18";
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
    void tabeleKljucIdempotentnostIKaskada() {
        assertEquals(PRE_RASPOREDA, flyway(PRE_RASPOREDA).migrate().targetSchemaVersion);
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(urlBaze, korisnik, lozinka));
        db.update("insert into obavestenja (vrsta, podaci, kreirano) values ('NE_STIZE_KOD', '{}', now(3))");
        db.update("insert into obavestenja (vrsta, podaci, kreirano) values ('PREVISE_KODOVA', '{}', now(3))");

        assertTrue(flyway(null).migrate().migrationsExecuted >= 1, "V18 nije izvršena");
        for (String t : new String[]{"rasporedi", "raspored_stavke", "otkazani_termini", "najave_odsustva"}) {
            assertEquals(1, broj(db, "select count(*) from information_schema.TABLES where TABLE_SCHEMA = database() "
                    + "and TABLE_NAME = '" + t + "'"), t);
        }
        assertEquals(1, broj(db, "select count(*) from information_schema.STATISTICS where TABLE_SCHEMA = database() "
                + "and TABLE_NAME = 'obavestenja' and INDEX_NAME = 'uk_obavestenja_kljuc' and NON_UNIQUE = 0"));
        assertEquals(2, broj(db, "select count(*) from obavestenja where kljuc is null"));

        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        db.update("delete from flyway_schema_history where cast(version as unsigned) > " + PRE_RASPOREDA);
        assertTrue(flyway(null).migrate().migrationsExecuted >= 1, "ponovljena V18 nije prošla");

        db.update("insert into obavestenja (vrsta, podaci, kreirano, kljuc) values ('MNOGO_NAJAVA', '{}', now(3), 'k1')");
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "insert into obavestenja (vrsta, podaci, kreirano, kljuc) values ('MNOGO_NAJAVA', '{}', now(3), 'k1')"));

        db.update("insert into predmeti (naziv) values ('Migracija V18')");
        Long predmet = db.queryForObject("select id from predmeti where naziv = 'Migracija V18'", Long.class);
        db.update("insert into grupe (naziv, godina_upisa) values ('V18', 2026)");
        Long grupa = db.queryForObject("select id from grupe where naziv = 'V18'", Long.class);
        db.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values ('Ana', 'V18', 2026, 'GD918', ?)", grupa);
        db.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values ('Bora', 'V18', 2026, 'GD919', ?)", grupa);
        Long ana = db.queryForObject("select id from studenti where indeks = 'GD918'", Long.class);
        Long bora = db.queryForObject("select id from studenti where indeks = 'GD919'", Long.class);
        db.update("insert into rasporedi (naziv, vazi_od, vazi_do, kreirano) values ('Zima', '2030-10-01', '2031-01-31', now(6))");
        Long raspored = db.queryForObject("select id from rasporedi where naziv = 'Zima'", Long.class);
        assertEquals(5, broj(db, "select prag_najava from rasporedi where id = " + raspored), "podrazumevani prag");
        db.update("insert into raspored_stavke (raspored_id, predmet_id, grupa_id, dan, pocetak, kraj) values (?, ?, ?, 3, '10:00', '13:00')",
                raspored, predmet, grupa);
        Long stavka = db.queryForObject("select id from raspored_stavke where raspored_id = ?", Long.class, raspored);
        db.update("insert into otkazani_termini (stavka_id, datum, otkazano) values (?, '2030-11-13', now(6))", stavka);
        db.update("insert into najave_odsustva (stavka_id, datum, student_id, vreme) values (?, '2030-11-06', ?, now(6))", stavka, ana);
        db.update("insert into najave_odsustva (stavka_id, datum, student_id, vreme) values (?, '2030-11-06', ?, now(6))", stavka, bora);
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "insert into najave_odsustva (stavka_id, datum, student_id, vreme) values (?, '2030-11-06', ?, now(6))", stavka, ana));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "insert into otkazani_termini (stavka_id, datum, otkazano) values (?, '2030-11-13', now(6))", stavka));

        db.update("delete from studenti where id = ?", bora);
        assertEquals(1, broj(db, "select count(*) from najave_odsustva"));
        db.update("delete from rasporedi where id = ?", raspored);
        assertEquals(0, broj(db, "select count(*) from raspored_stavke"));
        assertEquals(0, broj(db, "select count(*) from otkazani_termini"));
        assertEquals(0, broj(db, "select count(*) from najave_odsustva"));
    }

    static int broj(JdbcTemplate db, String sql) {
        return db.queryForObject(sql, Integer.class);
    }
}
