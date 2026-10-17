package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.PlatformTransactionManager;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.validation.BankaPP;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link VarijantaService} bez baze: repozitorijumi su lažni nad listama {@link #grupe}, {@link #varijante} i mapom
 * {@link #mediji}; {@link RenderVarijante} je pravi, a lažan je samo HTTP klijent {@link LatexRender}. Statusi posle
 * uspelog i neuspelog rendera, keš po hešu, prelazi statusa, pravila grupe, odgovora i priloga, semafor probe, red
 * zaključavanja i render cele banke.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VarijantaServiceTest {

    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 17, 12, 0);
    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final Clock CLOCK = Clock.fixed(SADA.atZone(ZONA).toInstant(), ZONA);
    static final Polje BROJ_A = BankaPP.normalizujPolja(
            List.of(new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null))).get(0);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    static final String PNG = "5b3e4d1c-8f90-4e3c-9d4b-2c3d4e5f6071";
    static final String PDF = "6c4f5e2d-9a01-4f4d-8e5c-3d4e5f607182";
    static final String GIF = "7d5a6f3e-0b12-4a5e-9f6d-4e5f60718293";
    static final String WEBP = "9f7c8b5a-2d34-4c7a-9b8f-60718293a4b5";
    static final String STARI_SVG = "8e6b7a4f-1c23-4b6f-8a7e-5f6071829304";
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
    static final int POMAK = LatexDokument.zaTelefon(null, "").pomakLinija();

    @Mock GrupaZadatakaRepository grupaRepository;
    @Mock VarijantaRepository varijantaRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock MedijRepository medijRepository;
    @Mock MedijService medijService;
    @Mock LatexRender latexRender;
    @Mock GrupaZadatakaService grupaZadatakaService;
    @Mock PlatformTransactionManager transakcije;
    @Mock EntityManager em;

    VarijantaService service;
    Predmet predmet;
    GrupaZadataka grupa;
    /** "Tabele": lažni repozitorijumi čitaju i pišu ove kolekcije. */
    final List<GrupaZadataka> grupe = new ArrayList<>();
    final List<Varijanta> varijante = new CopyOnWriteArrayList<>();
    final Map<String, Medij> mediji = new ConcurrentHashMap<>();
    final AtomicLong sledeciId = new AtomicLong(100);
    final AtomicInteger brojSvg = new AtomicInteger();

    @BeforeEach
    void setUp() {
        service = new VarijantaService(grupaRepository, varijantaRepository, predmetRepository, medijRepository, medijService,
                new RenderVarijante(latexRender, medijService), grupaZadatakaService, transakcije, em, CLOCK);
        predmet = new Predmet();
        predmet.setId(7L);
        predmet.setNaziv("Uvod u računarstvo");
        when(predmetRepository.findById(7L)).thenReturn(Optional.of(predmet));
        when(predmetRepository.existsById(7L)).thenReturn(true);
        grupa = grupa("2 → 10", 2, BROJ_A);

        when(grupaRepository.findById(anyLong())).thenAnswer(inv -> nadjiGrupu(inv.getArgument(0)));
        when(grupaRepository.findByIdForUpdate(anyLong())).thenAnswer(inv -> nadjiGrupu(inv.getArgument(0)));
        when(grupaRepository.countByRoditeljId(anyLong())).thenAnswer(inv -> grupe.stream()
                .filter(g -> g.getRoditelj() != null && g.getRoditelj().getId().equals(inv.getArgument(0))).count());

        when(varijantaRepository.findGrupaId(anyLong())).thenAnswer(inv -> nadji(inv.getArgument(0)).map(v -> v.getGrupa().getId()));
        when(varijantaRepository.findById(anyLong())).thenAnswer(inv -> nadji(inv.getArgument(0)));
        when(varijantaRepository.findByIdForUpdate(anyLong())).thenAnswer(inv -> nadji(inv.getArgument(0)));
        when(varijantaRepository.maxRb(anyLong())).thenAnswer(inv -> varijante.stream()
                .filter(v -> v.getGrupa().getId().equals(inv.getArgument(0))).mapToInt(Varijanta::getRb).max().orElse(0));
        when(varijantaRepository.save(any())).thenAnswer(inv -> {
            Varijanta v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(sledeciId.getAndIncrement());
                varijante.add(v);
            }
            return v;
        });
        doAnswer(inv -> varijante.remove(inv.<Varijanta>getArgument(0))).when(varijantaRepository).delete(any());
        when(varijantaRepository.lista(anyLong(), any())).thenAnswer(inv -> varijante.stream()
                .filter(v -> v.getGrupa().getId().equals(inv.getArgument(0)))
                .filter(v -> inv.getArgument(1) == null || v.getStatus() == inv.getArgument(1))
                .sorted(Comparator.comparingInt(Varijanta::getRb)).toList());
        when(varijantaRepository.svePredmeta(anyLong())).thenAnswer(inv -> new ArrayList<>(varijante));

        medij(PNG, "image/png", VrstaMedija.SLIKA);
        medij(PDF, "application/pdf", VrstaMedija.PRILOG_PDF);
        medij(GIF, "image/gif", VrstaMedija.SLIKA);
        medij(WEBP, "image/webp", VrstaMedija.SLIKA);
        medij(STARI_SVG, "image/svg+xml", VrstaMedija.RENDER_SVG);
        when(medijRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(mediji.get(inv.<String>getArgument(0))));
        when(medijRepository.getReferenceById(anyString())).thenAnswer(inv -> mediji.get(inv.<String>getArgument(0)));
        when(medijService.ucitaj(anyString())).thenAnswer(inv -> {
            Medij m = mediji.get(inv.<String>getArgument(0));
            return new MedijService.Fajl(m.getMime(), 3, new ByteArrayResource(new byte[]{1, 2, 3}), m.getVrsta());
        });
        when(medijService.sacuvajRender(any(), anyString())).thenAnswer(inv -> {
            Medij m = medij("svg-" + brojSvg.incrementAndGet(), "image/svg+xml", VrstaMedija.RENDER_SVG);
            m.setNaziv(inv.getArgument(1));
            return new MedijInfo(m.getId(), m.getNaziv(), m.getMime(), m.getVelicina());
        });
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(uspeh());
    }

    // ---------------------------------------------------------------- pomoćno

    GrupaZadataka grupa(String naziv, Integer tezina, Polje... polja) {
        GrupaZadataka g = new GrupaZadataka();
        g.setId(sledeciId.getAndIncrement());
        g.setPredmet(predmet);
        g.setNamena(Namena.PREDAVANJE);
        g.setNaziv(naziv);
        g.setRb(grupe.size() + 1);
        g.setTezina(tezina);
        g.setPolja(PoljaJson.uJson(List.of(polja)));
        g.setKreirano(SADA.minusDays(3));
        g.setIzmenjeno(SADA.minusDays(3));
        grupe.add(g);
        return g;
    }

    Medij medij(String id, String mime, VrstaMedija vrsta) {
        Medij m = new Medij();
        m.setId(id);
        m.setNaziv(id + ".bin");
        m.setMime(mime);
        m.setVelicina(3);
        m.setVrsta(vrsta);
        m.setKreirano(SADA);
        mediji.put(id, m);
        return m;
    }

    Optional<GrupaZadataka> nadjiGrupu(Long id) {
        return grupe.stream().filter(g -> g.getId().equals(id)).findFirst();
    }

    Optional<Varijanta> nadji(Long id) {
        return varijante.stream().filter(v -> v.getId().equals(id)).findFirst();
    }

    Varijanta varijanta(Long id) {
        return nadji(id).orElseThrow();
    }

    static RenderOdgovor uspeh() {
        return new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 9L);
    }

    static RenderOdgovor neuspeh(int linijaUTelu, String poruka) {
        int linija = POMAK + linijaUTelu;
        return new RenderOdgovor(false, null, null, new RenderGreskaInfo(linija, poruka),
                "(./dokument.tex\n./dokument.tex:" + linija + ": " + poruka + "\n", null);
    }

    static VarijantaCmd cmd(String tekst, String resenje, List<PrilogCmd> prilozi) {
        return new VarijantaCmd("V1", tekst, resenje, ODGOVOR_A, prilozi, null);
    }

    static VarijantaCmd cmd(String tekst) {
        return cmd(tekst, "$11$", List.of(new PrilogCmd("skica", PNG)));
    }

    static void ocekujGresku(int kod, String poruka, org.junit.jupiter.api.function.Executable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv);
        assertEquals(kod, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    // ---------------------------------------------------------------- kreiranje i render

    @Test
    void kreirajRenderujeOdmahIVracaNacrtSaSvgomZadatkaIResenja() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori $1011_2$ u dekadni."));

        assertEquals(StatusVarijante.NACRT, d.status());
        assertEquals(IzvorVarijante.RUCNO, d.izvor());
        assertEquals(RenderStatus.OK, d.renderStatus());
        assertEquals("svg-1", d.svgId());
        assertEquals("svg-2", d.resenjeSvgId());
        assertNull(d.renderGreska());
        assertNull(d.renderDeo());
        assertEquals(1, d.rb());
        assertEquals("V1", d.naziv());
        assertEquals(grupa.getId(), d.grupaId());
        assertEquals(ODGOVOR_A, d.odgovori());
        assertEquals(List.of(BROJ_A), d.polja());
        assertEquals(List.of(new PrilogInfo("skica", new MedijInfo(PNG, PNG + ".bin", "image/png", 3))), d.prilozi());
        assertEquals(SADA, d.kreirano());
        assertEquals(SADA, d.izmenjeno());
        verify(medijService).sacuvajRender(SVG, "zadatak-" + d.id() + ".svg");
        verify(medijService).sacuvajRender(SVG, "resenje-" + d.id() + ".svg");
        Varijanta v = varijanta(d.id());
        assertEquals(64, v.getRenderHash().length());
        assertEquals("{\"a\":{\"broj\":11.0}}", v.getOdgovori());

        VarijantaDetails druga = service.kreiraj(grupa.getId(),
                new VarijantaCmd(null, "Drugi tekst.", "  ", ODGOVOR_A, null, IzvorVarijante.CLAUDE));
        assertEquals(2, druga.rb());
        assertEquals(IzvorVarijante.CLAUDE, druga.izvor());
        assertEquals(StatusVarijante.NACRT, druga.status());
        assertNull(druga.resenje());
        assertNull(druga.resenjeSvgId());
        assertEquals(List.of(), druga.prilozi());

        // grupa se nikad ne upisuje, predmet se ne zaključava
        assertEquals(SADA.minusDays(3), grupa.getIzmenjeno());
        verify(grupaRepository, never()).save(any());
        verify(predmetRepository, never()).findByIdForUpdate(any());
        assertEquals(List.of(d.id(), druga.id()), service.lista(grupa.getId(), null).stream().map(VarijantaInfo::id).toList());
        assertEquals(List.of(), service.lista(grupa.getId(), StatusVarijante.AKTIVNA));
        VarijantaInfo info = service.lista(grupa.getId(), StatusVarijante.NACRT).get(0);
        assertEquals(new VarijantaInfo(d.id(), grupa.getId(), 1, "V1", StatusVarijante.NACRT, IzvorVarijante.RUCNO,
                RenderStatus.OK, "svg-1", "svg-2", SADA), info);
    }

    @Test
    void neuspeoRenderCuvaGreskuSaDelomILinijomBezSvga() {
        when(latexRender.renderuj(eq("svg"), contains("\\foo"), anyList())).thenReturn(neuspeh(2, "Undefined control sequence."));

        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Prva\nDruga \\foo"));

        assertEquals(StatusVarijante.NACRT, d.status());
        assertEquals(RenderStatus.GRESKA, d.renderStatus());
        assertEquals(new RenderGreskaInfo(2, "Undefined control sequence."), d.renderGreska());
        assertEquals("tekst", d.renderDeo());
        assertNull(d.svgId());
        assertNull(d.resenjeSvgId());
        assertEquals("tekst:2: Undefined control sequence.", varijanta(d.id()).getRenderGreska());
        assertNotNull(varijanta(d.id()).getRenderHash());
        verify(medijService, never()).sacuvajRender(any(), anyString());
        assertEquals(new RenderGreskaInfo(2, "Undefined control sequence."), service.detalji(d.id()).renderGreska());
    }

    @Test
    void greskaURresenjuNeCuvaNiSvgZadatka() {
        when(latexRender.renderuj(eq("svg"), contains("\\bar"), anyList())).thenReturn(neuspeh(1, "Undefined control sequence."));

        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Ispravan tekst.", "\\bar", List.of()));

        assertEquals(RenderStatus.GRESKA, d.renderStatus());
        assertEquals("resenje", d.renderDeo());
        assertEquals(new RenderGreskaInfo(1, "Undefined control sequence."), d.renderGreska());
        assertNull(d.svgId());
        verify(medijService, never()).sacuvajRender(any(), anyString());
    }

    @Test
    void nedostupanRenderSePropagiraKao503BezCuvanjaSvga() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenThrow(new RenderNedostupan());

        ocekujGresku(503, "Render servis nije dostupan.", () -> service.kreiraj(grupa.getId(), cmd("Tekst.")));
        verify(medijService, never()).sacuvajRender(any(), anyString());
    }

    // ---------------------------------------------------------------- izmena i keš

    @Test
    void izmenaIstogSadrzajaSaUspelimRenderomNeZoveRender() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        clearInvocations(latexRender, medijService);

        VarijantaCmd isti = new VarijantaCmd("Nov naziv", "Pretvori 1011.", "$11$",
                Map.of("a", new OdgovorPolja(12.0, null, null, null)), List.of(new PrilogCmd("skica", PNG)), null);
        VarijantaDetails i = service.izmeni(d.id(), isti);

        verifyNoInteractions(latexRender);
        verify(medijService, never()).sacuvajRender(any(), anyString());
        verify(medijService, never()).obrisi(anyString());
        assertEquals(d.svgId(), i.svgId());
        assertEquals(d.resenjeSvgId(), i.resenjeSvgId());
        assertEquals("Nov naziv", i.naziv());
        assertEquals(Map.of("a", new OdgovorPolja(12.0, null, null, null)), i.odgovori());
    }

    @Test
    void istiHesSaNeuspelimRenderomSePonovoRenderuje() {
        when(latexRender.renderuj(eq("svg"), contains("Tekst."), anyList()))
                .thenReturn(neuspeh(1, "Prekoračeno vreme kompajliranja (20 s)."), uspeh());
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Tekst.", null, List.of()));
        assertEquals(RenderStatus.GRESKA, d.renderStatus());

        VarijantaDetails i = service.izmeni(d.id(), cmd("Tekst.", null, List.of()));

        assertEquals(RenderStatus.OK, i.renderStatus());
        assertNull(i.renderGreska());
        assertNull(varijanta(d.id()).getRenderGreska());
        verify(latexRender, times(2)).renderuj(anyString(), anyString(), anyList());
    }

    @Test
    void aktivnaSaNeuspelimRenderomPadaUNacrtIZadrzavaStariSvg() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        service.odobri(d.id());
        when(latexRender.renderuj(eq("svg"), contains("\\foo"), anyList())).thenReturn(neuspeh(1, "Undefined control sequence."));

        VarijantaDetails i = service.izmeni(d.id(), cmd("\\foo 1011."));

        assertEquals(StatusVarijante.NACRT, i.status());
        assertEquals(RenderStatus.GRESKA, i.renderStatus());
        assertEquals(new RenderGreskaInfo(1, "Undefined control sequence."), i.renderGreska());
        assertEquals(d.svgId(), i.svgId());   // stari SVG se briše tek pri uspešnom novom renderu
        verify(medijService, never()).obrisi(anyString());
        assertEquals("\\foo 1011.", i.tekst());
    }

    @Test
    void aktivnaSaUspelimRenderomOstajeAktivnaIStariSvgSeBriseTekPosleNovog() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        service.odobri(d.id());
        clearInvocations(medijService);

        VarijantaDetails i = service.izmeni(d.id(), cmd("Pretvori 1100."));

        assertEquals(StatusVarijante.AKTIVNA, i.status());
        assertEquals(RenderStatus.OK, i.renderStatus());
        assertEquals("svg-3", i.svgId());
        assertEquals("svg-4", i.resenjeSvgId());
        InOrder redosled = inOrder(medijService);
        redosled.verify(medijService, times(2)).sacuvajRender(any(), anyString());
        redosled.verify(medijService).obrisi("svg-1");
        redosled.verify(medijService).obrisi("svg-2");
    }

    @Test
    void izmenaZamenjujePrilogePoImenu() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("\\includegraphics{skica}", null,
                List.of(new PrilogCmd("skica", PNG), new PrilogCmd("tabela", PDF))));
        Varijanta v = varijanta(d.id());
        VarijantaPrilog skica = v.getPrilozi().stream().filter(p -> p.getIme().equals("skica")).findFirst().orElseThrow();

        VarijantaDetails i = service.izmeni(d.id(), cmd("\\includegraphics{skica}", null,
                List.of(new PrilogCmd("skica", PDF), new PrilogCmd("graf", PNG))));

        assertEquals(List.of("graf:" + PNG, "skica:" + PDF),
                i.prilozi().stream().map(p -> p.ime() + ":" + p.medij().id()).toList());
        assertTrue(v.getPrilozi().contains(skica), "prilog istog imena ostaje isti red (jedinstven ključ varijanta+ime)");
        assertEquals(PDF, skica.getMedij().getId());
        assertEquals(2, v.getPrilozi().size());
        verify(latexRender, times(2)).renderuj(anyString(), anyString(), anyList());
    }

    // ---------------------------------------------------------------- statusi

    @Test
    void odobravanjeTraziUspeoRenderIPotpuneOdgovore() {
        when(latexRender.renderuj(eq("svg"), contains("\\foo"), anyList())).thenReturn(neuspeh(1, "Undefined control sequence."));
        VarijantaDetails losa = service.kreiraj(grupa.getId(), cmd("\\foo"));
        ocekujGresku(409, "Varijanta se odobrava tek kad je render uspeo.", () -> service.odobri(losa.id()));
        assertEquals(StatusVarijante.NACRT, varijanta(losa.id()).getStatus());

        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        varijanta(d.id()).setOdgovori("{}");   // npr. polje dodato u šemu posle unosa
        ocekujGresku(400, "Odgovor za polje 'a' nedostaje.", () -> service.odobri(d.id()));

        varijanta(d.id()).setOdgovori("{\"a\":{\"broj\":11.0}}");
        assertEquals(StatusVarijante.AKTIVNA, service.odobri(d.id()).status());
        assertEquals(StatusVarijante.AKTIVNA, service.odobri(d.id()).status());
        assertEquals(StatusVarijante.ARHIVIRANA, service.arhiviraj(d.id()).status());
        assertEquals(StatusVarijante.NACRT, service.vratiUNacrt(d.id()).status());
        assertEquals(StatusVarijante.ARHIVIRANA, service.arhiviraj(d.id()).status());
        assertEquals(StatusVarijante.AKTIVNA, service.odobri(d.id()).status());
        ocekujGresku(404, "Varijanta nije pronađena.", () -> service.odobri(999L));
    }

    @Test
    void prisilanRenderSePonavljaIKadJeHesIsti() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        clearInvocations(latexRender);

        VarijantaDetails r = service.render(d.id());

        verify(latexRender, times(2)).renderuj(anyString(), anyString(), anyList());
        assertEquals("svg-3", r.svgId());
        assertEquals(RenderStatus.OK, r.renderStatus());
    }

    @Test
    void duplikatJeNacrtSaIstimSadrzajemPrilozimaISopstvenimRenderom() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        service.odobri(d.id());
        clearInvocations(latexRender);

        VarijantaDetails k = service.dupliraj(d.id());

        assertNotEquals(d.id(), k.id());
        assertEquals(StatusVarijante.NACRT, k.status());
        assertEquals(2, k.rb());
        assertEquals(d.tekst(), k.tekst());
        assertEquals(d.resenje(), k.resenje());
        assertEquals(d.odgovori(), k.odgovori());
        assertEquals(d.naziv(), k.naziv());
        assertEquals(d.prilozi(), k.prilozi());
        assertEquals(RenderStatus.OK, k.renderStatus());
        assertEquals("svg-3", k.svgId());
        assertEquals("svg-4", k.resenjeSvgId());
        verify(latexRender, times(2)).renderuj(anyString(), anyString(), anyList());
        assertEquals(StatusVarijante.AKTIVNA, varijanta(d.id()).getStatus());
        assertEquals("svg-1", varijanta(d.id()).getRenderSvg().getId());
    }

    @Test
    void brisanjeBriseVarijantuPaSvgMedije() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        assertFalse(service.koriscenoNaCasu(d.id()));

        service.obrisi(d.id());

        assertTrue(varijante.isEmpty());
        InOrder redosled = inOrder(varijantaRepository, medijService);
        redosled.verify(varijantaRepository).delete(any());
        redosled.verify(varijantaRepository).flush();
        redosled.verify(medijService).obrisi("svg-1");
        redosled.verify(medijService).obrisi("svg-2");
        verify(medijService, never()).obrisi(PNG);   // medij priloga može deliti duplikat
        ocekujGresku(404, "Varijanta nije pronađena.", () -> service.obrisi(d.id()));
    }

    // ---------------------------------------------------------------- pravila grupe, odgovora i priloga

    @Test
    void grupaSaPodgrupamaIliBezTezineNePrimaVarijante() {
        GrupaZadataka dete = grupa("Podgrupa", 2);
        dete.setRoditelj(grupa);
        ocekujGresku(409, "Grupa sa podgrupama ne može imati varijante.", () -> service.kreiraj(grupa.getId(), cmd("Tekst.")));

        GrupaZadataka bezTezine = grupa("Bez težine", null, BROJ_A);
        ocekujGresku(409, "Težina je obavezna za grupu sa varijantama.", () -> service.kreiraj(bezTezine.getId(), cmd("Tekst.")));
        ocekujGresku(404, "Grupa nije pronađena.", () -> service.kreiraj(999L, cmd("Tekst.")));
        assertTrue(varijante.isEmpty());
        verifyNoInteractions(latexRender);
    }

    @Test
    void odgovoriSeProveravajuNadSemomGrupe() {
        ocekujGresku(400, "Nepoznato polje 'x'.", () -> service.kreiraj(grupa.getId(), new VarijantaCmd(null, "Tekst.", null,
                Map.of("a", new OdgovorPolja(11.0, null, null, null), "x", new OdgovorPolja(1.0, null, null, null)), null, null)));
        ocekujGresku(400, "Odgovor za polje 'a' nedostaje.",
                () -> service.kreiraj(grupa.getId(), new VarijantaCmd(null, "Tekst.", null, null, null, null)));
        ocekujGresku(400, "Odgovor za polje 'a' mora biti broj.", () -> service.kreiraj(grupa.getId(),
                new VarijantaCmd(null, "Tekst.", null, Map.of("a", new OdgovorPolja(null, List.of("11"), null, null)), null, null)));
        ocekujGresku(400, "Tekst zadatka mora imati 1 do 20 000 znakova.",
                () -> service.kreiraj(grupa.getId(), new VarijantaCmd(null, " ", null, ODGOVOR_A, null, null)));
        ocekujGresku(400, "Komanda \\write18 nije dozvoljena.",
                () -> service.kreiraj(grupa.getId(), cmd("\\write18{rm -rf /}")));
        assertTrue(varijante.isEmpty());
        verifyNoInteractions(latexRender);

        // grupa bez polja prima prazne ili izostavljene odgovore
        GrupaZadataka papir = grupa("Na papiru", 3);
        assertEquals(Map.of(), service.kreiraj(papir.getId(), new VarijantaCmd(null, "Tekst.", null, null, null, null)).odgovori());
        assertEquals(Map.of(), service.kreiraj(papir.getId(), new VarijantaCmd(null, "Tekst.", null, Map.of(), null, null)).odgovori());
    }

    @Test
    void odgovoriKojiNeStajuUKolonuSu400() {
        String dugo = "ž".repeat(200);
        Map<String, OdgovorPolja> mnogo = new LinkedHashMap<>();
        IntStream.range(0, 200).forEach(i -> mnogo.put("k" + i, new OdgovorPolja(null, List.of(dugo), null, null)));

        ocekujGresku(400, "Odgovori su preveliki.",
                () -> service.kreiraj(grupa.getId(), new VarijantaCmd(null, "Tekst.", null, mnogo, null, null)));
        verifyNoInteractions(latexRender);
    }

    @Test
    void priloziSeProveravajuPreZakljucavanja() {
        ocekujGresku(400, "Ime priloga 'dokument' je rezervisano.",
                () -> service.kreiraj(grupa.getId(), cmd("Tekst.", null, List.of(new PrilogCmd("dokument", PNG)))));
        ocekujGresku(400, "Ime priloga: 1-60 malih slova, cifara, '_' ili '-'.",
                () -> service.kreiraj(grupa.getId(), cmd("Tekst.", null, List.of(new PrilogCmd("Skica.png", PNG)))));
        ocekujGresku(400, "Ime priloga 'skica' se ponavlja.", () -> service.kreiraj(grupa.getId(),
                cmd("Tekst.", null, List.of(new PrilogCmd("skica", PNG), new PrilogCmd("skica", PDF)))));
        ocekujGresku(400, "Prilog 'skica' nije pronađen.", () -> service.kreiraj(grupa.getId(),
                cmd("Tekst.", null, List.of(new PrilogCmd("skica", "0a1b2c3d-0000-4000-8000-000000000000")))));
        ocekujGresku(400, "Prilog 'skica' nije pronađen.",
                () -> service.kreiraj(grupa.getId(), cmd("Tekst.", null, List.of(new PrilogCmd("skica", null)))));
        for (String nedozvoljen : List.of(GIF, WEBP, STARI_SVG)) {
            ocekujGresku(400, "Prilog mora biti PNG, JPEG ili PDF.",
                    () -> service.kreiraj(grupa.getId(), cmd("Tekst.", null, List.of(new PrilogCmd("slika", nedozvoljen)))));
        }
        List<PrilogCmd> jedanaest = IntStream.range(0, 11).mapToObj(i -> new PrilogCmd("p" + i, PNG)).toList();
        ocekujGresku(400, "Najviše 10 priloga.", () -> service.kreiraj(grupa.getId(), cmd("Tekst.", null, jedanaest)));
        verify(grupaRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(latexRender);

        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Tekst.", null,
                List.of(new PrilogCmd("tabela", PDF), new PrilogCmd("deset_10-x", PNG))));
        assertEquals(List.of("deset_10-x", "tabela"), d.prilozi().stream().map(PrilogInfo::ime).toList());
    }

    // ---------------------------------------------------------------- zaključavanje

    @Test
    void zakljucavaGrupuPaVarijantuINikadPredmet() {
        VarijantaDetails d = service.kreiraj(grupa.getId(), cmd("Pretvori 1011."));
        clearInvocations(grupaRepository, varijantaRepository);

        service.izmeni(d.id(), cmd("Pretvori 1100."));
        service.odobri(d.id());

        InOrder redosled = inOrder(varijantaRepository, grupaRepository);
        for (int i = 0; i < 2; i++) {
            redosled.verify(varijantaRepository).findGrupaId(d.id());
            redosled.verify(grupaRepository).findByIdForUpdate(grupa.getId());
            redosled.verify(varijantaRepository).findByIdForUpdate(d.id());
        }
        verify(varijantaRepository, never()).findById(anyLong());   // varijanta se ne čita pre ključa
        verify(predmetRepository, never()).findByIdForUpdate(any());
        verify(grupaRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- proba

    @Test
    void probaVracaSvgKaoTekstINistaNeCuva() {
        RenderProbaInfo p = service.proba(new RenderProbaCmd(7L, "Pretvori 1011.", "$11$", List.of(new PrilogCmd("skica", PNG))));

        assertTrue(p.ok());
        assertEquals(new String(SVG, StandardCharsets.UTF_8), p.svg());
        assertEquals(new String(SVG, StandardCharsets.UTF_8), p.resenjeSvg());
        assertNull(p.greska());
        assertNull(p.deo());
        verify(varijantaRepository, never()).save(any());
        verify(medijService, never()).sacuvajRender(any(), anyString());
        verify(grupaRepository, never()).findByIdForUpdate(anyLong());

        when(latexRender.renderuj(eq("svg"), contains("\\foo"), anyList())).thenReturn(neuspeh(1, "Undefined control sequence."));
        RenderProbaInfo losa = service.proba(new RenderProbaCmd(7L, "Tekst.", "\\foo", null));
        assertFalse(losa.ok());
        assertEquals("resenje", losa.deo());
        assertEquals(new RenderGreskaInfo(1, "Undefined control sequence."), losa.greska());
        assertNull(losa.svg());

        ocekujGresku(404, "Predmet nije pronađen.", () -> service.proba(new RenderProbaCmd(8L, "Tekst.", null, null)));
        ocekujGresku(400, "Predmet je obavezan.", () -> service.proba(new RenderProbaCmd(null, "Tekst.", null, null)));
        ocekujGresku(400, "Ime priloga 'dokument' je rezervisano.",
                () -> service.proba(new RenderProbaCmd(7L, "Tekst.", null, List.of(new PrilogCmd("dokument", PDF)))));
        ocekujGresku(400, "Komanda \\input nije dozvoljena.", () -> service.proba(new RenderProbaCmd(7L, "\\input{x}", null, null)));
    }

    @Test
    void probaVraca429KadSuCetiriProbeUToku() throws Exception {
        CountDownLatch uRenderu = new CountDownLatch(4);
        CountDownLatch pusti = new CountDownLatch(1);
        when(latexRender.renderuj(eq("svg"), contains("Spora"), anyList())).thenAnswer(inv -> {
            uRenderu.countDown();
            assertTrue(pusti.await(10, TimeUnit.SECONDS));
            return uspeh();
        });
        RenderProbaCmd spora = new RenderProbaCmd(7L, "Spora proba.", null, null);
        ExecutorService niti = Executors.newFixedThreadPool(4);
        try {
            List<Future<RenderProbaInfo>> probe = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                probe.add(niti.submit(() -> service.proba(spora)));
            }
            assertTrue(uRenderu.await(10, TimeUnit.SECONDS));

            ocekujGresku(429, "Previše proba u isto vreme.", () -> service.proba(new RenderProbaCmd(7L, "Brza.", null, null)));

            pusti.countDown();
            for (Future<RenderProbaInfo> p : probe) {
                assertTrue(p.get(10, TimeUnit.SECONDS).ok());
            }
        } finally {
            niti.shutdownNow();
        }
        // semafor je oslobođen i posle greške
        when(latexRender.renderuj(eq("svg"), contains("Pada"), anyList())).thenThrow(new RenderNedostupan());
        for (int i = 0; i < 5; i++) {
            ocekujGresku(503, "Render servis nije dostupan.", () -> service.proba(new RenderProbaCmd(7L, "Pada.", null, null)));
        }
        assertTrue(service.proba(new RenderProbaCmd(7L, "Brza.", null, null)).ok());
    }

    // ---------------------------------------------------------------- render cele banke

    @Test
    void renderujSveRenderujeZastareleINeuspeleAPreostaloBrojiSamoZastarele() {
        VarijantaDetails sveza = service.kreiraj(grupa.getId(), cmd("Sveža."));
        VarijantaDetails zastarela = service.kreiraj(grupa.getId(), cmd("Zastarela."));
        when(latexRender.renderuj(eq("svg"), contains("\\foo"), anyList())).thenReturn(neuspeh(1, "Undefined control sequence."));
        VarijantaDetails neuspela = service.kreiraj(grupa.getId(), cmd("\\foo"));
        varijanta(zastarela.id()).setRenderHash(null);   // npr. posle izmene preambule
        clearInvocations(latexRender);

        RenderujSveInfo r = service.renderujSve(7L);

        assertEquals(new RenderujSveInfo(2, 0), r);
        verify(latexRender, never()).renderuj(anyString(), contains("Sveža."), anyList());
        verify(latexRender, times(1)).renderuj(anyString(), contains("Zastarela."), anyList());
        verify(latexRender, times(1)).renderuj(anyString(), contains("$11$"), anyList());   // rešenje samo zastarele: neuspeloj pada tekst
        verify(latexRender, times(1)).renderuj(anyString(), contains("\\foo"), anyList());
        assertNotNull(varijanta(zastarela.id()).getRenderHash());
        assertEquals(RenderStatus.OK, varijanta(sveza.id()).getRenderStatus());
        assertEquals(RenderStatus.GRESKA, varijanta(neuspela.id()).getRenderStatus());
        // svaka varijanta u svojoj transakciji, sveže pročitana posle ključa
        verify(em, times(2)).clear();
        ocekujGresku(404, "Predmet nije pronađen.", () -> service.renderujSve(8L));
    }

    @Test
    void renderujSveObradiNajvise200PoPozivu() {
        GrupaZadataka papir = grupa("Na papiru", 3);
        for (int i = 0; i < 201; i++) {
            service.kreiraj(papir.getId(), new VarijantaCmd(null, "Tekst " + i + ".", null, null, null, null));
        }
        varijante.forEach(v -> v.setRenderHash(null));
        clearInvocations(latexRender);

        assertEquals(new RenderujSveInfo(200, 1), service.renderujSve(7L));
        verify(latexRender, times(200)).renderuj(anyString(), anyString(), anyList());
        assertEquals(new RenderujSveInfo(1, 0), service.renderujSve(7L));
        assertEquals(new RenderujSveInfo(0, 0), service.renderujSve(7L));
    }
}
