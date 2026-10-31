package tri.novica.gfssystem.service.push;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.KomandaCmd;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.TipKomande;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.portal.ObjavaOcenaServis;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;
import tri.novica.gfssystem.service.raspored.RasporedSatConfig;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Push iz studentskog portala nad pravom bazom (spec aplikacije 6.3 i 6.4): objava ocena (objavljene, pa ažurirane; povlačenje
 * ne javlja ništa; bez ocene u poruci) i zbirno "posle časa" (30 min posle kraja izvođenja, čeka izvođenje koje traje, jednom
 * po grupi i danu, ništa novo = ništa, posle dana se zatvara bez slanja). Sat je današnji dan (školska godina objave ocena).
 */
@SpringBootTest
@Import({RasporedSatConfig.class, LazniMejlServis.class, LazniPushTransport.class})
class PushPortalDbTest {

    static final Duration ROK = Duration.ofSeconds(10);

    @Autowired ObjavaOcenaServis objava;
    @Autowired PosleCasaPosao posleCasa;
    @Autowired PrezentacijaService prezentacije;
    @Autowired IzvodjenjeService izvodjenja;
    @Autowired PushPretplate pretplate;
    @Autowired PodesavanjaStudentaServis podesavanja;
    @Autowired LazniPushTransport transport;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    final LocalDate danas = LocalDate.now();
    CasPodaci d;
    Long grupa;
    Long prva;
    Long druga;
    Long predavanje;

    @BeforeEach
    void setUp() {
        sat.postavi(danas.atTime(9, 0));
        transport.ocisti();
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2026);
        prva = d.student(grupa, "Prva");
        druga = d.student(grupa, "Druga");
        predavanje = d.predavanje(grupa, danas);
        d.aktivnost(predavanje, prva, "ZADATAK");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from objave_ocena where predmet_id = ?", d.predmetId);
        jdbc.update("delete from studentski_uredjaji where student_id in (?, ?)", prva, druga);
        jdbc.update("delete from pokusaji where vrsta in ('PUSH_PRETPLATA', 'PUSH_PROBA')");
        // objava ocena pravi podrazumevane koeficijente predmeta
        jdbc.update("delete from koeficijenti_tip_testa where koeficijenti_id in "
                + "(select id from koeficijenti_ocenjivanja where predmet_id = ?)", d.predmetId);
        jdbc.update("delete from koeficijenti_ocenjivanja where predmet_id = ?", d.predmetId);
        d.obrisi();
        transport.ocisti();
    }

    @Test
    void objavaOcenaJavljaObjavuIAzuriranjeBezOcene() throws Exception {
        LazniUredjaj tel = pretplati(prva);
        objava.objavi(d.predmetId, grupa);
        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        assertEquals(1, z.size());
        assertFalse(z.getFirst().hitno());
        assertEquals(Duration.ofHours(24).toSeconds(), z.getFirst().ttlSekundi());
        String predmet = "Čas " + d.oznaka;
        assertEquals("{\"naslov\":\"Ocene su objavljene\",\"tekst\":\"" + predmet + ": pogledaj predlog ocene.\","
                + "\"putanja\":\"nalog/predmet/" + d.predmetId + "\",\"oznaka\":\"ocene-" + d.predmetId + "\"}",
                tel.desifruj(z.getFirst()), "bez broja ni ocene");
        assertEquals(List.of("Ocene su objavljene"), naslovi(prva, "OCENE"));
        assertEquals(List.of("Ocene su objavljene"), naslovi(druga, "OCENE"), "zvono i bez pretplate");

        sat.postavi(danas.atTime(9, 5));
        transport.ocisti();
        objava.objavi(d.predmetId, grupa);
        z = transport.cekaj(1, ROK);
        assertEquals("Ocene su ažurirane", PushDbTest.naslov(tel.desifruj(z.getFirst())));
        assertEquals(List.of("Ocene su objavljene", "Ocene su ažurirane"), naslovi(prva, "OCENE"));

        sat.postavi(danas.atTime(9, 10));
        transport.ocisti();
        podesavanja.sacuvaj(prva, new PodesavanjaNaloga(true, null, null, false, null, null, null));
        objava.objavi(d.predmetId, grupa);
        objava.povuci(d.predmetId, grupa);
        Thread.sleep(300);
        assertTrue(transport.primljeni().isEmpty(), "isključen push za ocene; povlačenje ne javlja ništa");
        assertEquals(3, naslovi(prva, "OCENE").size());
    }

    @Test
    void posleCasaPosleKrajaIzvodjenja() throws Exception {
        LazniUredjaj tel = pretplati(prva);
        pretplati(druga);
        podesavanja.sacuvaj(druga, new PodesavanjaNaloga(true, null, null, null, false, null, null));
        Long prez = prezentacije.kreiraj(new CreatePrezentacijaCmd(d.predmetId, "Vežbe " + d.oznaka, null, null)).id();
        prezentacije.dodajSlajd(prez, new SlajdCmd(TipSlajda.INFO, "Uvod", "Tekst", null, null, false, null, null, null,
                null), null);

        sat.postavi(danas.atTime(10, 0));
        Long prvo = izvodjenja.pokreni(prez, new PokreniCmd(true, grupa, predavanje)).id();
        izvodjenja.komanda(prvo, new KomandaCmd(TipKomande.IDI_NA, 0));
        sat.postavi(danas.atTime(10, 30));
        izvodjenja.komanda(prvo, new KomandaCmd(TipKomande.ZAVRSI, null));
        assertEquals(Timestamp.valueOf(danas.atTime(11, 0)), dospeva(), "30 min posle kraja");

        sat.postavi(danas.atTime(10, 50));
        Long drugo = izvodjenja.pokreni(prez, new PokreniCmd(true, grupa, predavanje)).id();
        sat.postavi(danas.atTime(11, 0));
        assertEquals(0, posleCasa.posalji(), "izvođenje grupe traje");
        sat.postavi(danas.atTime(11, 20));
        izvodjenja.komanda(drugo, new KomandaCmd(TipKomande.ZAVRSI, null));
        assertEquals(Timestamp.valueOf(danas.atTime(11, 50)), dospeva(), "kraj kasnijeg izvođenja pomera rok");
        sat.postavi(danas.atTime(11, 30));
        assertEquals(0, posleCasa.posalji());

        sat.postavi(danas.atTime(11, 50));
        assertEquals(2, posleCasa.posalji(), "obe studentkinje grupe; zvono i uz isključen push");
        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        Thread.sleep(200);
        assertEquals(1, transport.primljeni().size(), "Druga je isključila push posle časa");
        assertEquals("{\"naslov\":\"Posle današnjeg časa\",\"tekst\":\"Prezentacija je dostupna za ponavljanje.\","
                + "\"putanja\":\"nalog/predmet/" + d.predmetId + "\",\"oznaka\":\"posle-casa-" + danas + "\"}",
                tel.desifruj(z.getFirst()));
        assertEquals(1, naslovi(druga, "POSLE_CASA").size());
        assertEquals(0, posleCasa.posalji(), "jednom po grupi i danu");
        assertNotNull(jdbc.queryForObject("select poslato from posle_casa_poslato where grupa_id = ?", Timestamp.class, grupa));
    }

    @Test
    void posleCasaBezNovogBriseRedAPosleDanaZatvara() {
        Long bezIzvodjenja = d.grupa(2026);
        Long juce = d.grupa(2026);
        sat.postavi(danas.atTime(12, 0));
        LocalDateTime rano = danas.atTime(11, 0);
        jdbc.update("insert into posle_casa_poslato (grupa_id, datum, dospeva) values (?, ?, ?)", bezIzvodjenja, danas,
                Timestamp.valueOf(rano));
        jdbc.update("insert into posle_casa_poslato (grupa_id, datum, dospeva) values (?, ?, ?)", juce, danas.minusDays(1),
                Timestamp.valueOf(rano.minusDays(1)));
        assertEquals(0, posleCasa.posalji());
        assertEquals(0, jdbc.queryForObject("select count(*) from posle_casa_poslato where grupa_id = ?", Integer.class,
                bezIzvodjenja), "ništa novo: red se briše (kasnije izvođenje istog dana zakazuje svoje)");
        assertNotNull(jdbc.queryForObject("select poslato from posle_casa_poslato where grupa_id = ?", Timestamp.class, juce),
                "prošao dan: zatvoreno bez slanja");
        assertEquals(0, jdbc.queryForObject("select count(*) from studentska_obavestenja where kljuc like 'POSLE_CASA:%' "
                + "and kljuc in (?, ?)", Integer.class, PosleCasaPosao.kljuc(bezIzvodjenja, danas),
                PosleCasaPosao.kljuc(juce, danas.minusDays(1))));
    }

    // ---------------------------------------------------------------- pomoćno

    LazniUredjaj pretplati(Long studentId) {
        String token = TokenHash.od(UUID.randomUUID().toString());
        LocalDateTime t = danas.minusDays(1).atStartOfDay();
        jdbc.update("insert into studentski_uredjaji (student_id, token_hes, opis, nacin, prvi_ulaz, poslednji_ulaz, istice, opozvan) "
                        + "values (?, ?, 'JUnit', 'KOD', ?, ?, ?, null)", studentId, token, Timestamp.valueOf(t),
                Timestamp.valueOf(t), Timestamp.valueOf(t.plusDays(365)));
        Long uredjaj = jdbc.queryForObject("select id from studentski_uredjaji where token_hes = ?", Long.class, token);
        LazniUredjaj u = new LazniUredjaj();
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(uredjaj), u.cmd());
        return u;
    }

    Timestamp dospeva() {
        return jdbc.queryForObject("select dospeva from posle_casa_poslato where grupa_id = ? and datum = ?", Timestamp.class,
                grupa, danas);
    }

    List<String> naslovi(Long studentId, String vrsta) {
        return jdbc.queryForList("select naslov from studentska_obavestenja where student_id = ? and vrsta = ? order by id",
                String.class, studentId, vrsta);
    }
}
