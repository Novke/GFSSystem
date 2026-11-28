package tri.novica.gfssystem.service.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.Cookie;
import tri.novica.gfssystem.dto.test.online.*;
import tri.novica.gfssystem.entity.test.RezimTesta;
import tri.novica.gfssystem.entity.test.VrstaIspravke;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PolaganjeRepository;
import tri.novica.gfssystem.service.TestService;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.VarijantaService;
import tri.novica.gfssystem.service.prijava.PrijavaRacunaraService;
import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Online test nad pravom MySQL bazom (Flyway, kao CI): šablon iz TEST banke, pravljenje, tok, polaganje studenta (ulazni kod,
 * jedan uređaj, rev, istek sa tolerancijom, dvostruka predaja), ocena u polaganje, ispravka ključa naspram ručnih poena,
 * objava i ključevi studentskog JSON-a, banka (korišćena varijanta) i prijava računara. Bez {@code @Transactional}: svaki
 * poziv servisa se commit-uje, test čisti za sobom.
 */
@SpringBootTest
class OnlineTestDbTest {

    static final String SEMA = """
            [{"kljuc":"a","oznaka":"a)","tip":"BROJ","odstupanje":0,"odstupanjeTip":"APSOLUTNO"}]""";
    static final String NEMA = "Nije pronađeno.";

    @Autowired OnlineTestService online;
    @Autowired PolaganjeOnlineTesta polaganje;
    @Autowired RezultatiOnlineTesta rezultati;
    @Autowired SablonTestaService sabloni;
    @Autowired TestService testService;
    @Autowired VarijantaService varijante;
    @Autowired GrupaZadatakaService grupe;
    @Autowired PrijavaRacunaraService racunar;
    @Autowired PolaganjeRepository polaganjeRepository;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;

    long predmet, grupa, tip, koren, list;
    final List<Long> studenti = new ArrayList<>();
    final List<Long> varijanteIds = new ArrayList<>();
    final Map<Long, Double> tacno = new HashMap<>();
    final List<String> mediji = new ArrayList<>();
    final List<Long> testovi = new ArrayList<>();
    long sablon;
    String oznaka;
    /** Uređaji (KOD) studenata: aktivni uređaj primerka je FK na studentski_uredjaji. */
    long uA, uB, uB2, uC;

    @BeforeEach
    void setUp() {
        oznaka = UUID.randomUUID().toString().substring(0, 8);
        predmet = insert("predmeti", Map.of("naziv", "Online test DB " + oznaka));
        grupa = insert("grupe", Map.of("naziv", "OT " + oznaka, "godina_upisa", 2026));
        tip = insert("tipovi_testa", Map.of("naziv", "Kolokvijum " + oznaka, "predmet_id", predmet, "aktivan", true));
        for (String ime : List.of("Ana", "Bora", "Ceca")) {
            studenti.add(insert("studenti", Map.of("ime", ime, "prezime", "Online" + oznaka, "godina", 2026,
                    "indeks", "GD" + (100 + studenti.size()) + oznaka.substring(0, 3), "grupa_id", grupa)));
        }
        uA = uredjaj(studenti.get(0), "KOD");
        uB = uredjaj(studenti.get(1), "KOD");
        uB2 = uredjaj(studenti.get(1), "KOD");
        uC = uredjaj(studenti.get(2), "KOD");
        koren = grupa(null, "Koren", "[]");
        list = grupa(koren, "Sabiranje", SEMA);
        for (int n = 1; n <= 4; n++) varijanta(list, "V" + n, 10.0 * n);
        sablon = sabloni.kreiraj(new SablonCmd(predmet, tip, "Šablon " + oznaka, null, 45, RezimTesta.POJEDINACNO, 1, 5,
                List.of(new StavkaSablonaCmd("Zadatak 1", 10, 1, List.of(2), false, false, List.of(list))))).id();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from prijave_racunara where student_id in (" + ids(studenti) + ") or student_id is null and kreirano > now() - interval 1 hour and ip = 'test-" + oznaka + "'");
        jdbc.update("delete from pokusaji where kljuc = ?", "ip:test-" + oznaka);
        for (Long t : testovi) {
            jdbc.update("delete from polaganja where test_id = ?", t);
            jdbc.update("delete from testovi where id = ?", t);
        }
        jdbc.update("delete from pokusaji where kljuc like 'primerak:%' and vreme > now() - interval 1 hour");
        jdbc.update("delete from sabloni_testa where predmet_id = ?", predmet);
        jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", predmet);
        jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", predmet);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", predmet);
        for (String m : mediji) jdbc.update("delete from mediji where id = ?", m);
        jdbc.update("delete from studenti where id in (" + ids(studenti) + ")");
        jdbc.update("delete from tipovi_testa where id = ?", tip);
        jdbc.update("delete from grupe where id = ?", grupa);
        jdbc.update("delete from predmeti where id = ?", predmet);
    }

    // ---------------------------------------------------------------- tok

    @Test
    void tokOdPripremeDoObjave() throws Exception {
        OnlineTest t = napravi();
        assertEquals(StanjeOnline.PRIPREMA, t.stanje());
        assertEquals(10, t.maxPoena());
        assertEquals(3, t.studenti());
        long ana = studenti.get(0);
        // test u pripremi je za studenta isto što i nepostojeći
        greska(404, NEMA, () -> polaganje.ulaz(ana, uA, t.testId(), new UlazCmd(null, false), "1.1.1.1"));

        OnlineTest ulaz = online.otvoriUlaz(t.testId(), new OtvoriUlazCmd(true));
        String kod = ulaz.smena().ulazniKod();
        assertNotNull(kod);
        assertEquals(1, polaganje.spisak(ana).size());
        greska(403, PolaganjeOnlineTesta.POGRESAN_KOD, () -> polaganje.ulaz(ana, uA, t.testId(), new UlazCmd("x", false), "1.1.1.1"));
        PolaganjeStudenta ceka = polaganje.ulaz(ana, uA, t.testId(), new UlazCmd(kod, false), "1.1.1.1");
        assertEquals(FazaTesta.CEKANJE, ceka.faza());
        assertNull(ceka.zadaci(), "zadaci tek posle Pokreni");

        online.pokreni(t.testId());
        PolaganjeStudenta radi = polaganje.stanje(ana, uA, t.testId());
        assertEquals(FazaTesta.U_TOKU, radi.faza());
        assertEquals(1, radi.zadaci().size());
        ZadatakTesta z = radi.zadaci().get(0);
        String telo = json.writeValueAsString(radi);
        for (String kljuc : List.of("\"odgovori\"", "\"resenjeSvgId\"", "\"tacno\"", "\"konacnoPriznajeKorake\"")) {
            assertFalse(telo.contains(kljuc), "student ne sme dobiti " + kljuc + ": " + telo);
        }
        double ocekivano = tacnoZa(t.testId(), ana);
        Sacuvano s = polaganje.sacuvaj(ana, uA, t.testId(), z.zadatakId(), new CuvanjeCmd(0, Map.of("a", broj(ocekivano))));
        assertEquals(1, s.rev());
        greska(409, PolaganjeOnlineTesta.ZASTARELA,
                () -> polaganje.sacuvaj(ana, uA, t.testId(), z.zadatakId(), new CuvanjeCmd(0, Map.of("a", "1"))));
        greska(400, "Neispravan format podataka.",
                () -> polaganje.sacuvaj(ana, uA, t.testId(), z.zadatakId(), new CuvanjeCmd(1, Map.of("b", "1"))));

        PolaganjeStudenta predato = polaganje.predaj(ana, uA, t.testId());
        assertEquals(FazaTesta.PREDATO, predato.faza());
        assertNull(predato.zadaci());
        greska(409, PolaganjeOnlineTesta.PREDAT, () -> polaganje.predaj(ana, uA, t.testId()));
        assertEquals(10.0, polaganjeRepository.findByTestIdAndStudentId(t.testId(), ana).orElseThrow().getOstvareniPoeni());
        // online test ulazi u ocenu tek kad je pregledan
        assertNull(polaganjeRepository.findMaxPoeniByStudentAndTipTesta(ana, tip));

        // zatvaranje smene predaje one koji rade; ko nije ni ušao ostaje bez rada i bez polaganja
        greska(409, OnlineTestPravila.NIJE_ZAVRSEN, () -> testService.zavrsiEvidentiranje(t.testId()));
        online.zavrsi(t.testId());
        KonzolaTesta k = online.konzola(t.testId());
        assertEquals(StanjeOnline.ZAVRSEN, k.stanje());
        assertEquals(1, k.predalo());
        assertEquals(3, k.ukupno());
        assertTrue(polaganjeRepository.findByTestIdAndStudentId(t.testId(), studenti.get(1)).isEmpty());
        // rezultat se ne vidi pre objave
        greska(404, NEMA, () -> polaganje.rezultat(ana, t.testId()));
        testService.zavrsiEvidentiranje(t.testId());
        assertEquals(10.0, polaganjeRepository.findMaxPoeniByStudentAndTipTesta(ana, tip));

        rezultati.objavi(t.testId(), new ObjavaCmd(false));
        RezultatTestaStudenta bez = polaganje.rezultat(ana, t.testId());
        assertEquals(10.0, bez.poeni());
        assertFalse(bez.tacniPrikazani());
        assertNull(bez.zadaci().get(0).resenjeSvgId());
        assertNull(bez.zadaci().get(0).polja().get(0).tacno());
        String telo2 = json.writeValueAsString(bez);
        assertFalse(telo2.contains("\"odgovori\""), telo2);
        rezultati.objavi(t.testId(), new ObjavaCmd(true));
        RezultatTestaStudenta sa = polaganje.rezultat(ana, t.testId());
        assertEquals(ocekivano, sa.zadaci().get(0).polja().get(0).tacno().broj());
        List<RezultatTestaKratko> kratko = polaganje.rezultatiPredmeta(ana, predmet);
        assertEquals(1, kratko.size());
        assertTrue(kratko.get(0).racunaSe());
        // tuđ rezultat i nepostojeći test
        greska(404, NEMA, () -> polaganje.rezultat(ana, 999_999_999L));
        // paper endpointi su zatvoreni za online test; završen test se briše sa primercima i polaganjima
        greska(409, OnlineTestPravila.SAMO_PAPIR, () -> testService.dodajIspitanika(t.testId(), ana));
        testService.obrisi(t.testId());
        polaganjeRepository.flush();
        assertTrue(polaganjeRepository.findByTestIdAndStudentId(t.testId(), ana).isEmpty());
    }

    @Test
    void dvaUredjajaIPreuzimanje() {
        OnlineTest t = napravi();
        online.otvoriUlaz(t.testId(), new OtvoriUlazCmd(false));
        long bora = studenti.get(1);
        polaganje.ulaz(bora, uB, t.testId(), new UlazCmd(null, false), "1.1.1.1");
        online.pokreni(t.testId());
        greska(409, PolaganjeOnlineTesta.DRUGI_UREDJAJ, () -> polaganje.stanje(bora, uB2, t.testId()));
        greska(409, PolaganjeOnlineTesta.DRUGI_UREDJAJ, () -> polaganje.ulaz(bora, uB2, t.testId(), new UlazCmd(null, false), "2.2.2.2"));
        PolaganjeStudenta drugi = polaganje.ulaz(bora, uB2, t.testId(), new UlazCmd(null, true), "2.2.2.2");
        Long z = drugi.zadaci().get(0).zadatakId();
        greska(409, PolaganjeOnlineTesta.DRUGI_UREDJAJ,
                () -> polaganje.sacuvaj(bora, uB, t.testId(), z, new CuvanjeCmd(0, Map.of("a", "1"))));
        polaganje.sacuvaj(bora, uB2, t.testId(), z, new CuvanjeCmd(0, Map.of("a", "1")));
        KonzolaTesta k = online.konzola(t.testId());
        StudentKonzole sk = k.studenti().stream().filter(x -> x.studentId() == bora).findFirst().orElseThrow();
        assertEquals(1, sk.promenaUredjaja());
        assertFalse(sk.drugaMreza(), "znak druge mreže tek uz bar pet učesnika sa adresom");
        // tuđ zadatak: student koji nije na testu
        long tudji = insert("studenti", Map.of("ime", "Dejan", "prezime", "Online" + oznaka, "godina", 2026,
                "indeks", "GD9" + oznaka.substring(0, 3), "grupa_id", grupa));
        studenti.add(tudji);
        greska(404, NEMA, () -> polaganje.stanje(tudji, uA, t.testId()));
        greska(404, NEMA, () -> polaganje.sacuvaj(tudji, uA, t.testId(), z, new CuvanjeCmd(0, Map.of())));
    }

    @Test
    void istekSaTolerancijomIDvostrukaPredaja() throws Exception {
        OnlineTest t = napravi();
        online.otvoriUlaz(t.testId(), new OtvoriUlazCmd(false));
        long ana = studenti.get(0);
        long ceca = studenti.get(2);
        polaganje.ulaz(ana, uA, t.testId(), new UlazCmd(null, false), "1.1.1.1");
        polaganje.ulaz(ceca, uC, t.testId(), new UlazCmd(null, false), "1.1.1.1");
        online.pokreni(t.testId());
        Long z = polaganje.stanje(ana, uA, t.testId()).zadaci().get(0).zadatakId();
        Long zc = polaganje.stanje(ceca, uC, t.testId()).zadaci().get(0).zadatakId();

        // rok prošao pre 5 s: unutar 10 s tolerancije se još čuva
        jdbc.update("update test_smene set rok = ? where test_id = ?", LocalDateTime.now().minusSeconds(5), t.testId());
        polaganje.sacuvaj(ana, uA, t.testId(), z, new CuvanjeCmd(0, Map.of("a", broj(tacnoZa(t.testId(), ana)))));
        // pre 20 s: odbijeno, a sledeće čitanje zaključava primerak sa sačuvanim (ISTEK)
        jdbc.update("update test_smene set rok = ? where test_id = ?", LocalDateTime.now().minusSeconds(20), t.testId());
        greska(409, PolaganjeOnlineTesta.ISTEKLO,
                () -> polaganje.sacuvaj(ana, uA, t.testId(), z, new CuvanjeCmd(1, Map.of("a", "0"))));
        assertEquals(FazaTesta.PREDATO, polaganje.stanje(ana, uA, t.testId()).faza());
        assertEquals(10.0, polaganjeRepository.findByTestIdAndStudentId(t.testId(), ana).orElseThrow().getOstvareniPoeni());

        // dvostruka predaja u isto vreme: tačno jedna prolazi
        jdbc.update("update test_smene set rok = ? where test_id = ?", LocalDateTime.now().plusMinutes(10), t.testId());
        polaganje.sacuvaj(ceca, uC, t.testId(), zc, new CuvanjeCmd(0, Map.of("a", "1")));
        ExecutorService ex = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> f = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                f.add(ex.submit(() -> {
                    start.await();
                    try {
                        polaganje.predaj(ceca, uC, t.testId());
                        return "ok";
                    } catch (SystemException e) {
                        return e.getMessage();
                    }
                }));
            }
            start.countDown();
            List<String> ishodi = new ArrayList<>();
            for (Future<String> x : f) ishodi.add(x.get(30, TimeUnit.SECONDS));
            assertEquals(1, ishodi.stream().filter("ok"::equals).count(), ishodi.toString());
            assertTrue(ishodi.contains(PolaganjeOnlineTesta.PREDAT), ishodi.toString());
        } finally {
            ex.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from polaganja where test_id = ? and student_id = ?",
                Integer.class, t.testId(), ceca));
    }

    @Test
    void ispravkaKljucaIRucniPoeniIVracanje() {
        OnlineTest t = napravi();
        online.otvoriUlaz(t.testId(), new OtvoriUlazCmd(false));
        long ana = studenti.get(0);
        polaganje.ulaz(ana, uA, t.testId(), new UlazCmd(null, false), "1.1.1.1");
        online.pokreni(t.testId());
        Long z = polaganje.stanje(ana, uA, t.testId()).zadaci().get(0).zadatakId();
        polaganje.sacuvaj(ana, uA, t.testId(), z, new CuvanjeCmd(0, Map.of("a", "7")));
        polaganje.predaj(ana, uA, t.testId());
        assertEquals(0.0, poeni(t.testId(), ana));
        Long varijanta = jdbc.queryForObject("select varijanta_id from test_primerak_zadaci where id = ?", Long.class, z);

        OdgovoriTesta o = rezultati.ispravi(t.testId(), new IspravkaCmd(varijanta, "a", VrstaIspravke.KLJUC, "{\"broj\":7}"));
        assertEquals(10.0, poeni(t.testId(), ana));
        Long ispravka = o.ispravke().get(o.ispravke().size() - 1).id();
        // ručni poeni pobeđuju automatske, i posle nove ispravke
        rezultati.rucniPoeni(t.testId(), z, new RucniPoeniCmd(4.5, "delimično"));
        assertEquals(4.5, poeni(t.testId(), ana));
        rezultati.vratiIspravku(t.testId(), ispravka);
        assertEquals(4.5, poeni(t.testId(), ana));
        greska(409, "Ispravka je već vraćena.", () -> rezultati.vratiIspravku(t.testId(), ispravka));
        rezultati.rucniPoeni(t.testId(), z, new RucniPoeniCmd(null, null));
        assertEquals(0.0, poeni(t.testId(), ana));
        greska(400, null, () -> rezultati.rucniPoeni(t.testId(), z, new RucniPoeniCmd(11.0, null)));
    }

    // ---------------------------------------------------------------- banka

    @Test
    void varijantaIGrupaSaTestaSeNeBrisu() {
        OnlineTest t = napravi();
        Long naTestu = jdbc.queryForObject("select min(varijanta_id) from test_primerak_zadaci z join test_primerci p on p.id = z.primerak_id "
                + "where p.test_id = ?", Long.class, t.testId());
        greska(409, GrupaZadatakaService.KORISCENO_NA_TESTU, () -> varijante.obrisi(naTestu));
        greska(409, GrupaZadatakaService.KORISCENO_NA_TESTU, () -> grupe.obrisi(list));
    }

    // ---------------------------------------------------------------- prijava računara

    @Test
    void prijavaRacunara() {
        long ana = studenti.get(0);
        long kodUredjaj = uredjaj(ana, "KOD");
        long racunarUredjaj = uredjaj(ana, "RACUNAR");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        ZahtevRacunara z = racunar.zahtev("test-" + oznaka, req, res);
        assertEquals(4, z.broj().length());
        Cookie tajna = res.getCookie(PrijavaRacunaraService.KOLACIC);
        assertNotNull(tajna);
        assertEquals(1, jdbc.queryForObject("select count(*) from prijave_racunara where token_hes = ?", Integer.class,
                TokenHash.od(z.token())), "u bazi samo heš tokena");

        MockHttpServletRequest citaj = new MockHttpServletRequest();
        citaj.setCookies(tajna);
        assertEquals("CEKA", racunar.stanje(null, citaj, new MockHttpServletResponse()).stanje());

        assertEquals(z.broj(), racunar.zaOdobrenje(ana, z.token()).broj());
        greska(403, PrijavaRacunaraService.SA_TELEFONA, () -> racunar.odobri(ana, racunarUredjaj, z.token()));
        racunar.odobri(ana, kodUredjaj, z.token());

        MockHttpServletResponse preuzeto = new MockHttpServletResponse();
        assertEquals("ODOBREN", racunar.stanje(null, citaj, preuzeto).stanje());
        assertNotNull(preuzeto.getCookie("gfs_student"));
        Map<String, Object> novi = jdbc.queryForMap(
                "select nacin, timestampdiff(minute, prvi_ulaz, istice) m from studentski_uredjaji where student_id = ? order by id desc limit 1", ana);
        assertEquals("RACUNAR", novi.get("nacin"));
        assertEquals(240L, ((Number) novi.get("m")).longValue());
        // jednokratno
        assertEquals("ISTEKAO", racunar.stanje(null, citaj, new MockHttpServletResponse()).stanje());
        greska(410, PrijavaRacunaraService.ISTEKLA, () -> racunar.odobri(ana, kodUredjaj, z.token()));
        greska(410, PrijavaRacunaraService.ISTEKLA, () -> racunar.zaOdobrenje(ana, "nepostojeci"));
    }

    // ---------------------------------------------------------------- pomoćno

    OnlineTest napravi() {
        OnlineTest t = online.kreiraj(new NoviOnlineTestCmd(sablon, grupa, tip, LocalDate.now(), 30, null, null, studenti.subList(0, 3)));
        testovi.add(t.testId());
        return t;
    }

    double tacnoZa(long testId, long studentId) {
        Long v = jdbc.queryForObject("select z.varijanta_id from test_primerak_zadaci z join test_primerci p on p.id = z.primerak_id "
                + "where p.test_id = ? and p.student_id = ?", Long.class, testId, studentId);
        return tacno.get(v);
    }

    Double poeni(long testId, long studentId) {
        return polaganjeRepository.findByTestIdAndStudentId(testId, studentId).orElseThrow().getOstvareniPoeni();
    }

    static String broj(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    long insert(String tabela, Map<String, Object> red) {
        return new SimpleJdbcInsert(jdbc).withTableName(tabela).usingGeneratedKeyColumns("id").executeAndReturnKey(red).longValue();
    }

    long grupa(Long roditelj, String naziv, String polja) {
        Map<String, Object> red = new HashMap<>();
        red.put("predmet_id", predmet);
        red.put("roditelj_id", roditelj);
        red.put("namena", "TEST");
        red.put("naziv", naziv);
        red.put("rb", 1);
        red.put("tezina", roditelj == null ? null : 3);
        red.put("polja", polja);
        red.put("izvor", "RUCNO");
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        return insert("grupe_zadataka", red);
    }

    void varijanta(long g, String naziv, double odgovor) {
        String svgId = UUID.randomUUID().toString();
        String resenje = UUID.randomUUID().toString();
        for (String id : List.of(svgId, resenje)) {
            jdbc.update("insert into mediji (id, naziv, mime, velicina, vrsta, kreirano) "
                    + "values (?, 'render.svg', 'image/svg+xml', 10, 'RENDER_SVG', now(6))", id);
            mediji.add(id);
        }
        Map<String, Object> red = new HashMap<>();
        red.put("grupa_id", g);
        red.put("rb", varijanteIds.size() + 1);
        red.put("naziv", naziv);
        red.put("tekst", "Koliko je " + naziv);
        red.put("odgovori", "{\"a\":{\"broj\":" + odgovor + "}}");
        red.put("status", "AKTIVNA");
        red.put("izvor", "RUCNO");
        red.put("ikad_odobrena", true);
        red.put("render_status", "OK");
        red.put("render_svg_id", svgId);
        red.put("render_resenje_svg_id", resenje);
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        red.put("nivo", 2);
        long id = insert("varijante", red);
        varijanteIds.add(id);
        tacno.put(id, odgovor);
    }

    long uredjaj(long student, String nacin) {
        Map<String, Object> red = new HashMap<>();
        red.put("student_id", student);
        red.put("token_hes", TokenHash.od(UUID.randomUUID().toString()));
        red.put("nacin", nacin);
        red.put("prvi_ulaz", LocalDateTime.now());
        red.put("poslednji_ulaz", LocalDateTime.now());
        red.put("istice", LocalDateTime.now().plusDays(1));
        return insert("studentski_uredjaji", red);
    }

    static String ids(List<Long> l) {
        return l.isEmpty() ? "-1" : String.join(",", l.stream().map(String::valueOf).toList());
    }

    static void greska(int kod, String poruka, Executable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv);
        assertEquals(kod, e.getCode(), e.getMessage());
        if (poruka != null) assertEquals(poruka, e.getMessage());
    }
}
