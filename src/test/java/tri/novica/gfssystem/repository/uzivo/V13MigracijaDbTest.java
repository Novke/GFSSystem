package tri.novica.gfssystem.repository.uzivo;

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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migracija V13 (slajd Zadatak) nad bazom sa podacima koja je stala na V12 (kao staging i prod pre izdanja): postojeći
 * redovi slajdova, izvođenja, učesnika i odgovora upisani bez novih kolona dobijaju {@code boduje = 0},
 * {@code resenje_prikazano = 0} i {@code NULL} u svim novim nullable kolonama; drugi {@code migrate()} je no-op, a ponovljena
 * V13 nad bazom koja već ima njene objekte (bez istorije, kao reset staging baze) prolazi i ništa ne menja. Radi u sopstvenoj
 * bazi {@code <test baza>_v13} (pravi je i briše ovaj test kroz JDBC), da Flyway istorija glavne test baze ostane netaknuta.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V13MigracijaDbTest {

    /** Poslednja verzija pre migracije slajda Zadatak; menja se zajedno sa brojem {@code V13__slajd_zadatak.sql} pri merge-u. */
    static final String PRE_ZADATKA = "12";
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
        baza = m.group(2) + "_v13";
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
        assertEquals(PRE_ZADATKA, flyway(PRE_ZADATKA).migrate().targetSchemaVersion);
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(urlBaze, korisnik, lozinka));
        // redovi kako ih piše slika pre V13: bez novih kolona
        db.update("insert into predmeti (naziv) values ('Migracija V13')");
        Long predmetId = db.queryForObject("select id from predmeti where naziv = 'Migracija V13'", Long.class);
        db.update("insert into prezentacije (predmet_id, naziv, takmicenje, telefon_prikaz, detalji_dozvoljeni, kreirano, izmenjeno) "
                + "values (?, 'Migracija V13', 0, 'DUGMAD', 0, now(6), now(6))", predmetId);
        Long prezentacijaId = db.queryForObject("select id from prezentacije where predmet_id = ?", Long.class, predmetId);
        db.update("insert into slajdovi (prezentacija_id, rb, tip, naslov, sadrzaj, postepeno) "
                + "values (?, 1, 'INFO', 'Uvod', 'Tekst slajda', 0)", prezentacijaId);
        Long slajdId = db.queryForObject("select id from slajdovi where prezentacija_id = ?", Long.class, prezentacijaId);
        db.update("insert into izvodjenja (prezentacija_id, kod, aktivan_kod, cuvanje, status, pocetak, prikaz, korak, "
                + "rezultati_prikazani, tacan_prikazan, rang_lista_prikazana, ekran, qr_prikazan, telefon_prikaz, "
                + "detalji_dozvoljeni, takmicenje, verzija) "
                + "values (?, 'ABC234', 'ABC234', 0, 'AKTIVNO', now(6), 'PRIJAVA', 0, 0, 0, 0, 'NORMALAN', 0, 'DUGMAD', 0, 0, 1)",
                prezentacijaId);
        Long izvodjenjeId = db.queryForObject("select id from izvodjenja where prezentacija_id = ?", Long.class, prezentacijaId);
        db.update("insert into ucesnici (izvodjenje_id, ime, token_hash, izbacen, kreirano) values (?, 'Ana', ?, 0, now(6))",
                izvodjenjeId, "a".repeat(64));
        Long ucesnikId = db.queryForObject("select id from ucesnici where izvodjenje_id = ?", Long.class, izvodjenjeId);
        db.update("insert into pitanje_runde (izvodjenje_id, slajd_id, redni_broj, snimak, otvoreno) "
                + "values (?, ?, 1, '{}', now(6))", izvodjenjeId, slajdId);
        Long rundaId = db.queryForObject("select id from pitanje_runde where izvodjenje_id = ?", Long.class, izvodjenjeId);
        db.update("insert into odgovori (runda_id, ucesnik_id, opcije, poeni, vreme_ms, sakriven, kreirano) "
                + "values (?, ?, '1', 0, 1200, 0, now(6))", rundaId, ucesnikId);

        var rezultat = flyway(null).migrate();
        assertTrue(rezultat.migrationsExecuted >= 1, "V13 nije izvršena");
        proveriPodrazumevane(db, slajdId, izvodjenjeId, ucesnikId, rundaId);
        assertEquals(0, db.queryForObject("select count(*) from zadatak_dodele", Integer.class));

        // drugi migrate: ništa novo
        assertEquals(0, flyway(null).migrate().migrationsExecuted);

        // idempotentnost: istorija bez V13 (objekti već postoje, kao reset staging baze) -> V13 ponovo prolazi i ne dira podatke
        db.update("delete from flyway_schema_history where cast(version as unsigned) > " + PRE_ZADATKA);
        assertTrue(flyway(null).migrate().migrationsExecuted >= 1);
        proveriPodrazumevane(db, slajdId, izvodjenjeId, ucesnikId, rundaId);
    }

    static void proveriPodrazumevane(JdbcTemplate db, Long slajdId, Long izvodjenjeId, Long ucesnikId, Long rundaId) {
        Map<String, Object> slajd = db.queryForMap("select grupa_zadataka_id, zadatak_vreme_sekunde, zadatak_broj_varijanti "
                + "from slajdovi where id = ?", slajdId);
        assertNull(slajd.get("grupa_zadataka_id"));
        assertNull(slajd.get("zadatak_vreme_sekunde"));
        assertNull(slajd.get("zadatak_broj_varijanti"));
        Map<String, Object> izv = db.queryForMap("select cast(boduje as unsigned) boduje, "
                + "cast(resenje_prikazano as unsigned) resenje_prikazano, zadatak_broj_varijanti from izvodjenja where id = ?",
                izvodjenjeId);
        assertEquals(0L, ((Number) izv.get("boduje")).longValue());
        assertEquals(0L, ((Number) izv.get("resenje_prikazano")).longValue());
        assertNull(izv.get("zadatak_broj_varijanti"));
        assertNull(db.queryForObject("select student_id from ucesnici where id = ?", Long.class, ucesnikId));
        Map<String, Object> odg = db.queryForMap("select polja, varijanta_id from odgovori where runda_id = ?", rundaId);
        assertNull(odg.get("polja"));
        assertNull(odg.get("varijanta_id"));
    }
}
