package tri.novica.gfssystem.migracija;

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
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.service.uzivo.Ocenjivac;
import tri.novica.gfssystem.service.uzivo.OpcijaSnimak;
import tri.novica.gfssystem.service.uzivo.PitanjeSnimak;
import tri.novica.gfssystem.service.uzivo.SnimakRunde;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Popuna unazad iz V26 (delimično tačno) nad bazom sa podacima koja je stala na V25 (kao staging i prod pre izdanja):
 * odgovori na {@code VISE_TACNIH} čiji je {@code tacno} jednak oceni „tačno skup tačnih“ dobijaju udeo po formuli
 * ({@link Ocenjivac#udeoIzbora}) i, u takmičenju, poene po tom udelu ({@link Ocenjivac#poeni}); preocenjen odgovor, ostali
 * tipovi i zadatak dobijaju {@code IF(tacno, 1, 0)} bez promene poena; odgovor bez tačnog odgovora ostaje {@code NULL}. Trag
 * preocene dobija {@code staro_udeo} po istom pravilu iz {@code staro_tacno}. Ponovljena V26 (bez istorije, kao reset
 * staging baze) ništa ne menja. Radi u sopstvenoj bazi {@code <test baza>_v26}, kao {@code V13MigracijaDbTest}.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopunaUdelaIT {

    /** Poslednja verzija pre delimično tačnog; menja se zajedno sa brojem {@code V26__delimicno_tacno.sql} pri merge-u. */
    static final String PRE_UDELA = "25";
    static final Pattern URL = Pattern.compile("^(jdbc:mysql://[^/]+/)([A-Za-z0-9_]+)(.*)$");
    static final long TRAJANJE_MS = 10_000L;

    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper jsonMapper;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String korisnik;
    @Value("${spring.datasource.password:}") String lozinka;

    String baza;
    String urlBaze;
    JdbcTemplate db;
    int brojUcesnika;

    @BeforeAll
    void napraviBazu() {
        Matcher m = URL.matcher(url);
        assertTrue(m.matches(), "spring.datasource.url nije jdbc:mysql://host/baza");
        baza = m.group(2) + "_v26";
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

    static OpcijaSnimak opcija(long id, int rb, boolean tacna) {
        return new OpcijaSnimak(id, rb, String.valueOf((char) ('A' + rb - 1)), tacna);
    }

    static PitanjeSnimak pitanje(TipPitanja tip, List<OpcijaSnimak> opcije) {
        return new PitanjeSnimak(1L, 1L, tip, "Pitanje", null, 10, opcije, null, null, null, null, null, null, null, null);
    }

    @Test
    void popunaSeSlazeSaFormulom() {
        assertEquals(PRE_UDELA, flyway(PRE_UDELA).migrate().targetSchemaVersion);
        db = new JdbcTemplate(new DriverManagerDataSource(urlBaze, korisnik, lozinka));

        // VISE_TACNIH: A i B tačne od četiri; drugo pitanje sa tri tačne (A, B, C) od četiri
        PitanjeSnimak dveTacne = pitanje(TipPitanja.VISE_TACNIH,
                List.of(opcija(101, 1, true), opcija(102, 2, true), opcija(103, 3, false), opcija(104, 4, false)));
        PitanjeSnimak triTacne = pitanje(TipPitanja.VISE_TACNIH,
                List.of(opcija(201, 1, true), opcija(202, 2, true), opcija(203, 3, true), opcija(204, 4, false)));
        PitanjeSnimak jedanTacan = pitanje(TipPitanja.JEDAN_TACAN, List.of(opcija(301, 1, true), opcija(302, 2, false)));
        PitanjeSnimak anketa = pitanje(TipPitanja.ANKETA, List.of(opcija(401, 1, false), opcija(402, 2, false)));
        String snimakDveTacne = jsonMapper.writeValueAsString((SnimakRunde) dveTacne);
        assertTrue(snimakDveTacne.contains("\"vrsta\":\"PITANJE\""), snimakDveTacne);
        // snimak upisan pre Zadatak slajda nema "vrsta" i čita se kao pitanje
        String snimakTriTacneBezVrste = jsonMapper.writeValueAsString((SnimakRunde) triTacne)
                .replace("\"vrsta\":\"PITANJE\",", "");
        assertFalse(snimakTriTacneBezVrste.contains("vrsta"), snimakTriTacneBezVrste);

        Long takmicenje = izvodjenje("TAK234", true);
        Long rVise = runda(takmicenje, 1, snimakDveTacne);
        Long rTri = runda(takmicenje, 2, snimakTriTacneBezVrste);
        Long rJedan = runda(takmicenje, 3, jsonMapper.writeValueAsString((SnimakRunde) jedanTacan));
        Long rZadatak = runda(takmicenje, 4, "{\"vrsta\":\"ZADATAK\",\"slajdId\":null,\"varijante\":[]}");
        Long rAnketa = runda(takmicenje, 5, jsonMapper.writeValueAsString((SnimakRunde) anketa));
        Long bezTakmicenja = izvodjenje("BEZ234", false);
        Long rViseBez = runda(bezTakmicenja, 1, snimakDveTacne);

        Long u1 = ucesnik(takmicenje), u2 = ucesnik(takmicenje), u3 = ucesnik(takmicenje), u4 = ucesnik(takmicenje),
                u5 = ucesnik(takmicenje), u6 = ucesnik(bezTakmicenja);

        // poeni kako ih je pisala slika pre V26: sve ili ništa
        Long samoA = odgovor(rVise, u1, "101", false, 0, 3333);
        Long obe = odgovor(rVise, u2, "101,102", true, Ocenjivac.poeni(1.0, true, TRAJANJE_MS, 2000), 2000);
        Long aIC = odgovor(rVise, u3, "101,103", false, 0, 4000);
        Long preocenjen = odgovor(rVise, u4, "101", true, Ocenjivac.poeni(1.0, true, TRAJANJE_MS, 5000), 5000);
        Long obeIPogresna = odgovor(rVise, u5, "101,102,103", false, 0, 6000);
        Long dveOdTri = odgovor(rTri, u1, "201,202", false, 0, 1234);
        Long jedan = odgovor(rJedan, u1, "301", true, 777, 1500);
        db.update("insert into odgovori (runda_id, ucesnik_id, polja, tacno, poeni, vreme_ms, sakriven, kreirano) "
                + "values (?, ?, '{\"a\":{\"vrednost\":\"5\",\"tacno\":false}}', 0, 0, 900, 0, now(6))", rZadatak, u1);
        Long zadatak = db.queryForObject("select id from odgovori where runda_id = ?", Long.class, rZadatak);
        Long anketaOdg = odgovor(rAnketa, u1, "401", null, 0, 800);
        Long bezTak = odgovor(rViseBez, u6, "101", false, 0, 3000);

        // trag preocene: {A} je bio netačan (0 poena), nastavnik ga je proglasio tačnim
        db.update("insert into preocene_odgovora (odgovor_id, staro_tacno, novo_tacno, stara_polja, stari_poeni, razlog, "
                + "serija, kreirano) values (?, 0, 1, null, 0, 'Prihvatam', 'preocena-test', now(6))", preocenjen);
        Long trag = db.queryForObject("select id from preocene_odgovora where odgovor_id = ?", Long.class, preocenjen);

        var rezultat = flyway(null).migrate();
        assertTrue(rezultat.migrationsExecuted >= 1, "V26 nije izvršena");

        assertEquals(0.5, udeo(samoA));
        assertEquals(Ocenjivac.udeoIzbora(dveTacne, List.of(101L)), udeo(samoA));
        assertEquals(Ocenjivac.poeni(0.5, true, TRAJANJE_MS, 3333), poeni(samoA));
        assertEquals(1.0, udeo(obe));
        assertEquals(Ocenjivac.udeoIzbora(dveTacne, List.of(101L, 102L)), udeo(obe));
        assertEquals(Ocenjivac.poeni(1.0, true, TRAJANJE_MS, 2000), poeni(obe));
        assertEquals(0.0, udeo(aIC));
        assertEquals(Ocenjivac.udeoIzbora(dveTacne, List.of(101L, 103L)), udeo(aIC));
        assertEquals(0, poeni(aIC));
        // preocena ima prednost: tacno=1 nije ocena skupa {A}, pa udeo = 1 i poeni ostaju
        assertEquals(1.0, udeo(preocenjen));
        assertEquals(Ocenjivac.poeni(1.0, true, TRAJANJE_MS, 5000), poeni(preocenjen));
        assertEquals(0.5, udeo(obeIPogresna));
        assertEquals(Ocenjivac.udeoIzbora(dveTacne, List.of(101L, 102L, 103L)), udeo(obeIPogresna));
        assertEquals(Ocenjivac.poeni(0.5, true, TRAJANJE_MS, 6000), poeni(obeIPogresna));
        // 2/3 tačno kao u Javi (DOUBLE, bez zaokruživanja na 4 decimale)
        assertEquals(Ocenjivac.udeoIzbora(triTacne, List.of(201L, 202L)), udeo(dveOdTri));
        assertEquals(2.0 / 3, udeo(dveOdTri));
        assertEquals(Ocenjivac.poeni(2.0 / 3, true, TRAJANJE_MS, 1234), poeni(dveOdTri));
        assertEquals(1.0, udeo(jedan));
        assertEquals(777, poeni(jedan));
        assertEquals(0.0, udeo(zadatak));
        assertEquals(0, poeni(zadatak));
        assertNull(udeo(anketaOdg));
        // bez takmičenja: udeo po formuli, poeni ostaju 0
        assertEquals(0.5, udeo(bezTak));
        assertEquals(0, poeni(bezTak));

        // trag: staro_tacno=0 je ocena skupa {A}, pa staro_udeo po formuli i stari poeni po njemu (vrati_preocenu vraća
        // stanje kakvo bi dao nov odgovor)
        Map<String, Object> t = db.queryForMap("select staro_udeo, stari_poeni from preocene_odgovora where id = ?", trag);
        assertEquals(0.5, t.get("staro_udeo"));
        assertEquals(Ocenjivac.poeni(0.5, true, TRAJANJE_MS, 5000), ((Number) t.get("stari_poeni")).intValue());

        // drugi migrate: ništa novo
        List<Map<String, Object>> posle = stanje();
        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        // ponovljena V26 (istorija bez nje, kolone postoje, kao reset staging baze): ništa se ne menja
        db.update("delete from flyway_schema_history where cast(version as unsigned) > " + PRE_UDELA);
        assertTrue(flyway(null).migrate().migrationsExecuted >= 1);
        assertEquals(posle, stanje());
    }

    Long izvodjenje(String kod, boolean takmicenje) {
        db.update("insert into predmeti (naziv) values (?)", "Migracija V26 " + kod);
        Long predmetId = db.queryForObject("select id from predmeti where naziv = ?", Long.class, "Migracija V26 " + kod);
        db.update("insert into prezentacije (predmet_id, naziv, takmicenje, telefon_prikaz, detalji_dozvoljeni, kreirano, "
                + "izmenjeno) values (?, ?, ?, 'DUGMAD', 0, now(6), now(6))", predmetId, "Migracija V26 " + kod, takmicenje);
        Long prezentacijaId = db.queryForObject("select id from prezentacije where predmet_id = ?", Long.class, predmetId);
        db.update("insert into izvodjenja (prezentacija_id, kod, aktivan_kod, cuvanje, status, pocetak, kraj, prikaz, korak, "
                + "rezultati_prikazani, tacan_prikazan, rang_lista_prikazana, ekran, qr_prikazan, telefon_prikaz, "
                + "detalji_dozvoljeni, takmicenje, verzija) "
                + "values (?, ?, null, 1, 'ZAVRSENO', now(6), now(6), 'PRIJAVA', 0, 0, 0, 0, 'NORMALAN', 0, 'DUGMAD', 0, ?, 1)",
                prezentacijaId, kod, takmicenje);
        return db.queryForObject("select id from izvodjenja where kod = ?", Long.class, kod);
    }

    Long runda(Long izvodjenjeId, int rb, String snimak) {
        db.update("insert into pitanje_runde (izvodjenje_id, slajd_id, redni_broj, snimak, otvoreno, zatvoreno, trajanje_ms) "
                + "values (?, null, ?, ?, now(6), now(6), ?)", izvodjenjeId, rb, snimak, TRAJANJE_MS);
        return db.queryForObject("select id from pitanje_runde where izvodjenje_id = ? and redni_broj = ?", Long.class,
                izvodjenjeId, rb);
    }

    Long ucesnik(Long izvodjenjeId) {
        brojUcesnika++;
        String token = String.format("%064d", brojUcesnika);
        db.update("insert into ucesnici (izvodjenje_id, ime, token_hash, izbacen, kreirano) values (?, ?, ?, 0, now(6))",
                izvodjenjeId, "Učesnik " + brojUcesnika, token);
        return db.queryForObject("select id from ucesnici where token_hash = ?", Long.class, token);
    }

    Long odgovor(Long rundaId, Long ucesnikId, String opcije, Boolean tacno, int poeni, long vremeMs) {
        db.update("insert into odgovori (runda_id, ucesnik_id, opcije, tacno, poeni, vreme_ms, sakriven, kreirano) "
                + "values (?, ?, ?, ?, ?, ?, 0, now(6))", rundaId, ucesnikId, opcije, tacno, poeni, vremeMs);
        return db.queryForObject("select id from odgovori where runda_id = ? and ucesnik_id = ?", Long.class, rundaId,
                ucesnikId);
    }

    Double udeo(Long odgovorId) {
        return db.queryForObject("select udeo from odgovori where id = ?", Double.class, odgovorId);
    }

    int poeni(Long odgovorId) {
        return db.queryForObject("select poeni from odgovori where id = ?", Integer.class, odgovorId);
    }

    List<Map<String, Object>> stanje() {
        List<Map<String, Object>> redovi = new java.util.ArrayList<>(db.queryForList(
                "select id, cast(tacno as unsigned) tacno, udeo, poeni from odgovori order by id"));
        redovi.addAll(db.queryForList("select id, staro_tacno, staro_udeo, stari_poeni from preocene_odgovora order by id"));
        return redovi;
    }
}
