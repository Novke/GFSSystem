package tri.novica.gfssystem.service.cas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tri.novica.gfssystem.dto.cas.CasStanje;
import tri.novica.gfssystem.dto.cas.CekiranStudent;
import tri.novica.gfssystem.dto.cas.CekiranjeInfo;
import tri.novica.gfssystem.dto.cas.SkeniranjeOdgovor;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.PokreniCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.PredavanjeService;
import tri.novica.gfssystem.service.uzivo.IzvodjenjeService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;
import tri.novica.gfssystem.service.uzivo.UcesnikService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Čekiranje nad pravom MySQL bazom (kao CI), bez {@code @Transactional}: skeniranje prijavljenog i neprijavljenog studenta,
 * ključ čekanja, istek tokena, tajne i "na času", grupa, ponavljanje i paralelni zahtevi, stanje časa i nastavnički pogled.
 * Vreme je pravi sat; prošlost se pravi JDBC-om (vremena iz Jave) ili tokenom izračunatim za ranije vreme.
 */
@SpringBootTest
class CekiranjeServiceDbTest {

    static final String UREDJAJ_A = "a".repeat(64);
    static final String UREDJAJ_B = "b".repeat(64);

    @Autowired CekiranjeService cekiranje;
    @Autowired PredavanjeService predavanjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired UcesnikService ucesnikService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired tri.novica.gfssystem.service.prijava.CiscenjePrijave ciscenje;

    CasPodaci d;
    Long grupa;
    Long predavanje;
    Long ana;
    Long bora;

    @BeforeEach
    void setUp() {
        d = new CasPodaci(jdbc);
        grupa = d.grupa(2026);
        predavanje = d.predavanje(grupa, LocalDate.now(clock));
        ana = d.student(grupa, "Ana");
        bora = d.student(grupa, "Bora");
    }

    @AfterEach
    void tearDown() {
        d.obrisi();
    }

    /** Token tekućeg prozora (tajnu pravi nastavnički pogled). */
    String token(Long predavanjeId) {
        return cekiranje.zaNastavnika(predavanjeId).token();
    }

    static void ocekuj(int kod, String poruka, Executable e) {
        SystemException ex = assertThrows(SystemException.class, e::run);
        assertEquals(kod, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    interface Executable {
        void run();
    }

    // ---------------------------------------------------------------- skeniranje i ključ

    @Test
    void prijavljenSeCekiraIDobijaPrisustvo() {
        SkeniranjeOdgovor o = cekiranje.skeniraj(token(predavanje), Optional.of(ana), UREDJAJ_A);
        assertNull(o.kljuc());
        assertEquals(predavanje, o.stanje().predavanje().id());
        assertEquals("Čas " + d.oznaka, o.stanje().predavanje().predmet());
        assertFalse(o.stanje().predavanje().zavrseno());
        assertNotNull(o.stanje().cekiranU());
        assertNull(o.stanje().izvodjenje());
        assertEquals(1, d.brojCekiranja(predavanje));
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ana));
        assertEquals(1, d.posecenost(predavanje));
    }

    @Test
    void neprijavljenDobijaKljucPaPotvrdom() {
        SkeniranjeOdgovor o = cekiranje.skeniraj(token(predavanje), Optional.empty(), UREDJAJ_A);
        assertNull(o.stanje());
        assertTrue(o.kljuc().matches("^[A-Za-z0-9]{32}$"));
        assertEquals(0, d.brojCekiranja(predavanje));
        LocalDateTime skenirano = jdbc.queryForObject(
                "select skenirano from cekiranja_na_cekanju where predavanje_id = ?", LocalDateTime.class, predavanje);
        // u bazi samo heš ključa
        assertEquals(0, (int) jdbc.queryForObject("select count(*) from cekiranja_na_cekanju where kljuc_hash = ?",
                Integer.class, o.kljuc()));

        CasStanje s = cekiranje.potvrdi(o.kljuc(), ana, UREDJAJ_A);
        assertEquals(predavanje, s.predavanje().id());
        assertEquals(skenirano, s.cekiranU(), "vreme čekiranja je trenutak skeniranja, ne potvrde");
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ana));
    }

    @Test
    void kljucJednokratan() {
        String kljuc = cekiranje.skeniraj(token(predavanje), Optional.empty(), UREDJAJ_A).kljuc();
        cekiranje.potvrdi(kljuc, ana, UREDJAJ_A);
        ocekuj(410, CekiranjeService.KLJUC_ISTEKAO, () -> cekiranje.potvrdi(kljuc, bora, UREDJAJ_A));
        ocekuj(410, CekiranjeService.KLJUC_ISTEKAO, () -> cekiranje.potvrdi("nepostojeci", ana, null));
        ocekuj(410, CekiranjeService.KLJUC_ISTEKAO, () -> cekiranje.potvrdi("x".repeat(32), ana, null));
        assertTrue(d.aktivnosti(predavanje, bora).isEmpty());
    }

    @Test
    void kljucVaziSamoSaTelefonaKojiJeSkenirao() {
        // bezbednosni pregled: ključ poslat u grupu ne čekira nikoga na drugom telefonu
        String kljuc = cekiranje.skeniraj(token(predavanje), Optional.empty(), UREDJAJ_A).kljuc();
        ocekuj(410, CekiranjeService.KLJUC_ISTEKAO, () -> cekiranje.potvrdi(kljuc, bora, UREDJAJ_B));
        ocekuj(410, CekiranjeService.KLJUC_ISTEKAO, () -> cekiranje.potvrdi(kljuc, bora, null));
        assertEquals(0, d.brojCekiranja(predavanje));
        // odbijen pokušaj ne troši ključ: telefon koji je skenirao ga i dalje potvrđuje
        assertEquals(predavanje, cekiranje.potvrdi(kljuc, ana, UREDJAJ_A).predavanje().id());
        assertEquals(UREDJAJ_A, jdbc.queryForObject(
                "select uredjaj_hash from cekiranja where predavanje_id = ? and student_id = ?", String.class, predavanje, ana));
    }

    @Test
    void novoSkeniranjeIstimTelefonomPonistavaKljuc() {
        String t = token(predavanje);
        String prvi = cekiranje.skeniraj(t, Optional.empty(), UREDJAJ_A).kljuc();
        String drugogTelefona = cekiranje.skeniraj(t, Optional.empty(), UREDJAJ_B).kljuc();
        String drugi = cekiranje.skeniraj(t, Optional.empty(), UREDJAJ_A).kljuc();
        assertEquals(2, (int) jdbc.queryForObject(
                "select count(*) from cekiranja_na_cekanju where predavanje_id = ?", Integer.class, predavanje));
        ocekuj(410, CekiranjeService.KLJUC_ISTEKAO, () -> cekiranje.potvrdi(prvi, ana, UREDJAJ_A));
        assertNotNull(cekiranje.potvrdi(drugi, ana, UREDJAJ_A).predavanje());
        assertNotNull(cekiranje.potvrdi(drugogTelefona, bora, UREDJAJ_B).predavanje());
        // iskorišćen ključ ostaje (čišćenje ga briše posle sat vremena), novo skeniranje ga ne dira
        cekiranje.skeniraj(t, Optional.empty(), UREDJAJ_A);
        assertEquals(1, (int) jdbc.queryForObject(
                "select count(*) from cekiranja_na_cekanju where predavanje_id = ? and iskorisceno is not null and uredjaj_hash = ?",
                Integer.class, predavanje, UREDJAJ_A));
    }

    @Test
    void kljucIstekaoPosle15Min() {
        String kljuc = cekiranje.skeniraj(token(predavanje), Optional.empty(), UREDJAJ_A).kljuc();
        jdbc.update("update cekiranja_na_cekanju set istice = ? where predavanje_id = ?",
                LocalDateTime.now(clock).minusSeconds(1), predavanje);
        ocekuj(410, CekiranjeService.KLJUC_ISTEKAO, () -> cekiranje.potvrdi(kljuc, ana, UREDJAJ_A));
        assertEquals(0, d.brojCekiranja(predavanje));
    }

    // ---------------------------------------------------------------- token i stanje predavanja

    @Test
    void tokenStarijiOdTriProzora410() {
        token(predavanje);
        String star = CekiranjeToken.token(d.tajna(predavanje), predavanje, clock.millis() - 35_000);
        ocekuj(410, CekiranjeService.QR_ISTEKAO, () -> cekiranje.skeniraj(star, Optional.of(ana), null));
        String nedavni = CekiranjeToken.token(d.tajna(predavanje), predavanje, clock.millis() - 15_000);
        assertNotNull(cekiranje.skeniraj(nedavni, Optional.of(ana), null).stanje());
        ocekuj(410, CekiranjeService.QR_ISTEKAO, () -> cekiranje.skeniraj("nije-token", Optional.of(ana), null));
        ocekuj(410, CekiranjeService.QR_ISTEKAO, () -> cekiranje.skeniraj(null, Optional.empty(), UREDJAJ_A));
        ocekuj(410, CekiranjeService.QR_ISTEKAO,
                () -> cekiranje.skeniraj("999999999.xxxxxxxxxxxxxxxxxxxxxx", Optional.empty(), UREDJAJ_A));
    }

    @Test
    void zavrsenoPredavanje410() {
        String t = token(predavanje);
        predavanjeService.zavrsiPredavanje(predavanje);
        assertNull(d.tajna(predavanje), "završetak briše tajnu");
        ocekuj(410, CekiranjeService.CAS_ZAVRSEN, () -> cekiranje.skeniraj(t, Optional.of(ana), null));
        ocekuj(410, CekiranjeService.CAS_ZAVRSEN, () -> cekiranje.skeniraj(t, Optional.empty(), UREDJAJ_A));
    }

    @Test
    void predavanjeBezTajne410() {
        String t = CekiranjeToken.token("abcdefghjkmnpqrstuvwxyzABCDEFGHJ", predavanje, clock.millis());
        ocekuj(410, CekiranjeService.QR_ISTEKAO, () -> cekiranje.skeniraj(t, Optional.of(ana), null));
    }

    @Test
    void tajnaStarijaOd5hNeVazi() {
        token(predavanje);
        String stara = d.tajna(predavanje);
        d.tajnaOd(predavanje, LocalDateTime.now(clock).minusHours(5).minusMinutes(1));
        String t = CekiranjeToken.token(stara, predavanje, clock.millis());
        ocekuj(410, CekiranjeService.QR_ISTEKAO, () -> cekiranje.skeniraj(t, Optional.of(ana), null));
        // nastavnički pogled pravi novu tajnu, pa novi token važi
        String novi = token(predavanje);
        assertNotEquals(stara, d.tajna(predavanje));
        assertNotNull(cekiranje.skeniraj(novi, Optional.of(ana), null).stanje());
    }

    // ---------------------------------------------------------------- grupa

    @Test
    void tudjaGrupa403() {
        Long druga = d.grupa(2026);
        Long tudj = d.student(druga, "Tuđin");
        String t = token(predavanje);
        ocekuj(403, CekiranjeService.TUDJA_GRUPA, () -> cekiranje.skeniraj(t, Optional.of(tudj), null));
        // mlađa generacija ne sme na predavanje starije
        Long mladja = d.grupa(2027);
        Long mladi = d.student(mladja, "Mladi");
        ocekuj(403, CekiranjeService.TUDJA_GRUPA, () -> cekiranje.skeniraj(t, Optional.of(mladi), null));
        // i preko ključa, a ključ ostaje neiskorišćen
        String kljuc = cekiranje.skeniraj(t, Optional.empty(), UREDJAJ_A).kljuc();
        ocekuj(403, CekiranjeService.TUDJA_GRUPA, () -> cekiranje.potvrdi(kljuc, tudj, UREDJAJ_A));
        assertNotNull(cekiranje.potvrdi(kljuc, ana, UREDJAJ_A).predavanje());
        assertEquals(1, d.brojCekiranja(predavanje));
    }

    @Test
    void starijaGeneracijaSme() {
        Long starija = d.grupa(2025);
        Long ponovac = d.student(starija, "Ponovac");
        assertNotNull(cekiranje.skeniraj(token(predavanje), Optional.of(ponovac), null).stanje());
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ponovac));
    }

    @Test
    void studentBezGrupe403() {
        Long bez = d.student(null, "Bezgrupni");
        String t = token(predavanje);
        ocekuj(403, CekiranjeService.TUDJA_GRUPA, () -> cekiranje.skeniraj(t, Optional.of(bez), null));
        // grupa bez godine upisa: samo svoji
        Long bezGodine = d.grupa(null);
        Long s = d.student(bezGodine, "Bezgodisnji");
        ocekuj(403, CekiranjeService.TUDJA_GRUPA, () -> cekiranje.skeniraj(t, Optional.of(s), null));
        // predavanje bez grupe: niko
        Long bezGrupe = d.predavanje(null, LocalDate.now(clock));
        String t2 = token(bezGrupe);
        ocekuj(403, CekiranjeService.TUDJA_GRUPA, () -> cekiranje.skeniraj(t2, Optional.of(ana), null));
    }

    // ---------------------------------------------------------------- ponavljanje i paralelni zahtevi

    @Test
    void ponovnoSkeniranjeNeMenjaVreme() {
        LocalDateTime prvo = cekiranje.skeniraj(token(predavanje), Optional.of(ana), UREDJAJ_A).stanje().cekiranU();
        LocalDateTime drugo = cekiranje.skeniraj(token(predavanje), Optional.of(ana), UREDJAJ_B).stanje().cekiranU();
        assertEquals(prvo, drugo);
        assertEquals(1, d.brojCekiranja(predavanje));
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ana));
        assertEquals(1, d.posecenost(predavanje));
    }

    @Test
    void postojecaAktivnostSeNeSpusta() {
        d.aktivnost(predavanje, ana, "ZADATAK");
        cekiranje.skeniraj(token(predavanje), Optional.of(ana), null);
        assertEquals(List.of("ZADATAK"), d.aktivnosti(predavanje, ana));
        assertEquals(1, d.brojCekiranja(predavanje));
    }

    @Test
    void paralelnoSkeniranjeJedanRed() throws Exception {
        String t = token(predavanje);
        ExecutorService ex = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<SkeniranjeOdgovor> zadatak = () -> {
                start.await();
                return cekiranje.skeniraj(t, Optional.of(ana), UREDJAJ_A);
            };
            Future<SkeniranjeOdgovor> a = ex.submit(zadatak);
            Future<SkeniranjeOdgovor> b = ex.submit(zadatak);
            start.countDown();
            assertNotNull(a.get(30, TimeUnit.SECONDS).stanje());
            assertNotNull(b.get(30, TimeUnit.SECONDS).stanje());
        } finally {
            ex.shutdownNow();
        }
        assertEquals(1, d.brojCekiranja(predavanje));
        assertEquals(List.of("PRISUSTVO"), d.aktivnosti(predavanje, ana));
        assertEquals(1, d.posecenost(predavanje));
    }

    // ---------------------------------------------------------------- stanje časa

    Long prezentacija(String naziv) {
        return prezentacijaService.kreiraj(new CreatePrezentacijaCmd(d.predmetId, naziv, null, null)).id();
    }

    IzvodjenjeInfo pokreni(Long prezentacijaId, Long predavanjeId) {
        return izvodjenjeService.pokreni(prezentacijaId, new PokreniCmd(false, null, predavanjeId));
    }

    @Test
    void stanjeVodiUAktivnoIzvodjenje() {
        cekiranje.skeniraj(token(predavanje), Optional.of(ana), null);
        assertNull(cekiranje.stanje(ana).izvodjenje());
        IzvodjenjeInfo staro = pokreni(prezentacija("Prva"), predavanje);
        IzvodjenjeInfo novo = pokreni(prezentacija("Druga"), predavanje);
        jdbc.update("update izvodjenja set pocetak = ? where id = ?", LocalDateTime.now(clock).minusMinutes(5), staro.id());
        // izvođenje bez predavanja telefon ne prati
        pokreni(prezentacija("Treća"), null);

        CasStanje s = cekiranje.stanje(ana);
        assertEquals(predavanje, s.predavanje().id());
        assertEquals(novo.kod(), s.izvodjenje().kod());
        assertEquals("Druga", s.izvodjenje().naziv());
        // skeniranje u toku izvođenja odmah vraća izvođenje
        assertEquals(novo.kod(), cekiranje.skeniraj(token(predavanje), Optional.of(bora), null).stanje().izvodjenje().kod());
        // student koji nije čekiran nije na času
        Long ceca = d.student(grupa, "Ceca");
        assertEquals(CasStanje.NIJE_NA_CASU, cekiranje.stanje(ceca));
    }

    @Test
    void stanjeBezIzvodjenjaIzKogJeIzbacen() {
        cekiranje.skeniraj(token(predavanje), Optional.of(ana), null);
        IzvodjenjeInfo iz = pokreni(prezentacija("Prva"), predavanje);
        UcesnikService.Prijavljen u = ucesnikService.prijavi(iz.kod(), "x", Optional.of(ana));
        izvodjenjeService.izbaci(iz.id(), u.info().ucesnikId());
        CasStanje s = cekiranje.stanje(ana);
        assertEquals(predavanje, s.predavanje().id());
        assertNull(s.izvodjenje());
    }

    @Test
    void stanjeIsticePosle5h() {
        cekiranje.skeniraj(token(predavanje), Optional.of(ana), null);
        pokreni(prezentacija("Prva"), predavanje);
        d.vremeCekiranja(predavanje, ana, LocalDateTime.now(clock).minusHours(4).minusMinutes(59));
        assertNotNull(cekiranje.stanje(ana).predavanje());
        d.vremeCekiranja(predavanje, ana, LocalDateTime.now(clock).minusHours(5).minusMinutes(1));
        assertEquals(CasStanje.NIJE_NA_CASU, cekiranje.stanje(ana));
    }

    @Test
    void stanjeNullKadJePrisustvoUklonjeno() {
        cekiranje.skeniraj(token(predavanje), Optional.of(ana), null);
        assertNotNull(cekiranje.stanje(ana).predavanje());
        predavanjeService.skloniPrisutnog(predavanje, ana);
        assertEquals(CasStanje.NIJE_NA_CASU, cekiranje.stanje(ana));
        assertEquals(0, d.brojCekiranja(predavanje));
    }

    @Test
    void stanjeZavrsenogPredavanjaBezIzvodjenja() {
        cekiranje.skeniraj(token(predavanje), Optional.of(ana), null);
        pokreni(prezentacija("Prva"), predavanje);
        predavanjeService.zavrsiPredavanje(predavanje);
        CasStanje s = cekiranje.stanje(ana);
        assertTrue(s.predavanje().zavrseno());
        assertNull(s.izvodjenje());
    }

    @Test
    void poslednjeCekiranjeVazi() {
        Long drugo = d.predavanje(grupa, LocalDate.now(clock));
        cekiranje.skeniraj(token(predavanje), Optional.of(ana), null);
        d.vremeCekiranja(predavanje, ana, LocalDateTime.now(clock).minusHours(1));
        cekiranje.skeniraj(token(drugo), Optional.of(ana), null);
        assertEquals(drugo, cekiranje.stanje(ana).predavanje().id());
    }

    // ---------------------------------------------------------------- nastavnički pogled

    @Test
    void zaNastavnikaKasnioIIstiTelefon() {
        Long ceca = d.student(grupa, "Ceca");
        String t = token(predavanje);
        cekiranje.skeniraj(t, Optional.of(ana), UREDJAJ_A);
        cekiranje.skeniraj(t, Optional.of(bora), UREDJAJ_B);
        cekiranje.skeniraj(t, Optional.of(ceca), UREDJAJ_A);
        LocalDateTime prvo = LocalDateTime.now(clock).minusMinutes(30);
        d.vremeCekiranja(predavanje, ana, prvo);
        d.vremeCekiranja(predavanje, bora, prvo.plusMinutes(15));
        d.vremeCekiranja(predavanje, ceca, prvo.plusMinutes(16));

        CekiranjeInfo info = cekiranje.zaNastavnika(predavanje);
        assertNotNull(info.token());
        assertTrue(info.token().startsWith(predavanje + "."));
        assertTrue(info.vaziDoMs() > info.serverVremeMs() && info.vaziDoMs() <= info.serverVremeMs() + 10_000);
        assertFalse(info.datumNijeDanas());
        List<CekiranStudent> c = info.cekirani();
        assertEquals(List.of(ana, bora, ceca), c.stream().map(CekiranStudent::studentId).toList());
        assertEquals(List.of(false, false, true), c.stream().map(CekiranStudent::kasnio).toList());
        assertNull(c.get(0).istiTelefonKao());
        assertNull(c.get(1).istiTelefonKao());
        assertEquals(ana, c.get(2).istiTelefonKao());
        assertEquals("Ceca", c.get(2).ime());
    }

    @Test
    void zaNastavnikaTokenNullZaZavrseno() {
        Long juce = d.predavanje(grupa, LocalDate.now(clock).minusDays(1));
        assertTrue(cekiranje.zaNastavnika(juce).datumNijeDanas());
        d.zavrsi(juce);
        CekiranjeInfo info = cekiranje.zaNastavnika(juce);
        assertNull(info.token());
        assertNull(info.vaziDoMs());
        // završeno predavanje bez tajne je i ne dobija
        Long zavrseno = d.predavanje(grupa, LocalDate.now(clock));
        d.zavrsi(zavrseno);
        assertNull(cekiranje.zaNastavnika(zavrseno).token());
        assertNull(d.tajna(zavrseno));
        ocekuj(404, "Predavanje ne postoji! ID = 999999999", () -> cekiranje.zaNastavnika(999_999_999L));
    }

    @Test
    void ciscenjeBriseIstekleIIskorisceneKljuceve() {
        LocalDateTime sada = LocalDateTime.now(clock);
        String vazeci = "1".repeat(64), istekao = "2".repeat(64), iskoriscenDavno = "3".repeat(64), iskoriscenSkoro = "4".repeat(64);
        String sql = "insert into cekiranja_na_cekanju (kljuc_hash, predavanje_id, skenirano, istice, iskorisceno, uredjaj_hash)"
                + " values (?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, vazeci, predavanje, sada, sada.plusMinutes(15), null, UREDJAJ_A);
        jdbc.update(sql, istekao, predavanje, sada.minusHours(3), sada.minusHours(2), null, UREDJAJ_A);
        jdbc.update(sql, iskoriscenDavno, predavanje, sada.minusHours(3), sada.plusMinutes(5), sada.minusHours(2), UREDJAJ_A);
        jdbc.update(sql, iskoriscenSkoro, predavanje, sada.minusMinutes(20), sada.plusMinutes(5), sada.minusMinutes(10), UREDJAJ_A);
        ciscenje.ocisti();
        assertEquals(List.of(vazeci, iskoriscenSkoro), jdbc.queryForList(
                "select kljuc_hash from cekiranja_na_cekanju where predavanje_id = ? order by kljuc_hash", String.class, predavanje));
    }

    @Test
    void tajnaSeNeMenjaDokVazi() {
        token(predavanje);
        String prva = d.tajna(predavanje);
        token(predavanje);
        assertEquals(prva, d.tajna(predavanje));
        assertTrue(prva.matches("^[A-Za-z0-9]{32}$"));
    }
}
