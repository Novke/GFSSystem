package tri.novica.gfssystem.repository.banka;

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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migracija V12 nad bazom sa podacima koja je stala na V11 (kao staging i prod pre izdanja): postojeće odobrene varijante
 * ({@code AKTIVNA}, {@code ARHIVIRANA}) dobijaju {@code ikad_odobrena = 1}, nacrt ostaje 0, stari redovi dobijaju
 * podrazumevane {@code izvor} i {@code serija}; drugi {@code migrate()} je no-op, a ponovljena V12 nad bazom koja već ima
 * njene objekte (bez istorije, kao reset staging baze) prolazi i ništa ne menja. Radi u sopstvenoj bazi
 * {@code <test baza>_v12} (pravi je i briše ovaj test kroz JDBC), da Flyway istorija glavne test baze ostane netaknuta.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V12MigracijaDbTest {

    /** Poslednja verzija pre migracije MCP banke; menja se zajedno sa brojem {@code V12__mcp_banka.sql} pri merge-u. */
    static final String PRE_MCP = "11";
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
        baza = m.group(2) + "_v12";
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
    void odobreneVarijanteDobijajuIkadOdobrena() {
        assertEquals(PRE_MCP, flyway(PRE_MCP).migrate().targetSchemaVersion);
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(urlBaze, korisnik, lozinka));
        db.update("insert into predmeti (naziv) values ('Migracija V12')");
        Long predmetId = db.queryForObject("select id from predmeti where naziv = 'Migracija V12'", Long.class);
        db.update("insert into grupe_zadataka (predmet_id, namena, naziv, rb, tezina, polja, kreirano, izmenjeno) "
                + "values (?, 'PREDAVANJE', 'Konverzije', 1, 2, '[]', now(6), now(6))", predmetId);
        Long grupaId = db.queryForObject("select id from grupe_zadataka where predmet_id = ?", Long.class, predmetId);
        int rb = 0;
        for (String status : List.of("NACRT", "AKTIVNA", "ARHIVIRANA")) {
            db.update("insert into varijante (grupa_id, rb, tekst, odgovori, status, izvor, render_status, kreirano, izmenjeno) "
                    + "values (?, ?, 'Zadatak.', '{}', ?, 'RUCNO', 'OK', now(6), now(6))", grupaId, ++rb, status);
        }

        var rezultat = flyway(null).migrate();
        assertTrue(rezultat.migrationsExecuted >= 1, "V12 nije izvršena");
        assertEquals(List.of(0, 1, 1), ikadOdobrena(db));
        assertEquals("RUCNO", db.queryForObject("select izvor from grupe_zadataka where id = ?", String.class, grupaId));
        assertEquals(0, db.queryForObject("select count(*) from grupe_zadataka where serija is not null", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from varijante where serija is not null or zamenjuje_id is not null",
                Integer.class));

        // drugi migrate: ništa novo
        assertEquals(0, flyway(null).migrate().migrationsExecuted);

        // idempotentnost: istorija bez V12 (objekti već postoje, kao reset staging baze) -> V12 ponovo prolazi i ne dira
        // podatke. ARHIVIRANA je ovde veštački vraćena na 0: UPDATE je u istom uslovnom bloku kao ADD COLUMN, pa se ne
        // ponavlja kad kolona već postoji (inače bi opet bila 1)
        db.update("update varijante set ikad_odobrena = 0 where status = 'ARHIVIRANA'");
        db.update("delete from flyway_schema_history where cast(version as unsigned) > " + PRE_MCP);
        assertTrue(flyway(null).migrate().migrationsExecuted >= 1);
        assertEquals(List.of(0, 1, 0), ikadOdobrena(db));
    }

    static List<Integer> ikadOdobrena(JdbcTemplate db) {
        return db.queryForList("select ikad_odobrena from varijante order by rb", Integer.class);
    }
}
