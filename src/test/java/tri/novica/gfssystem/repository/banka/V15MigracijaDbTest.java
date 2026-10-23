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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migracija V15 (nivo varijante) nad bazom sa podacima koja je stala na V14 (kao staging i prod pre izdanja): postojeća
 * varijanta dobija {@code nivo = 2}, slajd i izvođenje {@code NULL} u {@code zadatak_nivo}; INSERT varijante bez kolone
 * (stara slika) i posle migracije dobija 2; drugi {@code migrate()} je no-op, a ponovljena V15 nad bazom koja već ima njene
 * kolone (bez istorije, kao reset staging baze) prolazi i ne dira podatke. Radi u sopstvenoj bazi {@code <test baza>_v15}.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V15MigracijaDbTest {

    /** Poslednja verzija pre nivoa varijante; menja se zajedno sa brojem {@code V15__nivo_varijante.sql} pri merge-u. */
    static final String PRE_NIVOA = "14";
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
        baza = m.group(2) + "_v15";
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
    void postojeciRedoviDobijajuPodrazumevaneVrednosti() {
        assertEquals(PRE_NIVOA, flyway(PRE_NIVOA).migrate().targetSchemaVersion);
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(urlBaze, korisnik, lozinka));
        db.update("insert into predmeti (naziv) values ('Migracija V15')");
        Long predmetId = db.queryForObject("select id from predmeti where naziv = 'Migracija V15'", Long.class);
        db.update("insert into grupe_zadataka (predmet_id, namena, naziv, rb, tezina, polja, kreirano, izmenjeno) "
                + "values (?, 'PREDAVANJE', 'Konverzije', 1, 2, '[]', now(6), now(6))", predmetId);
        Long grupaId = db.queryForObject("select id from grupe_zadataka where predmet_id = ?", Long.class, predmetId);
        Long varijantaId = novaVarijanta(db, grupaId, 1);
        db.update("insert into prezentacije (predmet_id, naziv, takmicenje, telefon_prikaz, detalji_dozvoljeni, kreirano, izmenjeno) "
                + "values (?, 'Migracija V15', 0, 'DUGMAD', 0, now(6), now(6))", predmetId);
        Long prezentacijaId = db.queryForObject("select id from prezentacije where predmet_id = ?", Long.class, predmetId);
        db.update("insert into slajdovi (prezentacija_id, rb, tip, naslov, sadrzaj, postepeno, grupa_zadataka_id) "
                + "values (?, 1, 'ZADATAK', 'Zadatak', null, 0, ?)", prezentacijaId, grupaId);
        Long slajdId = db.queryForObject("select id from slajdovi where prezentacija_id = ?", Long.class, prezentacijaId);
        db.update("insert into izvodjenja (prezentacija_id, kod, aktivan_kod, cuvanje, status, pocetak, prikaz, korak, "
                + "rezultati_prikazani, tacan_prikazan, rang_lista_prikazana, ekran, qr_prikazan, telefon_prikaz, "
                + "detalji_dozvoljeni, takmicenje, verzija) "
                + "values (?, 'ABC235', 'ABC235', 0, 'AKTIVNO', now(6), 'PRIJAVA', 0, 0, 0, 0, 'NORMALAN', 0, 'DUGMAD', 0, 0, 1)",
                prezentacijaId);
        Long izvodjenjeId = db.queryForObject("select id from izvodjenja where prezentacija_id = ?", Long.class, prezentacijaId);

        assertTrue(flyway(null).migrate().migrationsExecuted >= 1, "V15 nije izvršena");
        proveriPodrazumevane(db, varijantaId, slajdId, izvodjenjeId);
        // INSERT stare slike (bez kolone nivo) prolazi i dobija 2
        assertEquals(2, nivo(db, novaVarijanta(db, grupaId, 2)));

        assertEquals(0, flyway(null).migrate().migrationsExecuted);

        db.update("delete from flyway_schema_history where cast(version as unsigned) > " + PRE_NIVOA);
        assertTrue(flyway(null).migrate().migrationsExecuted >= 1);
        proveriPodrazumevane(db, varijantaId, slajdId, izvodjenjeId);
    }

    static Long novaVarijanta(JdbcTemplate db, Long grupaId, int rb) {
        db.update("insert into varijante (grupa_id, rb, tekst, odgovori, status, izvor, render_status, kreirano, izmenjeno) "
                + "values (?, ?, 'Tekst', '{}', 'NACRT', 'RUCNO', 'CEKA', now(6), now(6))", grupaId, rb);
        return db.queryForObject("select id from varijante where grupa_id = ? and rb = ?", Long.class, grupaId, rb);
    }

    static int nivo(JdbcTemplate db, Long varijantaId) {
        return db.queryForObject("select nivo from varijante where id = ?", Integer.class, varijantaId);
    }

    static void proveriPodrazumevane(JdbcTemplate db, Long varijantaId, Long slajdId, Long izvodjenjeId) {
        assertEquals(2, nivo(db, varijantaId));
        assertNull(db.queryForObject("select zadatak_nivo from slajdovi where id = ?", Integer.class, slajdId));
        assertNull(db.queryForObject("select zadatak_nivo from izvodjenja where id = ?", Integer.class, izvodjenjeId));
    }
}
