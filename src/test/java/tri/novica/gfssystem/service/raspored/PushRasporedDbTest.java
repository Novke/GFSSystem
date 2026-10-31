package tri.novica.gfssystem.service.raspored;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.dto.raspored.NajavaCmd;
import tri.novica.gfssystem.dto.raspored.PromenaTerminaOdgovor;
import tri.novica.gfssystem.dto.raspored.RasporedCmd;
import tri.novica.gfssystem.dto.raspored.RasporedDetails;
import tri.novica.gfssystem.dto.raspored.TerminCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;
import tri.novica.gfssystem.service.push.LazniPushTransport;
import tri.novica.gfssystem.service.push.LazniUredjaj;
import tri.novica.gfssystem.service.push.PodsetnikPosao;
import tri.novica.gfssystem.service.push.PushPretplate;
import tri.novica.gfssystem.service.push.PushTransport;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Push iz rasporeda nad pravom bazom (spec aplikacije 6.1, 6.2 i 6.6): otkazan čas i "ipak se održava" (zvono uvek, push po
 * prekidaču i samo uz "Pošalji obaveštenje"), podsetnik dan pre časa (vreme po periodu, najranije vreme studenta, preskače
 * otkazane i najavljene, nadoknada do 3 h i ne posle 22:00, jednom po danu), vreme podsetnika u periodu i push nastavniku za
 * {@code MNOGO_NAJAVA} bez imena i razloga. Datumi u 2032, da posao podsetnika ne vidi tuđe stavke.
 */
@SpringBootTest
@Import({RasporedSatConfig.class, LazniMejlServis.class, LazniPushTransport.class})
class PushRasporedDbTest {

    /** Utorak 2. 3. 2032; čas sredom 10-13. */
    static final LocalDateTime UTORAK = LocalDateTime.of(2032, 3, 2, 8, 0);
    static final LocalDate SREDA = LocalDate.of(2032, 3, 3);
    static final LocalDate OD = LocalDate.of(2032, 2, 1);
    static final LocalDate DO = LocalDate.of(2032, 6, 30);
    static final Duration ROK = Duration.ofSeconds(10);

    @Autowired RasporedService rasporedi;
    @Autowired TerminService termini;
    @Autowired NajavaService najave;
    @Autowired PodsetnikPosao podsetnik;
    @Autowired PushPretplate pretplate;
    @Autowired PodesavanjaStudentaServis podesavanja;
    @Autowired LazniPushTransport transport;
    @Autowired LazniMejlServis mejl;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    RasporedPodaci d;
    Long grupa;
    Long grupa2;
    final List<Long> sesije = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sat.postavi(UTORAK);
        transport.ocisti();
        mejl.ocisti();
        d = new RasporedPodaci(jdbc, rasporedi);
        grupa = d.cas.grupa(2030);
        grupa2 = d.cas.grupa(2030);
    }

    @AfterEach
    void tearDown() {
        for (Long s : sesije) jdbc.update("delete from nastavnicke_sesije where id = ?", s);
        jdbc.update("delete from studentski_uredjaji where student_id in (select id from studenti where grupa_id in (?, ?))",
                grupa, grupa2);
        jdbc.update("delete from pokusaji where vrsta in ('PUSH_PRETPLATA', 'PUSH_PROBA')");
        d.obrisi();
        transport.ocisti();
        mejl.ocisti();
    }

    @Test
    void otkazivanjeIVracanjeZvonoUvekPushPoPrekidacu() throws Exception {
        Long stavka = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00")).stavke().getFirst().id();
        Long ana = d.cas.student(grupa, "Ana");
        Long bora = d.cas.student(grupa, "Bora");
        Long ceda = d.cas.student(grupa, "Ceda");
        LazniUredjaj anin = pretplati(ana);
        pretplati(bora);
        podesavanja.sacuvaj(bora, new PodesavanjaNaloga(true, null, false, null, null, null, null));

        PromenaTerminaOdgovor o = termini.otkazi(new TerminCmd(stavka, SREDA, " sednica veća ", null));
        assertEquals(1, o.pushPoslato(), "Ana; Bora je isključila push za otkazan čas, Ceda nema pretplatu");
        assertEquals(0, o.obavesteno(), "niko nema adresu");
        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        Thread.sleep(200);
        assertEquals(1, transport.primljeni().size());
        assertTrue(anin.prima(z.getFirst()));
        assertTrue(z.getFirst().hitno());
        assertEquals(Duration.between(UTORAK, SREDA.atTime(10, 0)).toSeconds(), z.getFirst().ttlSekundi(),
                "TTL do početka časa");
        String predmet = "Čas " + d.cas.oznaka;
        assertEquals("{\"naslov\":\"Čas je otkazan\",\"tekst\":\"" + predmet + ", sre 3.3. u 10:00. sednica veća\","
                + "\"putanja\":\"nalog\",\"oznaka\":\"cas-" + stavka + "-" + SREDA + "\"}", anin.desifruj(z.getFirst()));
        for (Long s : List.of(ana, bora, ceda)) {
            assertEquals(List.of("OTKAZAN_CAS"), vrste(s), "zvono svima");
        }

        transport.ocisti();
        o = termini.vrati(new TerminCmd(stavka, SREDA, null, true));
        assertEquals(1, o.pushPoslato());
        z = transport.cekaj(1, ROK);
        assertEquals("{\"naslov\":\"Čas se ipak održava\",\"tekst\":\"" + predmet + ", sre 3.3. u 10:00.\","
                + "\"putanja\":\"nalog\",\"oznaka\":\"cas-" + stavka + "-" + SREDA + "\"}", anin.desifruj(z.getFirst()));
        assertEquals(List.of("OTKAZAN_CAS", "ODRZAVA_SE"), vrste(ceda));

        transport.ocisti();
        o = termini.otkazi(new TerminCmd(stavka, SREDA, null, false));
        assertEquals(0, o.pushPoslato(), "bez obaveštavanja: ni mejl ni push");
        Thread.sleep(300);
        assertTrue(transport.primljeni().isEmpty());
        assertEquals(List.of("OTKAZAN_CAS", "ODRZAVA_SE", "OTKAZAN_CAS"), vrste(ana),
                "zvono ipak dobija red (novo otkazivanje = nov ključ)");
    }

    @Test
    void podsetnikDanPreCasa() throws Exception {
        Long stavka = d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "13:00")).stavke().getFirst().id();
        Long stavka2 = d.period(OD, DO, 5, d.stavka(grupa2, 3, "12:00", "14:00")).stavke().getFirst().id();
        Long ana = d.cas.student(grupa, "Ana");
        Long bora = d.cas.student(grupa, "Bora");
        Long ceda = d.cas.student(grupa, "Ceda");
        Long dara = d.cas.student(grupa2, "Dara");
        LazniUredjaj anin = pretplati(ana);
        pretplati(bora);
        pretplati(ceda);
        pretplati(dara);
        podesavanja.sacuvaj(ceda, new PodesavanjaNaloga(true, false, null, null, null, null, null));
        najave.najavi(bora, new NajavaCmd(stavka, SREDA, "lekar"));
        termini.otkazi(new TerminCmd(stavka2, SREDA, null, false));

        sat.postavi(UTORAK.withHour(17).withMinute(55));
        assertEquals(0, podsetnik.posalji(), "vreme još nije stiglo");
        sat.postavi(UTORAK.withHour(18).withMinute(0));
        assertEquals(2, podsetnik.posalji(), "Ana i Ceda (Bora ne dolazi, Darin čas je otkazan)");
        assertEquals(1, podsetnici(ana));
        assertEquals(1, podsetnici(ceda), "red zvona i kad je push isključen");
        assertEquals(0, podsetnici(bora));
        assertEquals(0, podsetnici(dara));

        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        Thread.sleep(200);
        assertEquals(1, transport.primljeni().size(), "samo Ana: Ceda je isključila push podsetnika");
        assertTrue(anin.prima(z.getFirst()));
        assertFalse(z.getFirst().hitno());
        assertEquals(Duration.ofHours(16).toSeconds(), z.getFirst().ttlSekundi());
        assertEquals("{\"naslov\":\"Sutra imaš čas\",\"tekst\":\"10:00 Čas " + d.cas.oznaka + ". Vidimo se!\","
                + "\"putanja\":\"nalog/cas\",\"oznaka\":\"podsetnik-" + SREDA + "\"}", anin.desifruj(z.getFirst()));
        assertEquals(0, podsetnik.posalji(), "jednom po danu");
        assertTrue(podesavanja.za(ana).pushPodsetnik());

        sat.postavi(UTORAK.withHour(20).withMinute(30));
        Long eva = d.cas.student(grupa, "Eva");
        assertEquals(1, podsetnik.posalji(), "nadoknada do 3 h posle vremena");
        assertEquals(1, podsetnici(eva));
        sat.postavi(UTORAK.withHour(21).withMinute(1));
        Long fil = d.cas.student(grupa, "Fil");
        assertEquals(0, podsetnik.posalji(), "posle 3 h ništa");
        assertEquals(0, podsetnici(fil));
    }

    @Test
    void podsetnikNajranijeVremeISviSutrasnjiCasovi() throws Exception {
        d.period(OD, DO, 5, d.stavka(grupa, 3, "10:00", "12:00"));
        d.zapamti(rasporedi.kreiraj(new RasporedCmd("Drugi " + d.cas.oznaka, OD, DO, 5,
                List.of(d.stavka(grupa, 3, "14:00", "16:00")), true, LocalTime.of(17, 0))));
        d.zapamti(rasporedi.kreiraj(new RasporedCmd("Bez " + d.cas.oznaka, OD, DO, 5,
                List.of(d.stavka(grupa, 3, "16:30", "18:00")), false, null)));
        Long ana = d.cas.student(grupa, "Ana");
        LazniUredjaj anin = pretplati(ana);

        sat.postavi(UTORAK.withHour(16).withMinute(59));
        assertEquals(0, podsetnik.posalji());
        sat.postavi(UTORAK.withHour(17).withMinute(0));
        assertEquals(1, podsetnik.posalji(), "najranije vreme podsetnika studenta");
        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        String p = "Čas " + d.cas.oznaka;
        assertEquals("{\"naslov\":\"Sutra imaš časove\",\"tekst\":\"10:00 " + p + " · 14:00 " + p + ". Vidimo se!\","
                        + "\"putanja\":\"nalog/cas\",\"oznaka\":\"podsetnik-" + SREDA + "\"}", anin.desifruj(z.getFirst()),
                "period sa isključenim podsetnikom se ne navodi");
        assertEquals(Duration.ofHours(17).toSeconds(), z.getFirst().ttlSekundi(), "TTL do prvog časa");
        sat.postavi(UTORAK.withHour(18).withMinute(0));
        assertEquals(0, podsetnik.posalji());
    }

    @Test
    void vremePodsetnikaUPeriodu() {
        RasporedDetails r = d.zapamti(rasporedi.kreiraj(new RasporedCmd("P " + d.cas.oznaka, OD, DO, null, List.of())));
        assertEquals(LocalTime.of(18, 0), r.podsetnikVreme(), "stariji front: podrazumevano 18:00");
        assertEquals(LocalTime.of(18, 0), rasporedi.lista().stream().filter(x -> x.id().equals(r.id())).findFirst()
                .orElseThrow().podsetnikVreme());
        Long id = r.id();
        String naziv = r.naziv();
        assertNull(rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of(), false, LocalTime.of(9, 0)))
                .podsetnikVreme(), "isključen");
        assertNull(rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of())).podsetnikVreme(),
                "bez izbora: ostaje isključen");
        assertNull(rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of(), null, LocalTime.of(9, 0)))
                .podsetnikVreme(), "samo vreme ne uključuje");
        assertEquals(LocalTime.of(7, 30), rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of(), true,
                LocalTime.of(7, 30, 45))).podsetnikVreme(), "na minut");
        assertEquals(LocalTime.of(7, 30), rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of()))
                .podsetnikVreme());
        assertEquals(LocalTime.of(21, 30), rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of(), null,
                LocalTime.of(21, 30))).podsetnikVreme());
        for (LocalTime t : List.of(LocalTime.of(5, 59), LocalTime.of(21, 31), LocalTime.of(23, 0))) {
            SystemException e = assertThrows(SystemException.class,
                    () -> rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of(), true, t)));
            assertEquals(400, e.getCode());
            assertEquals("Vreme podsetnika mora biti od 06:00 do 21:30.", e.getMessage());
        }
        assertEquals(LocalTime.of(21, 30), rasporedi.izmeni(id, new RasporedCmd(naziv, OD, DO, null, List.of(), true, null))
                .podsetnikVreme(), "uključen bez vremena: zadržava 21:30");
    }

    @Test
    void mnogoNajavaPushNastavnikuBezImenaIRazloga() throws Exception {
        Long stavka = d.period(OD, DO, 1, d.stavka(grupa, 3, "10:00", "13:00")).stavke().getFirst().id();
        Long ana = d.cas.student(grupa, "Ana");
        Long bora = d.cas.student(grupa, "Bora");
        long sesija = sesija();
        LazniUredjaj tel = new LazniUredjaj("https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID());
        pretplate.pretplati(PushPretplate.Vlasnik.sesija(sesija), tel.cmd());

        najave.najavi(ana, new NajavaCmd(stavka, SREDA, "lekar"));
        Thread.sleep(200);
        assertTrue(transport.primljeni().isEmpty(), "prag 1 još nije pređen");
        najave.najavi(bora, new NajavaCmd(stavka, SREDA, "Bora ima ispit"));
        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        assertEquals(1, z.size());
        String json = tel.desifruj(z.getFirst());
        assertTrue(z.getFirst().hitno());
        assertTrue(json.startsWith("{\"naslov\":\"2 studenta neće doći\",\"tekst\":\"Čas " + d.cas.oznaka + ", "), json);
        assertTrue(json.contains(", sre 3.3. u 10:00 (prag 1).\""), json);
        assertTrue(json.endsWith("\"putanja\":\"raspored/termini?od=" + SREDA + "\",\"oznaka\":\"mnogo-najava-" + stavka
                + "-" + SREDA + "\"}"), json);
        for (String zabranjeno : List.of("Ana", "Bora", "lekar", "ispit")) {
            assertFalse(json.contains(zabranjeno), zabranjeno);
        }
    }

    // ---------------------------------------------------------------- pomoćno

    LazniUredjaj pretplati(Long studentId) {
        String token = TokenHash.od(UUID.randomUUID().toString());
        LocalDateTime t = UTORAK.minusDays(1);
        jdbc.update("insert into studentski_uredjaji (student_id, token_hes, opis, nacin, prvi_ulaz, poslednji_ulaz, istice, opozvan) "
                + "values (?, ?, 'JUnit', 'KOD', ?, ?, ?, null)", studentId, token, Timestamp.valueOf(t), Timestamp.valueOf(t),
                Timestamp.valueOf(t.plusDays(365)));
        Long uredjaj = jdbc.queryForObject("select id from studentski_uredjaji where token_hes = ?", Long.class, token);
        LazniUredjaj u = new LazniUredjaj();
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(uredjaj), u.cmd());
        return u;
    }

    long sesija() {
        String token = TokenHash.od(UUID.randomUUID().toString());
        jdbc.update("insert into nastavnicke_sesije (token_hes, opis, ip, kreirano, poslednji_ulaz, istice) "
                        + "values (?, 'JUnit', 'x', ?, ?, ?)", token, Timestamp.valueOf(UTORAK), Timestamp.valueOf(UTORAK),
                Timestamp.valueOf(UTORAK.plusDays(90)));
        Long id = jdbc.queryForObject("select id from nastavnicke_sesije where token_hes = ?", Long.class, token);
        sesije.add(id);
        return id;
    }

    List<String> vrste(Long studentId) {
        return jdbc.queryForList("select vrsta from studentska_obavestenja where student_id = ? order by id", String.class,
                studentId);
    }

    int podsetnici(Long studentId) {
        return jdbc.queryForObject("select count(*) from studentska_obavestenja where student_id = ? and vrsta = 'PODSETNIK'",
                Integer.class, studentId);
    }
}
