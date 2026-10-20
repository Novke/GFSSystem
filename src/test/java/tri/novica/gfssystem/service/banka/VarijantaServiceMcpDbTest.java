package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.banka.UpisVarijante.Radnja;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static tri.novica.gfssystem.utility.Promena.na;

/**
 * MCP metode {@link VarijantaService} nad pravom MySQL bazom (Flyway V1..V12, kao CI) sa lažnim {@link LatexRender} beanom
 * koji vraća SVG i PNG: serija i PNG iz istog rendera, delimična izmena na mestu samo za sopstveni neodobren nacrt (inače
 * kopija koja zamenjuje original), korak 3 MCP upisa nikad ne renderuje pod ključem (409 kad se sadržaj ili preambula
 * promene tokom rendera), brisanje samo sopstvenog nacrta, duplikat kao Klodov nacrt i slika bez upisa. Bez
 * {@code @Transactional}: svaki poziv servisa se zaista commit-uje, pa test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class VarijantaServiceMcpDbTest {

    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>".getBytes(StandardCharsets.UTF_8);
    /** PNG potpis plus oznaka dela, da se vidi koji PNG je od zadatka a koji od rešenja. */
    static final byte[] PNG_ZADATAK = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 'z'};
    static final byte[] PNG_RESENJE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 'r'};
    static final byte[] PRILOG_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final String RESENJE = "Rešenje: $11$";
    static final String IZMENJENA = "Varijanta ili preambula je u međuvremenu izmenjena; pokušaj ponovo.";
    static final String SAMO_SOPSTVENI = "Klod briše samo svoje neodobrene nacrte.";
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    static final List<String> SVG_I_PNG = List.of("svg", "png");
    static final List<String> SAMO_PNG = List.of("png");

    @Autowired VarijantaService service;
    @Autowired GrupaZadatakaService grupaService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijRepository medijRepository;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean LatexRender latexRender;
    @Value("${gfs.mediji.dir}") String medijiDir;

    Predmet predmet;
    GrupaDetails grupa;
    final List<String> mediji = new ArrayList<>();

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Banka MCP varijante DB test " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
        grupa = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "2 → 10", null, 2, null, List.of(BROJ_A)));
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(uspeh());
        when(latexRender.renderuj(anyList(), anyString(), anyList()))
                .thenAnswer(inv -> odgovor(inv.getArgument(0), inv.getArgument(1)));
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
        jdbc.update("update varijante set zamenjuje_id = null where grupa_id in " + grupePredmeta, id);
        jdbc.update("delete from varijante where grupa_id in " + grupePredmeta, id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        svg.forEach(medijService::obrisi);
        mediji.forEach(medijService::obrisi);
        predmetRepository.deleteById(id);
    }

    static RenderOdgovor uspeh() {
        return new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 7L, false);
    }

    /** Uspeh sa svakim traženim formatom; PNG rešenja se razlikuje od PNG-a zadatka. */
    static RenderOdgovor odgovor(List<String> formati, String tex) {
        Base64.Encoder b64 = Base64.getEncoder();
        String svg = formati.contains("svg") ? b64.encodeToString(SVG) : null;
        String png = formati.contains("png") ? b64.encodeToString(tex.contains(RESENJE) ? PNG_RESENJE : PNG_ZADATAK) : null;
        return new RenderOdgovor(true, svg, null, null, null, 7L, false, png);
    }

    VarijantaCmd cmd(String tekst, String resenje, IzvorVarijante izvor) {
        return new VarijantaCmd("V1", tekst, resenje, ODGOVOR_A, List.of(), izvor);
    }

    /** Klodov nacrt kroz MCP kreiranje (serija {@code uvoz-1}, bez PNG-a). */
    VarijantaDetails nacrt(String tekst, String resenje) {
        return service.kreiraj(grupa.id(), cmd(tekst, resenje, IzvorVarijante.CLAUDE), "uvoz-1", false).varijanta();
    }

    /** Novicina varijanta kroz REST put (RUCNO, sa rešenjem). */
    VarijantaDetails rucna(String tekst) {
        return service.kreiraj(grupa.id(), cmd(tekst, RESENJE, null));
    }

    static VarijantaPatch tekst(String tekst) {
        return new VarijantaPatch(null, na(tekst), null, null, null);
    }

    Map<String, Object> red(Long varijantaId) {
        return jdbc.queryForMap("select naziv, tekst, resenje, odgovori, status, izvor, serija, ikad_odobrena, zamenjuje_id, "
                + "render_status, render_svg_id, render_resenje_svg_id, render_hash, rb, izmenjeno from varijante where id = ?", varijantaId);
    }

    int brojVarijanti() {
        return jdbc.queryForObject("select count(*) from varijante where grupa_id = ?", Integer.class, grupa.id());
    }

    int brojRenderSvg() {
        return jdbc.queryForObject("select count(*) from mediji where vrsta = 'RENDER_SVG'", Integer.class);
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

    static SystemException greska(CompletableFuture<?> posao) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> posao.get(30, TimeUnit.SECONDS));
        return assertInstanceOf(SystemException.class, e.getCause());
    }

    @Test
    void kreirajSaSerijomIPng() {
        UpisVarijante u = service.kreiraj(grupa.id(), cmd("Pretvori $1011_2$ u dekadni.", RESENJE, IzvorVarijante.CLAUDE),
                "uvoz-1", true);

        assertEquals(Radnja.NOVA, u.radnja());
        VarijantaDetails d = u.varijanta();
        assertEquals(IzvorVarijante.CLAUDE, d.izvor());
        assertEquals("uvoz-1", d.serija());
        assertEquals(StatusVarijante.NACRT, d.status());
        assertEquals(RenderStatus.OK, d.renderStatus());
        assertFalse(d.ikadOdobrena());
        assertNull(d.zamenjuje());
        assertArrayEquals(PNG_ZADATAK, u.slike().zadatak());
        assertArrayEquals(PNG_RESENJE, u.slike().resenje());
        assertTrue(postojiFajl(d.svgId()));
        assertTrue(postojiFajl(d.resenjeSvgId()));
        Map<String, Object> red = red(d.id());
        assertEquals("CLAUDE", red.get("izvor"));
        assertEquals("uvoz-1", red.get("serija"));
        // jedno prevođenje po delu daje i SVG i PNG
        verify(latexRender, times(2)).renderuj(eq(SVG_I_PNG), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyString(), anyString(), anyList());

        // bez PNG-a: stari poziv sa jednim formatom, bez slike
        clearInvocations(latexRender);
        UpisVarijante bez = service.kreiraj(grupa.id(), cmd("Druga.", null, IzvorVarijante.CLAUDE), "uvoz-1", false);
        assertNull(bez.slike());
        assertEquals(2, bez.varijanta().rb());
        verify(latexRender, times(1)).renderuj(eq("svg"), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyList(), anyString(), anyList());

        // serija van obrasca: 400 pre rendera, nijedan red
        clearInvocations(latexRender);
        SystemException e = assertThrows(SystemException.class, () ->
                service.kreiraj(grupa.id(), cmd("Treća.", null, IzvorVarijante.CLAUDE), "Uvoz", true));
        assertEquals(400, e.getCode());
        assertEquals(Serija.PORUKA, e.getMessage());
        assertEquals(2, brojVarijanti());
        verifyNoInteractions(latexRender);
    }

    @Test
    void paloSamoResenjeNosiPngZadatka() {
        when(latexRender.renderuj(anyList(), contains("\\nepostoji"), anyList())).thenReturn(new RenderOdgovor(false, null,
                null, new RenderGreskaInfo(null, "Undefined control sequence."), null, 7L, false));

        UpisVarijante u = service.kreiraj(grupa.id(), cmd("Pretvori $1011_2$ u dekadni.", "Rešenje: \\nepostoji",
                IzvorVarijante.CLAUDE), "uvoz-1", true);

        assertEquals(RenderStatus.GRESKA, u.varijanta().renderStatus());
        assertEquals("resenje", u.varijanta().renderDeo());
        assertArrayEquals(PNG_ZADATAK, u.slike().zadatak());
        assertNull(u.slike().resenje());
        assertNull(u.varijanta().svgId(), "zadatak i rešenje se čuvaju samo iz istog uspelog rendera");
    }

    @Test
    void izmenaSopstvenogNacrtaJeNaMestu() {
        VarijantaDetails d = nacrt("Pretvori 1011.", RESENJE);
        clearInvocations(latexRender);

        UpisVarijante u = service.izmeniDelimicno(d.id(), tekst("Pretvori 1100."), "uvoz-2", true);

        assertEquals(Radnja.IZMENJENA, u.radnja());
        assertEquals(d.id(), u.varijanta().id());
        assertEquals("Pretvori 1100.", u.varijanta().tekst());
        assertEquals(RESENJE, u.varijanta().resenje(), "polje koje patch ne nosi ostaje");
        assertEquals("V1", u.varijanta().naziv());
        assertEquals(ODGOVOR_A, u.varijanta().odgovori());
        assertEquals(IzvorVarijante.CLAUDE, u.varijanta().izvor());
        assertEquals("uvoz-1", u.varijanta().serija(), "izmena na mestu ne menja seriju koja je nacrt napravila");
        assertNotEquals(d.svgId(), u.varijanta().svgId());
        assertArrayEquals(PNG_ZADATAK, u.slike().zadatak());
        assertArrayEquals(PNG_RESENJE, u.slike().resenje());
        assertEquals(1, brojVarijanti());
        assertEquals("Pretvori 1100.", red(d.id()).get("tekst"));
        verify(latexRender, times(2)).renderuj(eq(SVG_I_PNG), anyString(), anyList());

        // resenje: null briše rešenje
        UpisVarijante bezResenja = service.izmeniDelimicno(d.id(), new VarijantaPatch(null, null, na(null), null, null), "uvoz-2", false);
        assertEquals(Radnja.IZMENJENA, bezResenja.radnja());
        assertNull(bezResenja.slike());
        assertNull(bezResenja.varijanta().resenje());
        assertNull(bezResenja.varijanta().resenjeSvgId());
        assertNull(red(d.id()).get("resenje"));
        assertEquals("Pretvori 1100.", red(d.id()).get("tekst"));

        // samo naziv: keš rendera važi, bez rendera i bez slike; naziv: null ga briše
        clearInvocations(latexRender);
        UpisVarijante naziv = service.izmeniDelimicno(d.id(), new VarijantaPatch(na("Kol. 1, zad. 3"), null, null, null, null),
                "uvoz-2", true);
        assertEquals(Radnja.IZMENJENA, naziv.radnja());
        assertEquals("Kol. 1, zad. 3", naziv.varijanta().naziv());
        assertEquals(bezResenja.varijanta().svgId(), naziv.varijanta().svgId());
        assertNull(naziv.slike());
        verifyNoInteractions(latexRender);
        service.izmeniDelimicno(d.id(), new VarijantaPatch(na(null), null, null, null, null), "uvoz-2", false);
        assertNull(red(d.id()).get("naziv"));
        assertEquals(1, brojVarijanti());
    }

    @Test
    void priloziNullBriseSvePrilogeIOdgovoriSeProveravaju() {
        String png = medijService.sacuvajPrilog(new MockMultipartFile("fajl", "skica.png", "image/png", PRILOG_PNG)).id();
        mediji.add(png);
        VarijantaDetails d = service.kreiraj(grupa.id(), new VarijantaCmd("V1", "Sa skicom.", null, ODGOVOR_A,
                List.of(new PrilogCmd("skica", png)), IzvorVarijante.CLAUDE), "uvoz-1", false).varijanta();
        assertEquals(1, d.prilozi().size());

        UpisVarijante u = service.izmeniDelimicno(d.id(), new VarijantaPatch(null, na("Bez skice."), null, null, na(null)),
                "uvoz-1", false);

        assertEquals(List.of(), u.varijanta().prilozi());
        assertEquals(0, jdbc.queryForObject("select count(*) from varijanta_prilozi where varijanta_id = ?", Integer.class, d.id()));

        // odgovori van šeme grupe: 400 pre rendera, ništa upisano
        clearInvocations(latexRender);
        SystemException e = assertThrows(SystemException.class, () -> service.izmeniDelimicno(d.id(), new VarijantaPatch(null,
                na("Drugi tekst."), null, na(Map.of("b", new OdgovorPolja(1.0, null, null, null))), null), "uvoz-1", false));
        assertEquals(400, e.getCode());
        assertEquals("Bez skice.", red(d.id()).get("tekst"));
        verifyNoInteractions(latexRender);
    }

    @Test
    void izmenaRucneVarijantePraviKopiju() {
        VarijantaDetails r = rucna("Pretvori 1011.");
        service.odobri(r.id());
        Map<String, Object> pre = red(r.id());
        clearInvocations(latexRender);

        UpisVarijante u = service.izmeniDelimicno(r.id(), tekst("Pretvori 1100."), "uvoz-1", true);

        assertEquals(Radnja.KOPIJA, u.radnja());
        VarijantaDetails k = u.varijanta();
        assertNotEquals(r.id(), k.id());
        assertEquals(r.id(), k.zamenjuje());
        assertEquals(IzvorVarijante.CLAUDE, k.izvor());
        assertEquals(StatusVarijante.NACRT, k.status());
        assertFalse(k.ikadOdobrena());
        assertEquals("uvoz-1", k.serija());
        assertEquals(grupa.id(), k.grupaId());
        assertEquals(2, k.rb());
        assertEquals("Pretvori 1100.", k.tekst());
        assertEquals(RESENJE, k.resenje());
        assertEquals("V1", k.naziv());
        assertEquals(ODGOVOR_A, k.odgovori());
        assertEquals(RenderStatus.OK, k.renderStatus());
        assertNotEquals(r.svgId(), k.svgId());
        assertArrayEquals(PNG_ZADATAK, u.slike().zadatak());
        assertEquals(r.id(), ((Number) red(k.id()).get("zamenjuje_id")).longValue());
        // original je netaknut i aktivan
        assertEquals(pre, red(r.id()));
        assertEquals("AKTIVNA", pre.get("status"));
        assertEquals("Pretvori 1011.", pre.get("tekst"));
        assertEquals(2, brojVarijanti());
        verify(latexRender, times(2)).renderuj(eq(SVG_I_PNG), anyString(), anyList());
    }

    @Test
    void izmenaIkadOdobreneClaudeVarijantePraviKopiju() {
        VarijantaDetails d = nacrt("Pretvori 1011.", null);
        service.odobri(d.id());
        service.vratiUNacrt(d.id());   // opet NACRT i CLAUDE, ali je bila odobrena
        Map<String, Object> pre = red(d.id());

        // i sama izmena naziva pravi kopiju (kopija ima sopstveni render)
        UpisVarijante u = service.izmeniDelimicno(d.id(), new VarijantaPatch(na("Nov naziv"), null, null, null, null), "uvoz-2", false);

        assertEquals(Radnja.KOPIJA, u.radnja());
        assertNotEquals(d.id(), u.varijanta().id());
        assertEquals(d.id(), u.varijanta().zamenjuje());
        assertEquals("Nov naziv", u.varijanta().naziv());
        assertEquals("uvoz-2", u.varijanta().serija());
        assertEquals(RenderStatus.OK, u.varijanta().renderStatus());
        assertNotEquals(d.svgId(), u.varijanta().svgId());
        assertEquals(pre, red(d.id()));
        assertEquals("V1", pre.get("naziv"));
        assertEquals(2, brojVarijanti());
    }

    /**
     * Novica odobri Klodov nacrt dok Klodova izmena renderuje (između koraka 1 i 3): pod ključem varijanta više nije
     * sopstveni neodobren nacrt, pa korak 3 pravi kopiju; odobrenje ne menja heš sadržaja, pa se upisuje render iz koraka 2
     * (jedan poziv rendera: varijanta je bez rešenja).
     */
    @Test
    void izmenaPosleOdobrenjaUTokuRenderaPraviKopiju() throws Exception {
        VarijantaDetails d = nacrt("Pretvori 1011.", null);
        CountDownLatch uRenderu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        doAnswer(inv -> {
            uRenderu.countDown();
            await(pusti);
            return uspeh();
        }).when(latexRender).renderuj(eq("svg"), contains("Tokom rendera"), anyList());
        clearInvocations(latexRender);

        CompletableFuture<UpisVarijante> izmena = CompletableFuture.supplyAsync(() ->
                service.izmeniDelimicno(d.id(), tekst("Tokom rendera."), "uvoz-2", false));
        assertTrue(uRenderu.await(10, TimeUnit.SECONDS));
        assertEquals(StatusVarijante.AKTIVNA, service.odobri(d.id()).status());
        pusti.countDown();
        UpisVarijante u = izmena.get(30, TimeUnit.SECONDS);

        assertEquals(Radnja.KOPIJA, u.radnja());
        assertNotEquals(d.id(), u.varijanta().id());
        assertEquals(d.id(), u.varijanta().zamenjuje());
        assertEquals("Tokom rendera.", u.varijanta().tekst());
        assertEquals(StatusVarijante.NACRT, u.varijanta().status());
        assertEquals(RenderStatus.OK, u.varijanta().renderStatus());
        assertTrue(postojiFajl(u.varijanta().svgId()));
        Map<String, Object> original = red(d.id());
        assertEquals("AKTIVNA", original.get("status"));
        assertEquals("Pretvori 1011.", original.get("tekst"));
        assertEquals(d.svgId(), original.get("render_svg_id"));
        assertEquals(2, brojVarijanti());
        verify(latexRender, times(1)).renderuj(anyString(), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyList(), anyString(), anyList());
    }

    /**
     * Klodov patch menja samo tekst; dok Klodov render traje, Novica kroz REST izmenu doda rešenje (polje van patch-a), pa
     * se sadržaj spojen pod ključem razlikuje od renderovanog: 409, bez rendera pod ključem, ništa upisano.
     */
    @Test
    void izmenaTekstaTokomRenderaJe409BezRenderaPodKljucem() throws Exception {
        VarijantaDetails d = nacrt("Stari tekst.", null);
        List<Thread> niti = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch uRenderu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        doAnswer(inv -> {
            niti.add(Thread.currentThread());
            if (inv.<String>getArgument(1).contains("Klodov tekst")) {
                uRenderu.countDown();
                await(pusti);
            }
            return uspeh();
        }).when(latexRender).renderuj(eq("svg"), anyString(), anyList());
        AtomicReference<Thread> klodovaNit = new AtomicReference<>();

        CompletableFuture<UpisVarijante> izmena = CompletableFuture.supplyAsync(() -> {
            klodovaNit.set(Thread.currentThread());
            return service.izmeniDelimicno(d.id(), tekst("Klodov tekst."), "uvoz-2", false);
        });
        assertTrue(uRenderu.await(10, TimeUnit.SECONDS));
        // Novica u UI-ju čuva celu varijantu sa rešenjem dok Klodov render traje
        VarijantaDetails novicina = service.izmeni(d.id(), new VarijantaCmd("V1", "Stari tekst.", RESENJE, ODGOVOR_A, List.of(), null));
        pusti.countDown();
        SystemException e = greska(izmena);

        assertEquals(409, e.getCode());
        assertEquals(IZMENJENA, e.getMessage());
        assertEquals(1, niti.stream().filter(n -> n == klodovaNit.get()).count(), "Klodova nit renderuje samo u koraku 2");
        Map<String, Object> red = red(d.id());
        assertEquals("Stari tekst.", red.get("tekst"));
        assertEquals(RESENJE, red.get("resenje"));
        assertEquals(novicina.svgId(), red.get("render_svg_id"));
        assertEquals(1, brojVarijanti());
    }

    @Test
    void promenaPreambuleTokomKreiranjaJe409() throws Exception {
        int svgPre = brojRenderSvg();
        CountDownLatch uRenderu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        doAnswer(inv -> {
            uRenderu.countDown();
            await(pusti);
            return odgovor(inv.getArgument(0), inv.getArgument(1));
        }).when(latexRender).renderuj(anyList(), contains("Nova tokom preambule"), anyList());
        clearInvocations(latexRender);

        CompletableFuture<UpisVarijante> nova = CompletableFuture.supplyAsync(() -> service.kreiraj(grupa.id(),
                cmd("Nova tokom preambule.", null, IzvorVarijante.CLAUDE), "uvoz-1", true));
        assertTrue(uRenderu.await(10, TimeUnit.SECONDS));
        jdbc.update("update predmeti set latex_preambula = ? where id = ?", "\\usepackage{amsmath}\n\\usepackage{xcolor}", predmet.getId());
        pusti.countDown();
        SystemException e = greska(nova);

        assertEquals(409, e.getCode());
        assertEquals(IZMENJENA, e.getMessage());
        assertEquals(0, brojVarijanti());
        assertEquals(svgPre, brojRenderSvg());
        verify(latexRender, times(1)).renderuj(anyList(), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyString(), anyString(), anyList());
    }

    @Test
    void odobriIzmeniObrisiJe409() {
        VarijantaDetails d = nacrt("Pretvori 1011.", null);
        service.odobri(d.id());
        UpisVarijante kopija = service.izmeniDelimicno(d.id(), tekst("Pretvori 1100."), "uvoz-2", false);
        assertEquals(Radnja.KOPIJA, kopija.radnja());

        SystemException odobrena = assertThrows(SystemException.class, () -> service.obrisiSopstveniNacrt(d.id()));
        assertEquals(409, odobrena.getCode());
        assertEquals(SAMO_SOPSTVENI, odobrena.getMessage());
        assertEquals("AKTIVNA", red(d.id()).get("status"));

        VarijantaDetails r = rucna("Ručna.");   // NACRT, ali RUCNO
        SystemException rucna = assertThrows(SystemException.class, () -> service.obrisiSopstveniNacrt(r.id()));
        assertEquals(409, rucna.getCode());
        assertEquals(SAMO_SOPSTVENI, rucna.getMessage());
        assertEquals(3, brojVarijanti());

        // kopija je Klodov neodobren nacrt: briše se
        service.obrisiSopstveniNacrt(kopija.varijanta().id());
        assertEquals(2, brojVarijanti());
    }

    @Test
    void brisanjeSopstvenogNacrta() {
        VarijantaDetails d = service.kreiraj(grupa.id(), cmd("Pretvori 1011.", RESENJE, IzvorVarijante.CLAUDE), "uvoz-1", false)
                .varijanta();
        assertTrue(postojiFajl(d.svgId()));
        assertTrue(postojiFajl(d.resenjeSvgId()));

        service.obrisiSopstveniNacrt(d.id());

        assertEquals(0, jdbc.queryForObject("select count(*) from varijante where id = ?", Integer.class, d.id()));
        assertFalse(medijRepository.existsById(d.svgId()));
        assertFalse(medijRepository.existsById(d.resenjeSvgId()));
        assertFalse(postojiFajl(d.svgId()));
        assertFalse(postojiFajl(d.resenjeSvgId()));
        SystemException nema = assertThrows(SystemException.class, () -> service.obrisiSopstveniNacrt(d.id()));
        assertEquals(404, nema.getCode());
        assertEquals("Varijanta nije pronađena.", nema.getMessage());
    }

    @Test
    void mcpKreiranjeIDuplikatSuUvekClaude() {
        // MCP kreiraj ne čita cmd.izvor(): i bez izvora i sa RUCNO nastaje Klodov nacrt serije
        for (IzvorVarijante izvor : Arrays.asList(null, IzvorVarijante.RUCNO)) {
            VarijantaDetails d = service.kreiraj(grupa.id(), cmd("Pretvori 1011.", null, izvor), "uvoz-1", false).varijanta();
            assertEquals(IzvorVarijante.CLAUDE, d.izvor(), "izvor komande " + izvor);
            assertEquals(StatusVarijante.NACRT, d.status());
            assertEquals(Map.of("izvor", "CLAUDE", "serija", "uvoz-1"),
                    jdbc.queryForMap("select izvor, serija from varijante where id = ?", d.id()));
        }
        // REST kreiraj i dalje uzima izvor iz komande, bez njega RUCNO
        assertEquals(IzvorVarijante.RUCNO, service.kreiraj(grupa.id(), cmd("Ručna.", null, null)).izvor());
        assertEquals(IzvorVarijante.CLAUDE, service.kreiraj(grupa.id(), cmd("Iz komande.", null, IzvorVarijante.CLAUDE)).izvor());
        // MCP duplikat Novicine varijante je Klodov, REST duplikat čuva izvor originala
        VarijantaDetails r = rucna("Pretvori 1100.");
        VarijantaDetails klodov = service.dupliraj(r.id(), "uvoz-1");
        assertEquals(IzvorVarijante.CLAUDE, klodov.izvor());
        assertEquals("uvoz-1", klodov.serija());
        assertEquals(IzvorVarijante.RUCNO, service.dupliraj(r.id()).izvor());
        assertEquals(7, brojVarijanti());
    }

    @Test
    void duplirajKaoClaude() {
        VarijantaDetails r = rucna("Pretvori 1011.");
        service.odobri(r.id());

        VarijantaDetails k = service.dupliraj(r.id(), "uvoz-1");

        assertNotEquals(r.id(), k.id());
        assertEquals(IzvorVarijante.CLAUDE, k.izvor());
        assertEquals("uvoz-1", k.serija());
        assertEquals(StatusVarijante.NACRT, k.status());
        assertFalse(k.ikadOdobrena());
        assertNull(k.zamenjuje());
        assertEquals("Pretvori 1011.", k.tekst());
        assertEquals(RESENJE, k.resenje());
        assertEquals(RenderStatus.OK, k.renderStatus());
        assertNotEquals(r.svgId(), k.svgId());
        assertEquals(Map.of("izvor", "CLAUDE", "serija", "uvoz-1"),
                jdbc.queryForMap("select izvor, serija from varijante where id = ?", k.id()));
        assertEquals("AKTIVNA", red(r.id()).get("status"));

        // postojeći duplikat čuva izvor originala, bez serije
        VarijantaDetails stari = service.dupliraj(r.id());
        assertEquals(IzvorVarijante.RUCNO, stari.izvor());
        assertNull(stari.serija());

        SystemException e = assertThrows(SystemException.class, () -> service.dupliraj(r.id(), "Uvoz"));
        assertEquals(400, e.getCode());
        assertEquals(Serija.PORUKA, e.getMessage());
        assertEquals(3, brojVarijanti());
    }

    @Test
    void duplikatIzvoraIzmenjenogTokomRenderaJe409() throws Exception {
        VarijantaDetails r = rucna("Pretvori 1011.");
        CountDownLatch uRenderu = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        AtomicReference<Thread> klodovaNit = new AtomicReference<>();
        doAnswer(inv -> {
            if (Thread.currentThread() == klodovaNit.get()) {
                uRenderu.countDown();
                await(pusti);
            }
            return uspeh();
        }).when(latexRender).renderuj(eq("svg"), anyString(), anyList());

        CompletableFuture<VarijantaDetails> duplikat = CompletableFuture.supplyAsync(() -> {
            klodovaNit.set(Thread.currentThread());
            return service.dupliraj(r.id(), "uvoz-1");
        });
        assertTrue(uRenderu.await(10, TimeUnit.SECONDS));
        service.izmeni(r.id(), new VarijantaCmd("V1", "Novicin tekst.", RESENJE, ODGOVOR_A, List.of(), null));
        pusti.countDown();
        SystemException e = greska(duplikat);

        assertEquals(409, e.getCode());
        assertEquals(IZMENJENA, e.getMessage());
        assertEquals(1, brojVarijanti());
        assertEquals("Novicin tekst.", red(r.id()).get("tekst"));
    }

    @Test
    void slikaNeUpisuje() {
        VarijantaDetails d = nacrt("Pretvori 1011.", RESENJE);
        Map<String, Object> pre = red(d.id());
        int svgPre = brojRenderSvg();
        clearInvocations(latexRender);

        SlikeRendera s = service.slika(d.id());

        assertArrayEquals(PNG_ZADATAK, s.zadatak());
        assertArrayEquals(PNG_RESENJE, s.resenje());
        verify(latexRender, times(2)).renderuj(eq(SAMO_PNG), anyString(), anyList());
        verify(latexRender, never()).renderuj(anyString(), anyString(), anyList());
        assertEquals(pre, red(d.id()));
        assertEquals(d.svgId(), pre.get("render_svg_id"));
        assertEquals(svgPre, brojRenderSvg());

        SystemException nema = assertThrows(SystemException.class, () -> service.slika(-1L));
        assertEquals(404, nema.getCode());
    }

    @Test
    void probaSaSlikomTraziSamoPngINistaNeUpisuje() {
        int svgPre = brojRenderSvg();

        ProbaSaSlikom p = service.probaSaSlikom(new RenderProbaCmd(predmet.getId(), "Pretvori 1011.", RESENJE, List.of()));

        assertTrue(p.info().ok());
        assertNull(p.info().svg());
        assertNull(p.info().resenjeSvg());
        assertArrayEquals(PNG_ZADATAK, p.slike().zadatak());
        assertArrayEquals(PNG_RESENJE, p.slike().resenje());
        verify(latexRender, times(2)).renderuj(eq(SAMO_PNG), anyString(), anyList());
        assertEquals(0, brojVarijanti());
        assertEquals(svgPre, brojRenderSvg());

        // ista pravila kao proba: bez predmeta 400
        SystemException e = assertThrows(SystemException.class, () ->
                service.probaSaSlikom(new RenderProbaCmd(null, "Pretvori 1011.", null, List.of())));
        assertEquals(400, e.getCode());
    }
}
