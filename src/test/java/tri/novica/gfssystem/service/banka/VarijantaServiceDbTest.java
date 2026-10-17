package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link VarijantaService} nad pravom MySQL bazom (Flyway V1..V11, kao CI) sa lažnim {@link LatexRender} beanom koji
 * vraća fiksni SVG: upis reda, priloga i SVG medija (red i fajl), keš po hešu, brisanje SVG medija i fajlova,
 * zaključavanje (grupa pa varijanta, drugi upis čeka prvi) i render cele banke u zasebnim transakcijama. Bez
 * {@code @Transactional}: svaki poziv servisa se zaista commit-uje, pa test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class VarijantaServiceDbTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>".getBytes(StandardCharsets.UTF_8);
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));

    @Autowired VarijantaService service;
    @Autowired GrupaZadatakaService grupaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijRepository medijRepository;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transakcije;
    @Autowired EntityManagerFactory emf;
    @MockitoBean LatexRender latexRender;
    @Value("${gfs.mediji.dir}") String medijiDir;

    Predmet predmet;
    GrupaDetails grupa;
    String png;
    final List<String> mediji = new ArrayList<>();

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Banka varijante DB test " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
        grupa = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "2 → 10", null, 2, null, List.of(BROJ_A)));
        png = medijService.sacuvajPrilog(new MockMultipartFile("fajl", "skica.png", "image/png", PNG)).id();
        mediji.add(png);
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(uspeh());
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
        jdbc.update("delete from varijanta_prilozi where varijanta_id in (select v.id from varijante v "
                + "join grupe_zadataka g on g.id = v.grupa_id where g.predmet_id = ?)", id);
        jdbc.update("delete from varijante where grupa_id in " + grupePredmeta, id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        svg.forEach(medijService::obrisi);
        mediji.forEach(medijService::obrisi);
        predmetRepository.deleteById(id);
    }

    static RenderOdgovor uspeh() {
        return new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 7L);
    }

    VarijantaCmd cmd(String tekst) {
        return new VarijantaCmd("V1", tekst, "$11$", ODGOVOR_A, List.of(new PrilogCmd("skica", png)), null);
    }

    Map<String, Object> red(Long varijantaId) {
        return jdbc.queryForMap("select tekst, status, render_status, render_svg_id, render_resenje_svg_id, render_hash, "
                + "render_greska, odgovori, rb from varijante where id = ?", varijantaId);
    }

    boolean postojiFajl(String medijId) {
        return Files.exists(Path.of(medijiDir).resolve(medijId));
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void kreirajUpisujeRedPrilogISvgMedijeSaFajlovima() throws Exception {
        VarijantaDetails d = service.kreiraj(grupa.id(), cmd("Pretvori $1011_2$ u dekadni.\n\\includegraphics{skica}"));

        Map<String, Object> red = red(d.id());
        assertEquals("NACRT", red.get("status"));
        assertEquals("OK", red.get("render_status"));
        assertEquals(d.svgId(), red.get("render_svg_id"));
        assertEquals(d.resenjeSvgId(), red.get("render_resenje_svg_id"));
        assertEquals(64, ((String) red.get("render_hash")).length());
        assertNull(red.get("render_greska"));
        assertEquals("{\"a\":{\"broj\":11.0}}", red.get("odgovori"));
        assertEquals(1, red.get("rb"));
        assertEquals(List.of(Map.of("ime", "skica", "medij_id", png)),
                jdbc.queryForList("select ime, medij_id from varijanta_prilozi where varijanta_id = ?", d.id()));
        for (String[] svg : List.of(new String[]{d.svgId(), "zadatak-" + d.id() + ".svg"},
                new String[]{d.resenjeSvgId(), "resenje-" + d.id() + ".svg"})) {
            assertEquals(Map.of("vrsta", "RENDER_SVG", "mime", "image/svg+xml", "naziv", svg[1]),
                    jdbc.queryForMap("select vrsta, mime, naziv from mediji where id = ?", svg[0]));
            assertArrayEquals(SVG, Files.readAllBytes(Path.of(medijiDir).resolve(svg[0])));
        }
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RenderPrilog>> prilozi = ArgumentCaptor.forClass(List.class);
        verify(latexRender, times(2)).renderuj(eq("svg"), anyString(), prilozi.capture());
        assertEquals(new RenderPrilog("skica", "png", Base64.getEncoder().encodeToString(PNG)), prilozi.getValue().get(0));

        VarijantaDetails druga = service.kreiraj(grupa.id(), cmd("Druga."));
        assertEquals(2, druga.rb());
        assertEquals(List.of(d.id(), druga.id()), service.lista(grupa.id(), null).stream().map(VarijantaInfo::id).toList());
        assertEquals(List.of(), service.lista(grupa.id(), StatusVarijante.AKTIVNA));
        service.odobri(druga.id());
        List<VarijantaInfo> aktivne = service.lista(grupa.id(), StatusVarijante.AKTIVNA);
        assertEquals(List.of(druga.id()), aktivne.stream().map(VarijantaInfo::id).toList());
        assertEquals(druga.svgId(), aktivne.get(0).svgId());
        VarijantaDetails procitana = service.detalji(d.id());
        assertEquals(List.of(new GrupaPutanja(grupa.id(), "2 → 10")), procitana.putanja());
        assertEquals("image/png", procitana.prilozi().get(0).medij().mime());
        assertEquals(ODGOVOR_A, procitana.odgovori());
    }

    @Test
    void izmenaIstogSadrzajaZadrzavaSvgINeZoveRenderANovSadrzajMenjaIBriseStari() {
        VarijantaDetails d = service.kreiraj(grupa.id(), cmd("Pretvori 1011."));
        clearInvocations(latexRender);

        VarijantaDetails ista = service.izmeni(d.id(), new VarijantaCmd("V2", "Pretvori 1011.", "$11$",
                Map.of("a", new OdgovorPolja(12.0, null, null, null)), List.of(new PrilogCmd("skica", png)), null));

        verify(latexRender, never()).renderuj(any(), any(), any());
        assertEquals(d.svgId(), ista.svgId());
        assertEquals(d.svgId(), red(d.id()).get("render_svg_id"));
        assertEquals("V2", jdbc.queryForObject("select naziv from varijante where id = ?", String.class, d.id()));

        VarijantaDetails nova = service.izmeni(d.id(), cmd("Pretvori 1100."));

        verify(latexRender, times(2)).renderuj(eq("svg"), anyString(), anyList());
        assertNotEquals(d.svgId(), nova.svgId());
        assertNotEquals(d.resenjeSvgId(), nova.resenjeSvgId());
        assertEquals(nova.svgId(), red(d.id()).get("render_svg_id"));
        assertFalse(medijRepository.existsById(d.svgId()));
        assertFalse(medijRepository.existsById(d.resenjeSvgId()));
        assertFalse(postojiFajl(d.svgId()));
        assertFalse(postojiFajl(d.resenjeSvgId()));
        assertTrue(postojiFajl(nova.svgId()));
    }

    @Test
    void brisanjeBriseSvgMedijeIFajloveADuplikatZadrzavaSvojeIPrilog() {
        VarijantaDetails d = service.kreiraj(grupa.id(), cmd("Pretvori 1011."));
        VarijantaDetails k = service.dupliraj(d.id());
        assertEquals(StatusVarijante.NACRT, k.status());
        assertNotEquals(d.svgId(), k.svgId());
        assertEquals(List.of(Map.of("ime", "skica", "medij_id", png)),
                jdbc.queryForList("select ime, medij_id from varijanta_prilozi where varijanta_id = ?", k.id()));

        service.obrisi(d.id());

        assertEquals(0, jdbc.queryForObject("select count(*) from varijante where id = ?", Long.class, d.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from varijanta_prilozi where varijanta_id = ?", Long.class, d.id()));
        assertFalse(medijRepository.existsById(d.svgId()));
        assertFalse(medijRepository.existsById(d.resenjeSvgId()));
        assertFalse(postojiFajl(d.svgId()));
        assertFalse(postojiFajl(d.resenjeSvgId()));
        // duplikat ima svoj SVG i deli medij priloga, koji ostaje
        assertTrue(medijRepository.existsById(k.svgId()));
        assertTrue(postojiFajl(k.svgId()));
        assertTrue(medijRepository.existsById(png));
        assertEquals(k.svgId(), service.detalji(k.id()).svgId());
    }

    @Test
    void dveIstovremeneIzmeneZavrsavajuBezGreskeIPobedjujePoslednja() throws Exception {
        VarijantaDetails d = service.kreiraj(grupa.id(), cmd("Početni tekst."));
        CountDownLatch uRenderu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        when(latexRender.renderuj(eq("svg"), contains("Prvi upis"), anyList())).thenAnswer(inv -> {
            uRenderu.countDown();
            await(pusti);
            return uspeh();
        });

        CompletableFuture<VarijantaDetails> prvi = CompletableFuture.supplyAsync(() -> service.izmeni(d.id(), cmd("Prvi upis.")));
        assertTrue(uRenderu.await(10, TimeUnit.SECONDS));
        CompletableFuture<VarijantaDetails> drugi = CompletableFuture.supplyAsync(() -> service.izmeni(d.id(), cmd("Drugi upis.")));
        Thread.sleep(1000);
        assertFalse(drugi.isDone(), "drugi upis čeka ključ dok prvi renderuje");
        assertEquals("Početni tekst.", red(d.id()).get("tekst"));

        pusti.countDown();
        VarijantaDetails p = prvi.get(30, TimeUnit.SECONDS);
        VarijantaDetails r = drugi.get(30, TimeUnit.SECONDS);

        assertEquals("Prvi upis.", p.tekst());
        assertEquals("Drugi upis.", r.tekst());
        Map<String, Object> red = red(d.id());
        assertEquals("Drugi upis.", red.get("tekst"));
        assertEquals("OK", red.get("render_status"));
        assertEquals(r.svgId(), red.get("render_svg_id"));
        assertNotEquals(p.svgId(), r.svgId());
        assertFalse(medijRepository.existsById(p.svgId()), "SVG prvog upisa briše drugi, posle svog rendera");
        assertFalse(medijRepository.existsById(d.svgId()));
    }

    @Test
    void izmenaCekaKljucGrupeIDotleNeDrziRedVarijante() throws Exception {
        VarijantaDetails d = service.kreiraj(grupa.id(), cmd("Pre."));
        CountDownLatch drziGrupu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        CompletableFuture<Void> drugaTransakcija = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transakcije).executeWithoutResult(tx -> {
                    jdbc.queryForList("select id from grupe_zadataka where id = ? for update", grupa.id());
                    drziGrupu.countDown();
                    await(pusti);
                }));
        assertTrue(drziGrupu.await(10, TimeUnit.SECONDS));

        CompletableFuture<VarijantaDetails> izmena = CompletableFuture.supplyAsync(() -> service.izmeni(d.id(), cmd("Posle.")));
        CompletableFuture<VarijantaDetails> nova = CompletableFuture.supplyAsync(() -> service.kreiraj(grupa.id(), cmd("Nova.")));
        Thread.sleep(1500);
        assertFalse(izmena.isDone(), "izmena mora čekati ključ grupe");
        assertFalse(nova.isDone(), "nova varijanta mora čekati ključ grupe");
        // red varijante je slobodan dok izmena čeka grupu: servis ga zaključava tek posle grupe
        assertEquals(List.of(d.id()), new TransactionTemplate(transakcije).execute(tx ->
                jdbc.queryForList("select id from varijante where id = ? for update nowait", Long.class, d.id())));

        pusti.countDown();
        drugaTransakcija.get(30, TimeUnit.SECONDS);
        assertEquals("Posle.", izmena.get(30, TimeUnit.SECONDS).tekst());
        assertEquals(2, nova.get(30, TimeUnit.SECONDS).rb());
        assertEquals("Posle.", red(d.id()).get("tekst"));
    }

    @Test
    void renderujSveRenderujeSamoZastareleSvakuUSvojojTransakciji() {
        VarijantaDetails a = service.kreiraj(grupa.id(), cmd("Prva."));
        VarijantaDetails b = service.kreiraj(grupa.id(), cmd("Druga."));
        String hashB = (String) red(b.id()).get("render_hash");
        jdbc.update("update varijante set render_hash = null where id = ?", b.id());
        clearInvocations(latexRender);

        assertEquals(new RenderujSveInfo(1, 0), service.renderujSve(predmet.getId()));

        verify(latexRender, times(2)).renderuj(eq("svg"), anyString(), anyList());
        verify(latexRender).renderuj(eq("svg"), contains("Druga."), anyList());
        Map<String, Object> red = red(b.id());
        assertEquals(hashB, red.get("render_hash"));
        assertEquals("OK", red.get("render_status"));
        assertNotEquals(b.svgId(), red.get("render_svg_id"));
        assertFalse(medijRepository.existsById(b.svgId()));
        assertEquals(a.svgId(), red(a.id()).get("render_svg_id"));
        assertEquals(new RenderujSveInfo(0, 0), service.renderujSve(predmet.getId()));
        assertEquals(RenderStatus.OK, service.detalji(b.id()).renderStatus());
    }

    /**
     * Kao u HTTP zahtevu (open-in-view: jedan EntityManager za ceo zahtev, preko svih transakcija): izmena druge varijante
     * dok se prva renderuje mora biti viđena posle ključa, a ne pregažena starim stanjem pročitanim pri izboru kandidata.
     */
    @Test
    void renderujSveUZahtevuSaOtvorenimEntityManageromCitaSvezeStanjePosleKljuca() {
        VarijantaDetails a = service.kreiraj(grupa.id(), cmd("Prva."));
        VarijantaDetails b = service.kreiraj(grupa.id(), cmd("Druga."));
        jdbc.update("update varijante set render_hash = null where id in (?, ?)", a.id(), b.id());
        when(latexRender.renderuj(eq("svg"), contains("Prva."), anyList())).thenAnswer(inv -> {
            jdbc.update("update varijante set tekst = 'Druga, izmenjena.' where id = ?", b.id());
            return uspeh();
        });
        clearInvocations(latexRender);

        EntityManager zahtev = emf.createEntityManager();
        TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(zahtev));
        try {
            assertEquals(new RenderujSveInfo(2, 0), service.renderujSve(predmet.getId()));
        } finally {
            TransactionSynchronizationManager.unbindResource(emf);
            zahtev.close();
        }

        assertEquals("Druga, izmenjena.", red(b.id()).get("tekst"));
        verify(latexRender).renderuj(eq("svg"), contains("Druga, izmenjena."), anyList());
        verify(latexRender, never()).renderuj(eq("svg"), contains("Druga."), anyList());
    }
}
