package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Izvor i serija u banci nad pravom MySQL bazom (Flyway V1..V12, kao CI) sa lažnim {@link LatexRender} beanom: grupa
 * koju pravi Klod ({@code kreiraj(cmd, CLAUDE, serija)}), brojač Klodovih nacrta u stablu, {@code ikadOdobrena} i
 * {@code zamenjuje} u DTO-ovima, odobrena kopija arhivira original, pretraga po izvoru i seriji, brisanje samo prazne
 * grupe i njena trka sa dodavanjem varijante. Bez {@code @Transactional}: svaki poziv servisa se zaista commit-uje, pa test
 * čisti za sobom kroz JDBC.
 */
@SpringBootTest
class BankaIzvorDbTest {

    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>".getBytes(StandardCharsets.UTF_8);
    static final String PRAZNA = "Briše se samo prazna grupa.";
    static final Pageable PRVIH_25 = PageRequest.of(0, 25);

    @Autowired GrupaZadatakaService grupaService;
    @Autowired VarijantaService varijantaService;
    @Autowired BankaPretragaService pretragaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transakcije;
    @MockitoBean LatexRender latexRender;

    Predmet predmet;

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Banka izvor DB test " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 7L, false));
    }

    @AfterEach
    void tearDown() {
        Long id = predmet.getId();
        List<Long> varijante = jdbc.queryForList("select v.id from varijante v join grupe_zadataka g on g.id = v.grupa_id "
                + "where g.predmet_id = ?", Long.class, id);
        Set<String> svg = new LinkedHashSet<>();
        for (Long v : varijante) {
            svg.addAll(jdbc.queryForList("select id from mediji where naziv in (?, ?)", String.class,
                    "zadatak-" + v + ".svg", "resenje-" + v + ".svg"));
        }
        String grupePredmeta = "(select id from grupe_zadataka where predmet_id = ?)";
        jdbc.update("update varijante set zamenjuje_id = null where grupa_id in " + grupePredmeta, id);
        jdbc.update("delete from varijante where grupa_id in " + grupePredmeta, id);
        jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        svg.forEach(medijService::obrisi);
        predmetRepository.deleteById(id);
    }

    GrupaDetails koren(String naziv, Integer tezina) {
        return grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, naziv, null, tezina, null, List.of()));
    }

    GrupaDetails podgrupa(GrupaDetails roditelj, String naziv, Integer tezina) {
        return grupaService.kreiraj(new GrupaCmd(null, roditelj.id(), null, naziv, null, tezina, null, List.of()));
    }

    VarijantaDetails varijanta(GrupaDetails grupa, IzvorVarijante izvor) {
        return varijantaService.kreiraj(grupa.id(), new VarijantaCmd("V", "Pretvori $1011_2$ u dekadni.", null, Map.of(), List.of(), izvor));
    }

    String status(Long varijantaId) {
        return jdbc.queryForObject("select status from varijante where id = ?", String.class, varijantaId);
    }

    LocalDateTime izmenjeno(Long varijantaId) {
        return jdbc.queryForObject("select izmenjeno from varijante where id = ?", LocalDateTime.class, varijantaId);
    }

    boolean postojiGrupa(Long grupaId) {
        return jdbc.queryForObject("select count(*) from grupe_zadataka where id = ?", Integer.class, grupaId) == 1;
    }

    static String ishod(Supplier<String> posao) {
        try {
            return posao.get();
        } catch (SystemException e) {
            return e.getCode() + " " + e.getMessage();
        }
    }

    @Test
    void grupaZnaIzvorISeriju() {
        GrupaDetails rucna = koren("Konverzije", null);
        assertEquals(IzvorVarijante.RUCNO, rucna.izvor());
        assertNull(rucna.serija());
        assertFalse(rucna.iskljucena());
        assertNull(rucna.iskljucenjeId());

        GrupaDetails klodova = grupaService.kreiraj(new GrupaCmd(null, rucna.id(), null, "2 → 10", null, 2, null, List.of()),
                IzvorVarijante.CLAUDE, "uvoz-1");
        assertEquals(IzvorVarijante.CLAUDE, klodova.izvor());
        assertEquals("uvoz-1", klodova.serija());
        assertEquals(Namena.PREDAVANJE, klodova.namena());
        assertEquals(Map.of("izvor", "CLAUDE", "serija", "uvoz-1"),
                jdbc.queryForMap("select izvor, serija from grupe_zadataka where id = ?", klodova.id()));
        GrupaDetails procitana = grupaService.detalji(klodova.id());
        assertEquals(IzvorVarijante.CLAUDE, procitana.izvor());
        assertEquals("uvoz-1", procitana.serija());
        assertEquals(IzvorVarijante.RUCNO, grupaService.detalji(rucna.id()).izvor());

        // serija van obrasca je 400 pre ključa, ništa se ne upisuje
        SystemException e = assertThrows(SystemException.class, () -> grupaService.kreiraj(
                new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum", null, null, null, List.of()), IzvorVarijante.CLAUDE, "Uvoz"));
        assertEquals(400, e.getCode());
        assertEquals(Serija.PORUKA, e.getMessage());
        assertEquals(2, jdbc.queryForObject("select count(*) from grupe_zadataka where predmet_id = ?", Integer.class, predmet.getId()));
    }

    @Test
    void stabloBrojiKlodoveNacrteIPodstablo() {
        GrupaDetails r = koren("Konverzije", null);
        GrupaDetails a = podgrupa(r, "2 → 10", 2);
        GrupaDetails b = podgrupa(r, "16 → 2", 3);
        varijanta(a, IzvorVarijante.CLAUDE);
        varijanta(a, IzvorVarijante.CLAUDE);
        varijanta(a, IzvorVarijante.RUCNO);
        varijantaService.odobri(varijanta(a, IzvorVarijante.CLAUDE).id());   // Klodova, ali više nije nacrt
        varijanta(b, IzvorVarijante.CLAUDE);
        grupaService.kreiraj(new GrupaCmd(null, r.id(), null, "Klodova prazna", null, null, null, List.of()), IzvorVarijante.CLAUDE, "uvoz-1");

        List<GrupaCvor> stablo = grupaService.stablo(predmet.getId(), Namena.PREDAVANJE);

        GrupaCvor cr = stablo.get(0);
        assertEquals(3, cr.brojClaudeNacrta());
        assertEquals(List.of(5, 1, 4), List.of(cr.brojVarijanti(), cr.brojAktivnih(), cr.brojNacrta()));
        assertEquals(IzvorVarijante.RUCNO, cr.izvor());
        assertFalse(cr.iskljucena());
        assertNull(cr.iskljucenjeId());
        Map<String, GrupaCvor> deca = new HashMap<>();
        cr.deca().forEach(c -> deca.put(c.naziv(), c));
        assertEquals(2, deca.get("2 → 10").brojClaudeNacrta());
        assertEquals(1, deca.get("16 → 2").brojClaudeNacrta());
        assertEquals(0, deca.get("Klodova prazna").brojClaudeNacrta());
        assertEquals(IzvorVarijante.CLAUDE, deca.get("Klodova prazna").izvor());
        assertEquals(IzvorVarijante.RUCNO, deca.get("2 → 10").izvor());
    }

    @Test
    void odobriPamtiDaJeBilaOdobrena() {
        GrupaDetails g = koren("2 → 10", 2);
        VarijantaDetails nacrt = varijanta(g, IzvorVarijante.CLAUDE);
        assertFalse(nacrt.ikadOdobrena());
        assertNull(nacrt.zamenjuje());
        assertNull(nacrt.serija());

        VarijantaDetails odobrena = varijantaService.odobri(nacrt.id());
        assertEquals(StatusVarijante.AKTIVNA, odobrena.status());
        assertTrue(odobrena.ikadOdobrena());

        VarijantaDetails vracena = varijantaService.vratiUNacrt(nacrt.id());
        assertEquals(StatusVarijante.NACRT, vracena.status());
        assertTrue(vracena.ikadOdobrena());
        assertTrue(varijantaService.detalji(nacrt.id()).ikadOdobrena());
        assertTrue(jdbc.queryForObject("select ikad_odobrena from varijante where id = ?", Boolean.class, nacrt.id()));
        VarijantaInfo info = varijantaService.lista(g.id(), null).get(0);
        assertTrue(info.ikadOdobrena());
        assertNull(info.zamenjuje());
    }

    @Test
    void odobrenaKopijaArhiviraOriginal() {
        GrupaDetails g = koren("2 → 10", 2);
        Long original = varijantaService.odobri(varijanta(g, IzvorVarijante.RUCNO).id()).id();
        Long kopija = varijanta(g, IzvorVarijante.CLAUDE).id();
        jdbc.update("update varijante set zamenjuje_id = ?, serija = 'uvoz-1' where id = ?", original, kopija);

        VarijantaDetails procitana = varijantaService.detalji(kopija);
        assertEquals(original, procitana.zamenjuje());
        assertEquals("uvoz-1", procitana.serija());
        VarijantaInfo info = varijantaService.lista(g.id(), StatusVarijante.NACRT).get(0);
        assertEquals(original, info.zamenjuje());
        assertEquals("uvoz-1", info.serija());
        assertFalse(info.ikadOdobrena());

        // odobrenje koje ne prođe provere ne dira original
        jdbc.update("update varijante set render_status = 'GRESKA' where id = ?", kopija);
        assertThrows(SystemException.class, () -> varijantaService.odobri(kopija));
        assertEquals("AKTIVNA", status(original));
        jdbc.update("update varijante set render_status = 'OK' where id = ?", kopija);

        VarijantaDetails odobrena = varijantaService.odobri(kopija);
        assertEquals(StatusVarijante.AKTIVNA, odobrena.status());
        assertTrue(odobrena.ikadOdobrena());
        assertEquals("ARHIVIRANA", status(original));
        assertTrue(jdbc.queryForObject("select ikad_odobrena from varijante where id = ?", Boolean.class, original));

        // ponovljeno odobrenje već aktivne kopije ne dira original koji je Novica u međuvremenu vratio
        varijantaService.odobri(original);
        LocalDateTime pre = izmenjeno(original);
        varijantaService.odobri(kopija);
        assertEquals("AKTIVNA", status(original));
        assertEquals(pre, izmenjeno(original));
    }

    @Test
    void kopijaCijiJeOriginalVecArhiviranGaNeDira() {
        GrupaDetails g = koren("2 → 10", 2);
        Long original = varijantaService.arhiviraj(varijanta(g, IzvorVarijante.RUCNO).id()).id();
        LocalDateTime pre = izmenjeno(original);
        Long kopija = varijanta(g, IzvorVarijante.CLAUDE).id();
        jdbc.update("update varijante set zamenjuje_id = ? where id = ?", original, kopija);

        assertEquals(StatusVarijante.AKTIVNA, varijantaService.odobri(kopija).status());
        assertEquals("ARHIVIRANA", status(original));
        assertEquals(pre, izmenjeno(original));
    }

    @Test
    void pretragaPoIzvoruISeriji() {
        GrupaDetails g = koren("2 → 10", 2);
        Long rucna = varijanta(g, IzvorVarijante.RUCNO).id();
        Long klodova = varijanta(g, IzvorVarijante.CLAUDE).id();
        Long izSerije = varijanta(g, IzvorVarijante.CLAUDE).id();
        jdbc.update("update varijante set serija = 'uvoz-1' where id = ?", izSerije);
        Long id = predmet.getId();

        assertEquals(Set.of(klodova, izSerije), ids(new BankaFilter(id, null, null, null, IzvorVarijante.CLAUDE, null)));
        assertEquals(Set.of(rucna), ids(new BankaFilter(id, null, null, null, IzvorVarijante.RUCNO, null)));
        assertEquals(Set.of(izSerije), ids(new BankaFilter(id, null, null, null, null, "uvoz-1")));
        assertEquals(Set.of(izSerije), ids(new BankaFilter(id, null, null, StatusVarijante.NACRT, IzvorVarijante.CLAUDE, "uvoz-1")));
        assertEquals(Set.of(), ids(new BankaFilter(id, null, null, null, IzvorVarijante.RUCNO, "uvoz-1")));
        assertEquals(Set.of(rucna, klodova, izSerije), ids(new BankaFilter(id, null, null, null)));

        BankaPretragaStavka stavka = pretragaService.pretraga(new BankaFilter(id, null, null, null, null, "uvoz-1"), PRVIH_25)
                .getContent().get(0);
        assertEquals(IzvorVarijante.CLAUDE, stavka.izvor());
        assertEquals("uvoz-1", stavka.serija());
    }

    Set<Long> ids(BankaFilter f) {
        Set<Long> ids = new HashSet<>();
        pretragaService.pretraga(f, PRVIH_25).getContent().forEach(s -> ids.add(s.varijantaId()));
        return ids;
    }

    @Test
    void briseSeSamoPraznaGrupa() {
        GrupaDetails r = koren("Konverzije", null);
        GrupaDetails a = podgrupa(r, "A", 2);
        GrupaDetails b = podgrupa(r, "B", 2);
        GrupaDetails c = podgrupa(r, "C", 2);
        Long v = varijanta(a, IzvorVarijante.CLAUDE).id();

        SystemException saVarijantom = assertThrows(SystemException.class, () -> grupaService.obrisiPraznu(a.id()));
        assertEquals(409, saVarijantom.getCode());
        assertEquals(PRAZNA, saVarijantom.getMessage());
        assertTrue(postojiGrupa(a.id()));
        assertEquals("NACRT", status(v));

        SystemException saPodgrupom = assertThrows(SystemException.class, () -> grupaService.obrisiPraznu(r.id()));
        assertEquals(409, saPodgrupom.getCode());
        assertEquals(PRAZNA, saPodgrupom.getMessage());
        assertTrue(postojiGrupa(r.id()));

        grupaService.obrisiPraznu(b.id());
        assertFalse(postojiGrupa(b.id()));
        assertEquals(List.of("A:1", "C:2"), jdbc.queryForList(
                "select concat(naziv, ':', rb) from grupe_zadataka where roditelj_id = ? order by rb", String.class, r.id()));

        SystemException nema = assertThrows(SystemException.class, () -> grupaService.obrisiPraznu(b.id()));
        assertEquals(404, nema.getCode());
        assertEquals("Grupa nije pronađena.", nema.getMessage());
    }

    /**
     * Brisanje prazne grupe i dodavanje varijante u nju istovremeno: tačno jedno uspe, nikad varijanta bez grupe. Dva
     * kruga, u svakom drugi poziv prvi stane u red za ključ grupe (redosled odobravanja ključa je InnoDB-ov, pa test
     * prihvata oba ishoda u oba kruga).
     */
    @Test
    void obrisiPraznuIDodavanjeVarijanteIstovremeno() throws Exception {
        for (boolean brisanjePrvo : List.of(true, false)) {
            trkaBrisanjaIDodavanja(koren("Prazna " + brisanjePrvo, 2), brisanjePrvo);
        }
    }

    void trkaBrisanjaIDodavanja(GrupaDetails g, boolean brisanjePrvo) throws Exception {
        // treća transakcija drži red grupe, pa oba poziva stignu do svog ključa grupe pre nego što ijedan prođe
        CountDownLatch drziGrupu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        CompletableFuture<Void> drugaTransakcija = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transakcije).executeWithoutResult(tx -> {
                    jdbc.queryForList("select id from grupe_zadataka where id = ? for update", g.id());
                    drziGrupu.countDown();
                    await(pusti);
                }));
        assertTrue(drziGrupu.await(10, TimeUnit.SECONDS));

        Supplier<CompletableFuture<String>> brisi = () -> CompletableFuture.supplyAsync(() -> ishod(() -> {
            grupaService.obrisiPraznu(g.id());
            return "obrisana";
        }));
        Supplier<CompletableFuture<String>> dodaj = () -> CompletableFuture.supplyAsync(() ->
                ishod(() -> "varijanta " + varijanta(g, IzvorVarijante.CLAUDE).id()));
        CompletableFuture<String> brisanje;
        CompletableFuture<String> dodavanje;
        if (brisanjePrvo) {
            brisanje = brisi.get();
            Thread.sleep(500);
            dodavanje = dodaj.get();
        } else {
            dodavanje = dodaj.get();
            Thread.sleep(500);
            brisanje = brisi.get();
        }
        Thread.sleep(1500);
        assertFalse(brisanje.isDone(), "brisanje mora čekati ključ grupe");
        assertFalse(dodavanje.isDone(), "dodavanje mora čekati ključ grupe");

        pusti.countDown();
        drugaTransakcija.get(30, TimeUnit.SECONDS);
        String obrisana = brisanje.get(30, TimeUnit.SECONDS);
        String dodata = dodavanje.get(30, TimeUnit.SECONDS);

        int varijanti = jdbc.queryForObject("select count(*) from varijante where grupa_id = ?", Integer.class, g.id());
        if (postojiGrupa(g.id())) {
            assertEquals("409 " + PRAZNA, obrisana);
            assertTrue(dodata.startsWith("varijanta "), dodata);
            assertEquals(1, varijanti);
        } else {
            assertEquals("obrisana", obrisana);
            assertEquals("404 Grupa nije pronađena.", dodata);
            assertEquals(0, varijanti);   // nikad varijanta bez grupe
        }
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
