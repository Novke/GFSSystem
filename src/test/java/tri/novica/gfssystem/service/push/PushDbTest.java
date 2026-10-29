package tri.novica.gfssystem.service.push;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.dto.push.PushPretplataCmd;
import tri.novica.gfssystem.dto.push.PushVrstaCmd;
import tri.novica.gfssystem.dto.push.PushVrstaInfo;
import tri.novica.gfssystem.dto.push.ZvonoInfo;
import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.mejl.LazniMejlServis;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;
import tri.novica.gfssystem.service.prijava.NastavnikSesijaServis;
import tri.novica.gfssystem.service.prijava.NovoObavestenje;
import tri.novica.gfssystem.service.prijava.StudentNalogServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.service.raspored.RasporedSatConfig;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Push pretplate, slanje, zvono studenta i nastavnička podešavanja nad pravom bazom (spec aplikacije 5, 6.6, 6.7 i 8).
 * Transport je {@link LazniPushTransport}: poruke se šifruju i potpisuju kao u produkciji, test ih dešifruje ključem
 * "browsera" ({@link LazniUredjaj}). Sat je pomeren u 2031, da se pokušaji i pretplate ne sudare sa drugim testovima. Bez
 * {@code @Transactional}; test čisti za sobom (nastavničke sesije i podešavanja nastavnika su globalni).
 */
@SpringBootTest
@Import({RasporedSatConfig.class, LazniMejlServis.class, LazniPushTransport.class})
class PushDbTest {

    static final LocalDateTime SADA = LocalDateTime.of(2031, 3, 4, 12, 0);
    static final Duration ROK = Duration.ofSeconds(10);

    @Autowired PushPretplate pretplate;
    @Autowired PushPosiljalac posiljalac;
    @Autowired PushNastavniku nastavnik;
    @Autowired StudentskaObavestenja obavestenja;
    @Autowired PodesavanjaStudentaServis podesavanja;
    @Autowired StudentskaPrijava prijava;
    @Autowired StudentNalogServis nalog;
    @Autowired NastavnikSesijaServis sesije;
    @Autowired ApplicationEventPublisher dogadjaji;
    @Autowired PlatformTransactionManager transakcije;
    @Autowired LazniPushTransport transport;
    @Autowired RasporedSatConfig.Sat sat;
    @Autowired JdbcTemplate jdbc;

    CasPodaci d;
    Long grupa;
    final List<Long> sesijeTesta = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sat.postavi(SADA);
        transport.ocisti();
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2030);
    }

    @AfterEach
    void tearDown() {
        for (Long s : sesijeTesta) {
            jdbc.update("delete from nastavnicke_sesije where id = ?", s);
        }
        jdbc.update("delete from podesavanja_nastavnika_push");
        jdbc.update("delete from pokusaji where vrsta in ('PUSH_PRETPLATA', 'PUSH_PROBA')");
        jdbc.update("delete from studentski_uredjaji where student_id in (select id from studenti where grupa_id = ?)", grupa);
        d.obrisi();
        transport.ocisti();
    }

    // ---------------------------------------------------------------- pretplata

    @Test
    void pretplataSeUpisujeZamenjujeIPrelaziNaDrugiUredjaj() {
        Long s = d.student(grupa, "Ana");
        long u1 = uredjaj(s);
        long u2 = uredjaj(s);
        var v1 = PushPretplate.Vlasnik.uredjaj(u1);
        var v2 = PushPretplate.Vlasnik.uredjaj(u2);

        var stanje = pretplate.stanje(v1);
        assertFalse(stanje.pretplacen());
        assertEquals(65, Base64Url.dekodiraj(stanje.javniKljuc()).length, "nekompresovana P-256 tačka");

        LazniUredjaj a = new LazniUredjaj();
        pretplate.pretplati(v1, a.cmd());
        assertTrue(pretplate.stanje(v1).pretplacen());
        assertFalse(pretplate.stanje(v2).pretplacen());

        LazniUredjaj b = new LazniUredjaj();
        pretplate.pretplati(v1, b.cmd());   // isti uređaj, nova pretplata browsera: zamena na mestu
        assertEquals(1, broj("studentski_uredjaj_id", u1));
        assertEquals(b.endpoint, jdbc.queryForObject("select endpoint from push_pretplate where studentski_uredjaj_id = ?",
                String.class, u1));

        pretplate.pretplati(v2, b.cmd());   // isti endpoint na drugom uređaju: prelazi, stari red se briše
        assertEquals(0, broj("studentski_uredjaj_id", u1));
        assertEquals(1, broj("studentski_uredjaj_id", u2));

        pretplate.odjavi(v2);
        assertFalse(pretplate.stanje(v2).pretplacen());
        pretplate.odjavi(v2);   // i bez pretplate: bez greške
    }

    @Test
    void neispravnePretplateSu400() {
        long u = uredjaj(d.student(grupa, "Ana"));
        var v = PushPretplate.Vlasnik.uredjaj(u);
        LazniUredjaj a = new LazniUredjaj();
        String k = a.cmd().p256dh();
        String auth = a.cmd().auth();
        for (String endpoint : List.of("https://evil.example.com/push/1", "https://fcm.googleapis.com.evil.com/x",
                "https://fcm.googleapis.com:8443/fcm/send/x", "http://fcm.googleapis.com/fcm/send/x",
                "https://127.0.0.1/x")) {
            SystemException e = assertThrows(SystemException.class,
                    () -> pretplate.pretplati(v, new PushPretplataCmd(endpoint, k, auth)), endpoint);
            assertEquals(400, e.getCode(), endpoint);
            assertEquals(PushPretplate.NIJE_PODRZAN, e.getMessage(), endpoint);
        }
        for (PushPretplataCmd cmd : List.of(new PushPretplataCmd(null, k, auth),
                new PushPretplataCmd("ne je adresa", k, auth),
                new PushPretplataCmd(a.endpoint, "AAAA", auth),
                new PushPretplataCmd(a.endpoint, k, "kratko"),
                new PushPretplataCmd(a.endpoint, k, null))) {
            SystemException e = assertThrows(SystemException.class, () -> pretplate.pretplati(v, cmd));
            assertEquals(400, e.getCode());
            assertEquals(PushPretplate.NEISPRAVNA, e.getMessage());
        }
        assertEquals(0, broj("studentski_uredjaj_id", u));
    }

    @Test
    void limitUpisaIProbe() {
        long u = uredjaj(d.student(grupa, "Ana"));
        var v = PushPretplate.Vlasnik.uredjaj(u);
        LazniUredjaj a = new LazniUredjaj();
        PushPretplataCmd los = new PushPretplataCmd("https://evil.example.com/x", a.cmd().p256dh(), a.cmd().auth());
        for (int i = 0; i < PushPretplate.LIMIT_UPISA; i++) {
            assertEquals(400, status(() -> pretplate.pretplati(v, los)), "i neispravan pokušaj se broji");
        }
        assertEquals(429, status(() -> pretplate.pretplati(v, a.cmd())));
        sat.postavi(SADA.plusMinutes(61));
        pretplate.pretplati(v, a.cmd());

        // proba: 409 bez pretplate (i to se broji), pa 429 posle pet pokušaja
        long u2 = uredjaj(d.student(grupa, "Bora"));
        var v2 = PushPretplate.Vlasnik.uredjaj(u2);
        assertEquals(409, status(() -> pretplate.proba(v2)));
        for (int i = 1; i < PushPretplate.LIMIT_PROBE; i++) {
            assertEquals(409, status(() -> pretplate.proba(v2)));
        }
        assertEquals(429, status(() -> pretplate.proba(v2)));
    }

    @Test
    void probaStizeSifrovanaSaVapidZaglavljem() throws Exception {
        long u = uredjaj(d.student(grupa, "Ana"));
        var v = PushPretplate.Vlasnik.uredjaj(u);
        assertEquals(409, status(() -> pretplate.proba(v)));
        LazniUredjaj a = new LazniUredjaj();
        pretplate.pretplati(v, a.cmd());
        pretplate.proba(v);

        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        assertEquals(1, z.size());
        PushTransport.Zahtev zahtev = z.getFirst();
        assertTrue(a.prima(zahtev));
        assertEquals(3600, zahtev.ttlSekundi());
        assertFalse(zahtev.hitno());
        assertTrue(zahtev.autorizacija().startsWith("vapid t="), "RFC 8292");
        assertTrue(zahtev.autorizacija().endsWith(", k=" + pretplate.stanje(v).javniKljuc()));
        assertEquals("{\"naslov\":\"GFS radi\",\"tekst\":\"Obaveštenja na ovom uređaju su uključena.\","
                + "\"putanja\":\"nalog\",\"oznaka\":\"proba\"}", a.desifruj(zahtev));
        dok(() -> jdbc.queryForObject("select poslednji_uspeh is not null from push_pretplate where studentski_uredjaj_id = ?",
                Boolean.class, u), "2xx upisuje poslednji uspeh");
    }

    @Test
    void ishodSlanja404410IPetGresakaBrisu() throws Exception {
        Long s = d.student(grupa, "Ana");
        long u = uredjaj(s);
        LazniUredjaj a = new LazniUredjaj();
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(u), a.cmd());
        PushPoruka p = new PushPoruka("Naslov", "Tekst", "nalog", "x");

        transport.status(z -> 503);
        for (int i = 1; i < PushPosiljalac.MAX_GRESAKA; i++) {
            int n = i;
            assertEquals(1, posiljalac.studentima(Map.of(s, p), Duration.ofHours(1), false));
            dok(() -> greske(u) == n, "greška " + n);
        }
        transport.status(z -> 201);
        assertEquals(1, posiljalac.studentima(Map.of(s, p), Duration.ofHours(1), false));
        dok(() -> greske(u) == 0, "uspeh nuluje uzastopne greške");

        transport.status(z -> 500);
        for (int i = 0; i < PushPosiljalac.MAX_GRESAKA; i++) {
            assertEquals(1, posiljalac.studentima(Map.of(s, p), Duration.ofHours(1), false));
            int n = i + 1;
            dok(() -> n == PushPosiljalac.MAX_GRESAKA ? broj("studentski_uredjaj_id", u) == 0 : greske(u) == n,
                    "greška " + n);
        }
        assertEquals(0, broj("studentski_uredjaj_id", u), "posle pet uzastopnih grešaka pretplata se briše");

        for (int kod : new int[]{404, 410}) {
            pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(u), new LazniUredjaj().cmd());
            transport.status(z -> kod);
            posiljalac.studentima(Map.of(s, p), Duration.ofHours(1), false);
            dok(() -> broj("studentski_uredjaj_id", u) == 0, kod + " briše odmah");
        }
    }

    @Test
    void odjavaOpozivIRaskidBrisuPretplatu() {
        Long s = d.student(grupa, "Ana");
        long u1 = uredjaj(s);
        long u2 = uredjaj(s);
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(u1), new LazniUredjaj().cmd());
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(u2), new LazniUredjaj().cmd());

        prijava.odjaviUredjaj(u1);
        assertEquals(0, broj("studentski_uredjaj_id", u1));
        assertEquals(1, broj("studentski_uredjaj_id", u2));
        prijava.odjaviSve(s);
        assertEquals(0, broj("studentski_uredjaj_id", u2), "odjava svuda");

        long u3 = uredjaj(s);
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(u3), new LazniUredjaj().cmd());
        nalog.opozoviUredjaje(s);
        assertEquals(0, broj("studentski_uredjaj_id", u3), "nastavnik opoziva uređaje");

        // opozvan uređaj čiji red pretplate je ostao (npr. upis u istom trenutku): slanje ga preskače
        long u4 = uredjaj(s);
        pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(u4), new LazniUredjaj().cmd());
        jdbc.update("update studentski_uredjaji set opozvan = ? where id = ?", Timestamp.valueOf(SADA), u4);
        assertEquals(0, posiljalac.studentima(Map.of(s, new PushPoruka("a", "b", "nalog", "c")), Duration.ofHours(1), false));
        assertEquals(401, status(() -> pretplate.pretplati(PushPretplate.Vlasnik.uredjaj(-1L), new LazniUredjaj().cmd())));

        long sesija = sesija();
        pretplate.pretplati(PushPretplate.Vlasnik.sesija(sesija), new LazniUredjaj().cmd());
        assertEquals(1, broj("nastavnicka_sesija_id", sesija));
        sesije.opozovi(sesija);
        assertEquals(0, broj("nastavnicka_sesija_id", sesija), "odjava nastavnika");
    }

    // ---------------------------------------------------------------- zvono i podešavanja

    @Test
    void zvonoSkrivaPodsetnikPratiPrekidaceIOtvaranje() {
        Long s = d.student(grupa, "Ana");
        ZvonoInfo prazno = obavestenja.zvono(s);
        assertFalse(prazno.novo());
        assertTrue(prazno.stavke().isEmpty());

        upisi(s, VrstaStudentskogObavestenja.PODSETNIK, "PODSETNIK:2031-03-05", "Sutra imaš čas");
        sat.postavi(SADA.plusMinutes(1));
        upisi(s, VrstaStudentskogObavestenja.OTKAZAN_CAS, "OTKAZAN:1:2031-03-05:1", "Čas je otkazan");
        sat.postavi(SADA.plusMinutes(2));
        upisi(s, VrstaStudentskogObavestenja.OCENE, "OCENE:1:1:x", "Ocene su objavljene");
        assertFalse(upisiVrati(s, VrstaStudentskogObavestenja.OCENE, "OCENE:1:1:x", "Duplo"), "ključ je jedinstven");

        ZvonoInfo z = obavestenja.zvono(s);
        assertTrue(z.novo());
        assertEquals(List.of("Ocene su objavljene", "Čas je otkazan"), z.stavke().stream().map(x -> x.naslov()).toList(),
                "najnovije prvo, podsetnik nikad u zvonu");

        podesavanja.sacuvaj(s, new PodesavanjaNaloga(true, null, null, null, null, false, null));
        assertEquals(List.of("Čas je otkazan"), obavestenja.zvono(s).stavke().stream().map(x -> x.naslov()).toList(),
                "isključeno zvono za ocene");

        sat.postavi(SADA.plusMinutes(3));
        obavestenja.otvoreno(s);
        assertFalse(obavestenja.zvono(s).novo());
        sat.postavi(SADA.plusMinutes(4));
        upisi(s, VrstaStudentskogObavestenja.ODRZAVA_SE, "ODRZAVA_SE:1:2031-03-05:1", "Čas se ipak održava");
        assertTrue(obavestenja.zvono(s).novo());

        for (int i = 0; i < 35; i++) {
            upisi(s, VrstaStudentskogObavestenja.OTKAZAN_CAS, "OTKAZAN:9:" + i, "N" + i);
        }
        assertEquals(StudentskaObavestenja.ZVONO_NAJVISE, obavestenja.zvono(s).stavke().size());
    }

    @Test
    void podesavanjaNalogaNullZnaciBezIzmene() {
        Long s = d.student(grupa, "Ana");
        PodesavanjaNaloga p = podesavanja.za(s);
        assertEquals(new PodesavanjaNaloga(true, true, true, true, true, true, true), p, "bez reda: sve uključeno");

        p = podesavanja.sacuvaj(s, new PodesavanjaNaloga(true, false, null, null, true, null, false));
        assertEquals(new PodesavanjaNaloga(true, false, true, true, true, true, false), p);
        p = podesavanja.sacuvaj(s, new PodesavanjaNaloga(false));   // stari klijent: samo mejl
        assertEquals(new PodesavanjaNaloga(false, false, true, true, true, true, false), p);
        p = podesavanja.sacuvaj(s, new PodesavanjaNaloga(false, null, false, false, false, true, null));
        assertEquals(new PodesavanjaNaloga(false, false, false, false, false, true, false), podesavanja.za(s));
        assertEquals(p, podesavanja.za(s));

        Long t = d.student(grupa, "Bora");
        podesavanja.sacuvaj(t, new PodesavanjaNaloga(true, null, null, true, null, null, null));   // nov red: null = podrazumevano
        assertEquals(new PodesavanjaNaloga(true, true, true, true, true, true, true), podesavanja.za(t));
    }

    // ---------------------------------------------------------------- nastavnik

    @Test
    void nastavnikPodesavanjaIPushPoVrsti() throws Exception {
        assertEquals(List.of("NE_STIZE_KOD", "PREVISE_KODOVA", "MNOGO_NAJAVA"),
                nastavnik.podesavanja().vrste().stream().map(PushVrstaInfo::vrsta).toList());
        assertTrue(nastavnik.podesavanja().vrste().stream().allMatch(PushVrstaInfo::ukljuceno));
        assertEquals(400, status(() -> nastavnik.izmeni(new PushVrstaCmd("NEPOSTOJI", true))));
        assertEquals(400, status(() -> nastavnik.izmeni(new PushVrstaCmd(null, true))));
        assertEquals(400, status(() -> nastavnik.izmeni(new PushVrstaCmd("NE_STIZE_KOD", null))));

        long sesija = sesija();
        LazniUredjaj tel = new LazniUredjaj("https://web.push.apple.com/" + UUID.randomUUID());
        pretplate.pretplati(PushPretplate.Vlasnik.sesija(sesija), tel.cmd());

        objavi(new NovoObavestenje(VrstaObavestenja.NE_STIZE_KOD, null, 0, 0));
        List<PushTransport.Zahtev> z = transport.cekaj(1, ROK);
        assertEquals(1, z.size());
        assertTrue(z.getFirst().hitno());
        assertEquals(Duration.ofHours(24).toSeconds(), z.getFirst().ttlSekundi());
        assertEquals("{\"naslov\":\"Studentu ne stiže kod\",\"tekst\":\"Otvori obaveštenja.\","
                + "\"putanja\":\"?obavestenja=otvori\",\"oznaka\":\"ne-stize-kod\"}", tel.desifruj(z.getFirst()));

        var posle = nastavnik.izmeni(new PushVrstaCmd("NE_STIZE_KOD", false));
        assertFalse(posle.vrste().getFirst().ukljuceno());
        assertTrue(posle.vrste().get(1).ukljuceno());
        transport.ocisti();
        objavi(new NovoObavestenje(VrstaObavestenja.NE_STIZE_KOD, null, 0, 0));
        objavi(new NovoObavestenje(VrstaObavestenja.PREVISE_KODOVA, null, 0, 0));
        z = transport.cekaj(1, ROK);
        Thread.sleep(300);
        assertEquals(1, transport.primljeni().size(), "isključena vrsta se ne šalje");
        assertEquals("Previše zahteva za kod", naslov(tel.desifruj(z.getFirst())));
    }

    // ---------------------------------------------------------------- pomoćno

    long uredjaj(Long studentId) {
        String token = TokenHash.od(UUID.randomUUID().toString());
        jdbc.update("insert into studentski_uredjaji (student_id, token_hes, opis, nacin, prvi_ulaz, poslednji_ulaz, istice, opozvan) "
                        + "values (?, ?, 'JUnit', 'KOD', ?, ?, ?, null)", studentId, token, Timestamp.valueOf(SADA),
                Timestamp.valueOf(SADA), Timestamp.valueOf(SADA.plusDays(365)));
        return jdbc.queryForObject("select id from studentski_uredjaji where token_hes = ?", Long.class, token);
    }

    long sesija() {
        String token = TokenHash.od(UUID.randomUUID().toString());
        jdbc.update("insert into nastavnicke_sesije (token_hes, opis, ip, kreirano, poslednji_ulaz, istice) "
                        + "values (?, 'JUnit', 'x', ?, ?, ?)", token, Timestamp.valueOf(SADA), Timestamp.valueOf(SADA),
                Timestamp.valueOf(SADA.plusDays(90)));
        Long id = jdbc.queryForObject("select id from nastavnicke_sesije where token_hes = ?", Long.class, token);
        sesijeTesta.add(id);
        return id;
    }

    int broj(String kolona, long id) {
        return jdbc.queryForObject("select count(*) from push_pretplate where " + kolona + " = ?", Integer.class, id);
    }

    int greske(long uredjajId) {
        List<Integer> g = jdbc.queryForList("select uzastopne_greske from push_pretplate where studentski_uredjaj_id = ?",
                Integer.class, uredjajId);
        return g.isEmpty() ? -1 : g.getFirst();
    }

    void upisi(Long studentId, VrstaStudentskogObavestenja vrsta, String kljuc, String naslov) {
        assertTrue(upisiVrati(studentId, vrsta, kljuc, naslov));
    }

    boolean upisiVrati(Long studentId, VrstaStudentskogObavestenja vrsta, String kljuc, String naslov) {
        return Boolean.TRUE.equals(new TransactionTemplate(transakcije).execute(s ->
                obavestenja.upisi(studentId, vrsta, kljuc, new PushPoruka(naslov, "Tekst", "nalog", "o"))));
    }

    void objavi(NovoObavestenje o) {
        new TransactionTemplate(transakcije).executeWithoutResult(s -> dogadjaji.publishEvent(o));
    }

    static String naslov(String json) {
        int i = json.indexOf("\"naslov\":\"") + 10;
        return json.substring(i, json.indexOf('"', i));
    }

    static int status(Runnable poziv) {
        return assertThrows(SystemException.class, poziv::run).getCode();
    }

    static void dok(BooleanSupplier uslov, String opis) {
        long kraj = System.nanoTime() + ROK.toNanos();
        while (!uslov.getAsBoolean()) {
            if (System.nanoTime() > kraj) fail("Nije dočekano: " + opis);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
        }
    }
}
