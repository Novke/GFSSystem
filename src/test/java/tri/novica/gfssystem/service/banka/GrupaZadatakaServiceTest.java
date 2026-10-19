package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.IskljucenaTemaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.validation.BankaPP;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link GrupaZadatakaService} bez baze: repozitorijumi su lažni i čitaju/pišu liste {@link #grupe} i {@link #varijante}.
 * Pravila stabla (namena, naziv među braćom, dubina, list ili unutrašnji, premeštanje), promena šeme polja nad
 * varijantama, brojači i redosled zaključavanja.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrupaZadatakaServiceTest {

    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 17, 12, 0);
    static final ZoneId ZONA = ZoneId.of("Europe/Belgrade");
    static final Clock CLOCK = Clock.fixed(SADA.atZone(ZONA).toInstant(), ZONA);
    static final String SVG = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    static final String SVG_RESENJA = "4a2d3c0b-7e8f-4d2b-8c3a-1b2c3d4e5f60";
    static final String PRILOG = "5b3e4d1c-8f90-4e3c-9d4b-2c3d4e5f6071";

    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Polje TEKST_A = new Polje("a", "a) binarni u dekadni", TipPolja.TEKST, null, null, null, null, null, null);
    static final Polje TEKST_B = new Polje("b", "b) heks u binarni", TipPolja.TEKST, null, null, null, null, null, null);
    static final Polje IZBOR_C = new Polje("c", "Koji automat?", TipPolja.JEDAN_TACAN, null, null, null, null, null,
            List.of(new PoljeOpcija(1L, "A"), new PoljeOpcija(2L, "B"), new PoljeOpcija(3L, "C")));
    static final String ODGOVOR_A = "{\"a\":{\"broj\":45.0}}";

    @Mock GrupaZadatakaRepository grupaRepository;
    @Mock VarijantaRepository varijantaRepository;
    @Mock PredmetRepository predmetRepository;
    @Mock IskljucenaTemaRepository temaRepository;
    @Mock MedijService medijService;
    @Mock EntityManager em;

    GrupaZadatakaService service;
    Predmet predmet;
    Predmet drugiPredmet;
    /** "Tabele": lažni repozitorijumi čitaju i pišu ove liste. */
    final List<GrupaZadataka> grupe = new ArrayList<>();
    final List<Varijanta> varijante = new ArrayList<>();
    final AtomicLong sledeciId = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        service = new GrupaZadatakaService(grupaRepository, varijantaRepository, predmetRepository, temaRepository,
                medijService, em, CLOCK);
        predmet = predmet(7L, "Uvod u računarstvo");
        drugiPredmet = predmet(8L, "Statika");

        when(grupaRepository.findPredmetId(anyLong()))
                .thenAnswer(inv -> nadji(inv.getArgument(0)).map(g -> g.getPredmet().getId()));
        when(grupaRepository.findById(anyLong())).thenAnswer(inv -> nadji(inv.getArgument(0)));
        when(grupaRepository.findByIdForUpdate(anyLong())).thenAnswer(inv -> nadji(inv.getArgument(0)));
        when(grupaRepository.findByRoditeljIdOrderByRb(anyLong()))
                .thenAnswer(inv -> po(grupe.stream().filter(g -> roditeljId(g) != null && roditeljId(g).equals(inv.getArgument(0)))));
        when(grupaRepository.findByPredmetIdAndNamenaAndRoditeljIsNullOrderByRb(anyLong(), any()))
                .thenAnswer(inv -> po(grupe.stream().filter(g -> g.getRoditelj() == null
                        && g.getPredmet().getId().equals(inv.getArgument(0)) && g.getNamena() == inv.getArgument(1))));
        when(grupaRepository.findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(anyLong(), any()))
                .thenAnswer(inv -> po(grupe.stream().filter(g -> g.getPredmet().getId().equals(inv.getArgument(0))
                        && g.getNamena() == inv.getArgument(1))));
        when(grupaRepository.countByRoditeljId(anyLong()))
                .thenAnswer(inv -> grupe.stream().filter(g -> roditeljId(g) != null && roditeljId(g).equals(inv.getArgument(0))).count());
        when(grupaRepository.save(any())).thenAnswer(inv -> {
            GrupaZadataka g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId(sledeciId.getAndIncrement());
                grupe.add(g);
            }
            return g;
        });
        doAnswer(inv -> grupe.remove(inv.<GrupaZadataka>getArgument(0))).when(grupaRepository).delete(any());

        when(varijantaRepository.countByGrupaId(anyLong())).thenAnswer(inv -> (long) varijanteGrupe(inv.getArgument(0)).size());
        when(varijantaRepository.findByGrupaIdForUpdate(anyLong())).thenAnswer(inv -> varijanteGrupe(inv.getArgument(0)));
        when(varijantaRepository.brojaciPoGrupi(anyCollection())).thenAnswer(inv -> brojaci(inv.getArgument(0)));
        doAnswer(inv -> {
            inv.<Iterable<Varijanta>>getArgument(0).forEach(varijante::remove);
            return null;
        }).when(varijantaRepository).deleteAll(anyIterable());
    }

    // ---------------------------------------------------------------- pomoćno

    Predmet predmet(Long id, String naziv) {
        Predmet p = new Predmet();
        p.setId(id);
        p.setNaziv(naziv);
        when(predmetRepository.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        when(predmetRepository.existsById(id)).thenReturn(true);
        return p;
    }

    Optional<GrupaZadataka> nadji(Long id) {
        return grupe.stream().filter(g -> g.getId().equals(id)).findFirst();
    }

    static Long roditeljId(GrupaZadataka g) {
        return g.getRoditelj() == null ? null : g.getRoditelj().getId();
    }

    static List<GrupaZadataka> po(Stream<GrupaZadataka> s) {
        return s.sorted(Comparator.comparingInt(GrupaZadataka::getRb)).collect(Collectors.toCollection(ArrayList::new));
    }

    List<Varijanta> varijanteGrupe(Long grupaId) {
        return varijante.stream().filter(v -> v.getGrupa().getId().equals(grupaId)).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Kao pravi upit: redovi {@code [grupaId, status, renderStatus, broj]}. */
    List<Object[]> brojaci(Collection<Long> ids) {
        Map<List<Object>, Long> zbir = new LinkedHashMap<>();
        for (Varijanta v : varijante) {
            if (ids.contains(v.getGrupa().getId())) {
                zbir.merge(List.of(v.getGrupa().getId(), v.getStatus(), v.getRenderStatus()), 1L, Long::sum);
            }
        }
        return zbir.entrySet().stream()
                .map(e -> new Object[]{e.getKey().get(0), e.getKey().get(1), e.getKey().get(2), e.getValue()}).toList();
    }

    GrupaZadataka koren(Predmet p, Namena namena, String naziv, int rb) {
        GrupaZadataka g = new GrupaZadataka();
        g.setId(sledeciId.getAndIncrement());
        g.setPredmet(p);
        g.setNamena(namena);
        g.setNaziv(naziv);
        g.setRb(rb);
        g.setKreirano(SADA.minusDays(3));
        g.setIzmenjeno(SADA.minusDays(3));
        grupe.add(g);
        return g;
    }

    GrupaZadataka koren(String naziv, int rb) {
        return koren(predmet, Namena.PREDAVANJE, naziv, rb);
    }

    GrupaZadataka podgrupa(GrupaZadataka roditelj, String naziv, int rb) {
        GrupaZadataka g = koren(roditelj.getPredmet(), roditelj.getNamena(), naziv, rb);
        g.setRoditelj(roditelj);
        return g;
    }

    /** Lanac od {@code n} grupa (koren pa po jedna podgrupa); vraća najdublju. */
    GrupaZadataka lanac(int n) {
        GrupaZadataka g = koren("Nivo 1", 1);
        for (int i = 2; i <= n; i++) {
            g = podgrupa(g, "Nivo " + i, 1);
        }
        return g;
    }

    Varijanta varijanta(GrupaZadataka g, StatusVarijante status, RenderStatus render, String odgovori) {
        Varijanta v = new Varijanta();
        v.setId(sledeciId.getAndIncrement());
        v.setGrupa(g);
        v.setRb(varijanteGrupe(g.getId()).size() + 1);
        v.setTekst("Pretvori 1011 u dekadni.");
        v.setOdgovori(odgovori);
        v.setStatus(status);
        v.setIzvor(IzvorVarijante.RUCNO);
        v.setRenderStatus(render);
        v.setRenderGreska(render == RenderStatus.GRESKA ? "linija 3: Undefined control sequence" : null);
        v.setKreirano(SADA.minusDays(2));
        v.setIzmenjeno(SADA.minusDays(2));
        varijante.add(v);
        return v;
    }

    /** Kolona {@code polja} kako je servis upisuje (normalizovana). */
    static String sema(Polje... polja) {
        return PoljaJson.uJson(BankaPP.normalizujPolja(List.of(polja)));
    }

    static GrupaCmd nova(Long predmetId, Long roditeljId, Namena namena, String naziv) {
        return new GrupaCmd(predmetId, roditeljId, namena, naziv, null, null, null, List.of());
    }

    static GrupaCmd izmena(String naziv, Integer tezina, List<Polje> polja) {
        return new GrupaCmd(null, null, null, naziv, null, tezina, null, polja);
    }

    static void ocekuj(int status, String poruka, Executable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv);
        assertEquals(status, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    List<String> nazivi(List<GrupaZadataka> lista) {
        return lista.stream().map(GrupaZadataka::getNaziv).toList();
    }

    List<Integer> rb(List<GrupaZadataka> lista) {
        return lista.stream().map(GrupaZadataka::getRb).toList();
    }

    List<GrupaZadataka> deca(GrupaZadataka g) {
        return po(grupe.stream().filter(d -> g.getId().equals(roditeljId(d))));
    }

    List<GrupaZadataka> koreni() {
        return po(grupe.stream().filter(d -> d.getRoditelj() == null && d.getNamena() == Namena.PREDAVANJE));
    }

    // ---------------------------------------------------------------- kreiraj

    @Test
    void podgrupaNasledjujeNamenuIPredmetRoditelja() {
        GrupaZadataka r = koren("Konverzije brojeva", 1);

        GrupaDetails d = service.kreiraj(nova(drugiPredmet.getId(), r.getId(), Namena.TEST, "  2 → 10  "));

        assertEquals(Namena.PREDAVANJE, d.namena());
        assertEquals(7L, d.predmetId());
        assertEquals(r.getId(), d.roditeljId());
        assertEquals("2 → 10", d.naziv());
        assertEquals(1, d.rb());
        assertTrue(d.list());
        assertEquals(List.of(new GrupaPutanja(r.getId(), "Konverzije brojeva"), new GrupaPutanja(d.id(), "2 → 10")), d.putanja());
        assertEquals(SADA, d.izmenjeno());
        GrupaZadataka upisana = nadji(d.id()).orElseThrow();
        assertSame(predmet, upisana.getPredmet());
        assertEquals("[]", upisana.getPolja());
        assertEquals(SADA, upisana.getKreirano());
    }

    @Test
    void korenTraziPredmetINamenu() {
        ocekuj(400, "Namena je obavezna za koren.", () -> service.kreiraj(nova(7L, null, null, "Koren")));
        ocekuj(400, "Predmet je obavezan.", () -> service.kreiraj(nova(null, null, Namena.PREDAVANJE, "Koren")));
        ocekuj(404, "Predmet nije pronađen.", () -> service.kreiraj(nova(99L, null, Namena.PREDAVANJE, "Koren")));
        ocekuj(404, "Grupa nije pronađena.", () -> service.kreiraj(nova(7L, 999L, null, "Podgrupa")));
        assertTrue(grupe.isEmpty());

        GrupaDetails d = service.kreiraj(nova(7L, null, Namena.TEST, "Kolokvijum 1"));
        assertEquals(Namena.TEST, d.namena());
        assertNull(d.roditeljId());
        assertEquals(List.of(new GrupaPutanja(d.id(), "Kolokvijum 1")), d.putanja());
    }

    @Test
    void nazivJeJedinstvenMedjuBracomBezObziraNaVelikaSlova() {
        GrupaZadataka r = koren("Konverzije", 1);
        GrupaZadataka s = koren("Logika", 2);
        GrupaZadataka binarni = podgrupa(r, "Binarni", 1);
        GrupaZadataka heks = podgrupa(r, "Heks", 2);

        ocekuj(400, "Grupa sa tim nazivom već postoji.", () -> service.kreiraj(nova(7L, r.getId(), null, "  binarni ")));
        ocekuj(400, "Grupa sa tim nazivom već postoji.", () -> service.kreiraj(nova(7L, null, Namena.PREDAVANJE, "LOGIKA")));
        // rođak, drugi predmet i druga namena nisu braća
        assertEquals("Binarni", service.kreiraj(nova(7L, s.getId(), null, "Binarni")).naziv());
        assertEquals("Logika", service.kreiraj(nova(7L, null, Namena.TEST, "Logika")).naziv());
        assertEquals("Logika", service.kreiraj(nova(8L, null, Namena.PREDAVANJE, "Logika")).naziv());

        // izmena: sopstveni naziv (i drugačija velika slova) je u redu, naziv brata nije
        assertEquals("BINARNI", service.izmeni(binarni.getId(), izmena("BINARNI", null, List.of())).naziv());
        ocekuj(400, "Grupa sa tim nazivom već postoji.", () -> service.izmeni(heks.getId(), izmena("binarni", null, List.of())));
        assertEquals("Heks", heks.getNaziv());
    }

    @Test
    void rbNoveGrupeJeNajveciMedjuBracomPlusJedan() {
        GrupaZadataka r = koren("Konverzije", 1);
        podgrupa(r, "A", 1);
        podgrupa(r, "B", 3);
        koren("Logika", 5);

        assertEquals(4, service.kreiraj(nova(7L, r.getId(), null, "C")).rb());
        assertEquals(6, service.kreiraj(nova(7L, null, Namena.PREDAVANJE, "Automati")).rb());
        assertEquals(1, service.kreiraj(nova(7L, null, Namena.TEST, "Prvi test")).rb());
    }

    @Test
    void dubinaStablaJeNajvise6() {
        GrupaZadataka peti = lanac(5);
        GrupaDetails sesti = service.kreiraj(nova(7L, peti.getId(), null, "Nivo 6"));
        assertEquals(6, sesti.putanja().size());

        int pre = grupe.size();
        ocekuj(400, "Najveća dubina stabla je 6.", () -> service.kreiraj(nova(7L, sesti.id(), null, "Nivo 7")));
        assertEquals(pre, grupe.size());
    }

    @Test
    void podgrupaListuSaVarijantamaJe409() {
        GrupaZadataka r = koren("Konverzije", 1);
        GrupaZadataka list = podgrupa(r, "2 → 10", 1);
        varijanta(list, StatusVarijante.NACRT, RenderStatus.OK, "{}");

        int pre = grupe.size();
        ocekuj(409, "Grupa sa varijantama ne može imati podgrupe.", () -> service.kreiraj(nova(7L, list.getId(), null, "Pod")));
        assertEquals(pre, grupe.size());
        verify(grupaRepository, never()).save(any());
    }

    @Test
    void kreiranjeProveravaGrupuIPoljaPreZakljucavanja() {
        ocekuj(400, "Naziv grupe mora imati 1 do 120 znakova.", () -> service.kreiraj(nova(7L, null, Namena.PREDAVANJE, "   ")));
        ocekuj(400, "Težina mora biti od 1 do 5.", () -> service.kreiraj(
                new GrupaCmd(7L, null, Namena.PREDAVANJE, "Koren", null, 6, null, List.of())));
        ocekuj(400, "Ključ polja 'a' se ponavlja.", () -> service.kreiraj(
                new GrupaCmd(7L, null, Namena.PREDAVANJE, "Koren", null, 2, null, List.of(BROJ_A, TEKST_A))));
        ocekuj(400, "Opis može imati najviše 5 000 znakova.", () -> service.kreiraj(
                new GrupaCmd(7L, null, Namena.PREDAVANJE, "Koren", "x".repeat(5_001), null, null, List.of())));
        verify(predmetRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void poljaSeNormalizujuIUpisujuKaoJson() {
        Polje bezPodrazumevanih = new Polje(" a ", " a) binarni u dekadni ", TipPolja.BROJ, null, null, " ", null, null, null);

        GrupaDetails d = service.kreiraj(new GrupaCmd(7L, null, Namena.PREDAVANJE, "Koren", "  ", 2, "  Brojevi do 255.  ",
                List.of(bezPodrazumevanih)));

        Polje ocekivano = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, 0.0,
                OdstupanjeTip.APSOLUTNO, null, true, true, null);
        assertEquals(List.of(ocekivano), d.polja());
        assertNull(d.opis());
        assertEquals("Brojevi do 255.", d.uputstvo());
        assertEquals(2, d.tezina());
        assertEquals(PoljaJson.uJson(List.of(ocekivano)), nadji(d.id()).orElseThrow().getPolja());
    }

    @Test
    void prevelikaSemaPoljaJe400() {
        // kontrolni znak JSON zapisuje kao \\u0001 (6 bajtova), pa 10 polja sa 6 opcija prelazi text kolonu (64 KB)
        String dugo = "\u0001".repeat(200);
        List<Polje> polja = new ArrayList<>();
        for (char k = 'a'; k <= 'j'; k++) {
            List<PoljeOpcija> opcije = new ArrayList<>();
            for (long i = 1; i <= 6; i++) opcije.add(new PoljeOpcija(i, dugo));
            polja.add(new Polje(String.valueOf(k), dugo, TipPolja.JEDAN_TACAN, null, null, null, null, null, opcije));
        }
        ocekuj(400, "Šema polja je prevelika.", () -> service.kreiraj(
                new GrupaCmd(7L, null, Namena.PREDAVANJE, "Koren", null, null, null, polja)));
        assertTrue(grupe.isEmpty());
    }

    // ---------------------------------------------------------------- detalji, stablo, putanja

    @Test
    void stabloSabiraBrojacePodstablaJednimUpitom() {
        GrupaZadataka r = koren("Konverzije brojeva", 1);
        GrupaZadataka b = podgrupa(r, "16 → 2", 2);
        GrupaZadataka a = podgrupa(r, "2 → 10", 1);
        GrupaZadataka b1 = podgrupa(b, "Bez predznaka", 1);
        GrupaZadataka prazan = koren("Logika", 2);
        koren("Kolokvijum", 1).setNamena(Namena.TEST);
        a.setTezina(2);
        varijanta(a, StatusVarijante.NACRT, RenderStatus.OK, "{}");
        varijanta(a, StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
        varijanta(a, StatusVarijante.NACRT, RenderStatus.GRESKA, "{}");
        varijanta(b1, StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
        varijanta(b1, StatusVarijante.ARHIVIRANA, RenderStatus.GRESKA, "{}");

        List<GrupaCvor> stablo = service.stablo(7L, Namena.PREDAVANJE);

        assertEquals(List.of("Konverzije brojeva", "Logika"), stablo.stream().map(GrupaCvor::naziv).toList());
        GrupaCvor cr = stablo.get(0);
        assertBrojevi(cr, 5, 2, 2, 2);
        assertFalse(cr.list());
        assertEquals(List.of("2 → 10", "16 → 2"), cr.deca().stream().map(GrupaCvor::naziv).toList());
        GrupaCvor ca = cr.deca().get(0);
        assertBrojevi(ca, 3, 1, 2, 1);
        assertTrue(ca.list());
        assertEquals(2, ca.tezina());
        assertEquals(1, ca.rb());
        GrupaCvor cb = cr.deca().get(1);
        assertBrojevi(cb, 2, 1, 0, 1);
        assertFalse(cb.list());
        assertBrojevi(cb.deca().get(0), 2, 1, 0, 1);
        GrupaCvor cp = stablo.get(1);
        assertEquals(prazan.getId(), cp.id());
        assertBrojevi(cp, 0, 0, 0, 0);
        assertTrue(cp.list());
        assertTrue(cp.deca().isEmpty());

        verify(grupaRepository, times(1)).findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(7L, Namena.PREDAVANJE);
        verify(varijantaRepository, times(1)).brojaciPoGrupi(anyCollection());
        verify(grupaRepository, never()).findById(anyLong());
        verify(grupaRepository, never()).findByRoditeljIdOrderByRb(anyLong());

        assertEquals(List.of("Kolokvijum"), service.stablo(7L, Namena.TEST).stream().map(GrupaCvor::naziv).toList());
        assertTrue(service.stablo(8L, Namena.PREDAVANJE).isEmpty());
        ocekuj(404, "Predmet nije pronađen.", () -> service.stablo(99L, Namena.PREDAVANJE));
    }

    static void assertBrojevi(GrupaCvor c, int varijanti, int aktivnih, int nacrta, int gresaka) {
        assertEquals(List.of(varijanti, aktivnih, nacrta, gresaka),
                List.of(c.brojVarijanti(), c.brojAktivnih(), c.brojNacrta(), c.brojGresaka()), c.naziv());
    }

    @Test
    void detaljiUnutrasnjegCvoraImajuZbirPodstablaIPutanju() {
        GrupaZadataka r = koren("Konverzije", 1);
        GrupaZadataka a = podgrupa(r, "2 → 10", 1);
        GrupaZadataka b = podgrupa(r, "16 → 2", 2);
        koren("Logika", 2);
        varijanta(a, StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
        varijanta(b, StatusVarijante.NACRT, RenderStatus.GRESKA, "{}");
        r.setOpis("Brojni sistemi");

        GrupaDetails dr = service.detalji(r.getId());
        assertFalse(dr.list());
        assertEquals(List.of(2, 1, 1, 1), List.of(dr.brojVarijanti(), dr.brojAktivnih(), dr.brojNacrta(), dr.brojGresaka()));
        assertEquals("Brojni sistemi", dr.opis());
        assertNull(dr.roditeljId());

        GrupaDetails db = service.detalji(b.getId());
        assertTrue(db.list());
        assertEquals(List.of(1, 0, 1, 1), List.of(db.brojVarijanti(), db.brojAktivnih(), db.brojNacrta(), db.brojGresaka()));
        assertEquals(List.of(new GrupaPutanja(r.getId(), "Konverzije"), new GrupaPutanja(b.getId(), "16 → 2")), db.putanja());
        assertEquals(List.of(new GrupaPutanja(r.getId(), "Konverzije"), new GrupaPutanja(b.getId(), "16 → 2")),
                service.putanja(b));

        ocekuj(404, "Grupa nije pronađena.", () -> service.detalji(999L));
    }

    @Test
    void iskljucenjePretkaVaziZaPodstabloASopstvenoImaPrednost() {
        GrupaZadataka r = koren("Brojni sistemi", 1);
        GrupaZadataka a = podgrupa(r, "Razlomljeni", 1);
        GrupaZadataka a1 = podgrupa(a, "10 → 2", 1);
        GrupaZadataka a2 = podgrupa(a, "2 → 10", 2);
        GrupaZadataka b = podgrupa(r, "Celi", 2);
        when(temaRepository.iskljuceneGrupe(7L)).thenReturn(Map.of(a.getId(), 40L, a2.getId(), 41L));

        GrupaCvor cr = service.stablo(7L, Namena.PREDAVANJE).get(0);
        GrupaCvor ca = cr.deca().get(0);
        assertEquals(List.of(false, true, true, true, false), Stream.of(cr, ca, ca.deca().get(0), ca.deca().get(1), cr.deca().get(1))
                .map(GrupaCvor::iskljucena).toList());
        assertNull(cr.iskljucenjeId());
        assertEquals(40L, ca.iskljucenjeId());
        assertEquals(40L, ca.deca().get(0).iskljucenjeId());
        assertEquals(41L, ca.deca().get(1).iskljucenjeId());
        assertNull(cr.deca().get(1).iskljucenjeId());
        verify(temaRepository, times(1)).iskljuceneGrupe(7L);

        assertEquals(40L, service.detalji(a1.getId()).iskljucenjeId());
        assertTrue(service.detalji(a1.getId()).iskljucena());
        assertEquals(41L, service.detalji(a2.getId()).iskljucenjeId());
        assertFalse(service.detalji(b.getId()).iskljucena());
        assertNull(service.detalji(r.getId()).iskljucenjeId());
    }

    // ---------------------------------------------------------------- izmeni

    @Test
    void izmenaZakljucavaPredmetPaGrupu() {
        GrupaZadataka r = koren("Konverzije", 1);

        GrupaDetails d = service.izmeni(r.getId(), new GrupaCmd(8L, 5L, Namena.TEST, "Brojni sistemi", "Opis", 3, "Uputstvo",
                List.of(BROJ_A)));

        InOrder redosled = inOrder(predmetRepository, grupaRepository);
        redosled.verify(grupaRepository).findPredmetId(r.getId());
        redosled.verify(predmetRepository).findByIdForUpdate(7L);
        redosled.verify(grupaRepository).findByIdForUpdate(r.getId());
        // predmet, roditelj i namena se izmenom ne menjaju (premeštanje je posebna operacija)
        assertEquals(7L, d.predmetId());
        assertNull(d.roditeljId());
        assertEquals(Namena.PREDAVANJE, d.namena());
        assertEquals("Brojni sistemi", d.naziv());
        assertEquals("Opis", d.opis());
        assertEquals(3, d.tezina());
        assertEquals("Uputstvo", d.uputstvo());
        assertEquals(1, d.polja().size());
        assertEquals(SADA, r.getIzmenjeno());
        ocekuj(404, "Grupa nije pronađena.", () -> service.izmeni(999L, izmena("X", null, List.of())));
    }

    @Test
    void tezinaJeObaveznaZaGrupuSaVarijantama() {
        GrupaZadataka r = koren("Konverzije", 1);
        GrupaZadataka list = podgrupa(r, "2 → 10", 1);
        list.setTezina(2);
        varijanta(list, StatusVarijante.NACRT, RenderStatus.OK, "{}");

        ocekuj(400, "Težina je obavezna za grupu sa varijantama.", () -> service.izmeni(list.getId(), izmena("2 → 10", null, List.of())));
        assertEquals(2, list.getTezina());
        // unutrašnji čvor i list bez varijanti smeju bez težine
        assertNull(service.izmeni(r.getId(), izmena("Konverzije", null, List.of())).tezina());
        assertEquals(4, service.izmeni(list.getId(), izmena("2 → 10", 4, List.of())).tezina());
    }

    @Test
    void promenaTipaPoljaSaAktivnimVarijantamaJe409INistaSeNeMenja() {
        GrupaZadataka list = koren("2 → 10", 1);
        list.setTezina(2);
        list.setPolja(sema(BROJ_A));
        Varijanta aktivna = varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK, ODGOVOR_A);
        Varijanta nacrt = varijanta(list, StatusVarijante.NACRT, RenderStatus.OK, ODGOVOR_A);
        String polja = list.getPolja();

        ocekuj(409, "Tip polja 'a' se ne menja dok grupa ima aktivne varijante.",
                () -> service.izmeni(list.getId(), izmena("Novi naziv", 2, List.of(TEKST_A, TEKST_B))));

        assertEquals("2 → 10", list.getNaziv());
        assertEquals(polja, list.getPolja());
        assertEquals(StatusVarijante.AKTIVNA, aktivna.getStatus());
        assertEquals(ODGOVOR_A, aktivna.getOdgovori());
        assertEquals(StatusVarijante.NACRT, nacrt.getStatus());
        assertEquals(ODGOVOR_A, nacrt.getOdgovori());
    }

    @Test
    void promenaTipaBezAktivnihBriseVrednostAStatusOstaje() {
        GrupaZadataka list = koren("2 → 10", 1);
        list.setTezina(2);
        list.setPolja(sema(BROJ_A));
        Varijanta arhivirana = varijanta(list, StatusVarijante.ARHIVIRANA, RenderStatus.OK, ODGOVOR_A);
        Varijanta nacrt = varijanta(list, StatusVarijante.NACRT, RenderStatus.OK, ODGOVOR_A);

        service.izmeni(list.getId(), izmena("2 → 10", 2, List.of(TEKST_A)));

        assertEquals("{}", arhivirana.getOdgovori());
        assertEquals(StatusVarijante.ARHIVIRANA, arhivirana.getStatus());
        assertEquals(SADA, arhivirana.getIzmenjeno());
        assertEquals("{}", nacrt.getOdgovori());
        assertEquals(StatusVarijante.NACRT, nacrt.getStatus());
    }

    @Test
    void dodatoPoljeVracaAktivneUNacrtAArhiviraneOstajuBezDiranjaRendera() {
        GrupaZadataka list = koren("2 → 10", 1);
        list.setTezina(2);
        list.setPolja(sema(BROJ_A));
        Varijanta aktivna = varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK, ODGOVOR_A);
        Varijanta arhivirana = varijanta(list, StatusVarijante.ARHIVIRANA, RenderStatus.GRESKA, ODGOVOR_A);
        Varijanta nacrt = varijanta(list, StatusVarijante.NACRT, RenderStatus.OK, ODGOVOR_A);

        GrupaDetails d = service.izmeni(list.getId(), izmena("2 → 10", 2, List.of(BROJ_A, TEKST_B)));

        assertEquals(StatusVarijante.NACRT, aktivna.getStatus());
        assertEquals(StatusVarijante.ARHIVIRANA, arhivirana.getStatus());
        assertEquals(StatusVarijante.NACRT, nacrt.getStatus());
        for (Varijanta v : List.of(aktivna, arhivirana, nacrt)) {
            assertEquals(ODGOVOR_A, v.getOdgovori());
        }
        assertEquals(RenderStatus.OK, aktivna.getRenderStatus());
        assertEquals(RenderStatus.GRESKA, arhivirana.getRenderStatus());
        assertEquals("linija 3: Undefined control sequence", arhivirana.getRenderGreska());
        assertEquals(SADA, aktivna.getIzmenjeno());
        // ništa se nije promenilo
        assertEquals(SADA.minusDays(2), arhivirana.getIzmenjeno());
        assertEquals(SADA.minusDays(2), nacrt.getIzmenjeno());
        assertEquals(List.of(3, 0, 2, 1), List.of(d.brojVarijanti(), d.brojAktivnih(), d.brojNacrta(), d.brojGresaka()));
    }

    @Test
    void uklonjenoPoljeBriseVrednostAAktivnaOstajeAktivna() {
        GrupaZadataka list = koren("2 → 10", 1);
        list.setTezina(2);
        list.setPolja(sema(BROJ_A, TEKST_B));
        Varijanta aktivna = varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK,
                "{\"a\":{\"broj\":45.0},\"b\":{\"prihvatljivi\":[\"1011\"]}}");

        service.izmeni(list.getId(), izmena("2 → 10", 2, List.of(TEKST_B)));

        assertEquals("{\"b\":{\"prihvatljivi\":[\"1011\"]}}", aktivna.getOdgovori());
        assertEquals(StatusVarijante.AKTIVNA, aktivna.getStatus());
    }

    @Test
    void uklonjenaTacnaOpcijaBriseVrednostIVracaUNacrt() {
        GrupaZadataka list = koren("Automati", 1);
        list.setTezina(3);
        list.setPolja(sema(IZBOR_C));
        Varijanta druga = varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK, "{\"c\":{\"tacna\":2}}");
        Varijanta prva = varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK, "{\"c\":{\"tacna\":1}}");
        Polje bezDruge = new Polje("c", "Koji automat?", TipPolja.JEDAN_TACAN, null, null, null, null, null,
                List.of(new PoljeOpcija(1L, "A"), new PoljeOpcija(3L, "C")));

        service.izmeni(list.getId(), izmena("Automati", 3, List.of(bezDruge)));

        assertEquals("{}", druga.getOdgovori());
        assertEquals(StatusVarijante.NACRT, druga.getStatus());
        assertEquals("{\"c\":{\"tacna\":1}}", prva.getOdgovori());
        assertEquals(StatusVarijante.AKTIVNA, prva.getStatus());
    }

    @Test
    void novaOpcijaBezIdaNePreuzimaIdUklonjeneOpcijeIzSacuvaneSeme() {
        // sačuvano A=1, B=2, C=3 (C je tačna); klijent (npr. MCP) šalje [1:A, 2:B, D bez id-ja]: D ne sme da postane 3
        GrupaZadataka list = koren("Automati", 1);
        list.setTezina(3);
        list.setPolja(sema(IZBOR_C));
        Varijanta aktivna = varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK, "{\"c\":{\"tacna\":3}}");
        Polje saD = new Polje("c", "Koji automat?", TipPolja.JEDAN_TACAN, null, null, null, null, null,
                List.of(new PoljeOpcija(1L, "A"), new PoljeOpcija(2L, "B"), new PoljeOpcija(null, "D")));

        GrupaDetails d = service.izmeni(list.getId(), izmena("Automati", 3, List.of(saD)));

        List<PoljeOpcija> ocekivano = List.of(new PoljeOpcija(1L, "A"), new PoljeOpcija(2L, "B"), new PoljeOpcija(4L, "D"));
        assertEquals(ocekivano, d.polja().get(0).opcije());
        assertEquals(ocekivano, PoljaJson.izKolone(list.getPolja()).get(0).opcije());
        assertEquals("{}", aktivna.getOdgovori(), "tačna opcija 3 je uklonjena, ne preslikana na D");
        assertEquals(StatusVarijante.NACRT, aktivna.getStatus());
    }

    @Test
    void idNoveOpcijeJeIznadNajvecegSacuvanogIDatogZaIstiKljuc() {
        GrupaZadataka list = koren("Automati", 1);
        list.setTezina(3);
        list.setPolja(sema(IZBOR_C, TEKST_B));   // c: 1..3
        Polje c = new Polje("c", "Koji automat?", TipPolja.VISE_TACNIH, null, null, null, null, null,
                List.of(new PoljeOpcija(null, "X"), new PoljeOpcija(2L, "B"), new PoljeOpcija(null, "Y")));
        Polje e = new Polje("e", "Novo polje", TipPolja.JEDAN_TACAN, null, null, null, null, null,
                List.of(new PoljeOpcija(null, "P"), new PoljeOpcija(null, "Q")));
        Polje f = new Polje("f", "Dato veće", TipPolja.JEDAN_TACAN, null, null, null, null, null,
                List.of(new PoljeOpcija(9L, "R"), new PoljeOpcija(null, "S")));

        GrupaDetails d = service.izmeni(list.getId(), izmena("Automati", 3, List.of(c, e, f)));

        assertEquals(List.of(new PoljeOpcija(4L, "X"), new PoljeOpcija(2L, "B"), new PoljeOpcija(5L, "Y")), d.polja().get(0).opcije());
        assertEquals(List.of(new PoljeOpcija(1L, "P"), new PoljeOpcija(2L, "Q")), d.polja().get(1).opcije());   // novo polje: od 1
        assertEquals(List.of(new PoljeOpcija(9L, "R"), new PoljeOpcija(10L, "S")), d.polja().get(2).opcije());
    }

    @Test
    void izmenaBezPoljaNeMenjaSemuNiVarijanteAPraznaListaBriseSemu() {
        GrupaZadataka list = koren("2 → 10", 1);
        list.setTezina(2);
        list.setPolja(sema(BROJ_A));
        String polja = list.getPolja();
        Varijanta aktivna = varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK, ODGOVOR_A);

        GrupaDetails d = service.izmeni(list.getId(), izmena("2 u 10", 3, null));

        assertEquals(polja, list.getPolja());
        assertEquals(BankaPP.normalizujPolja(List.of(BROJ_A)), d.polja());
        assertEquals("2 u 10", d.naziv());
        assertEquals(3, d.tezina());
        assertEquals(ODGOVOR_A, aktivna.getOdgovori());
        assertEquals(StatusVarijante.AKTIVNA, aktivna.getStatus());
        verify(varijantaRepository, never()).findByGrupaIdForUpdate(anyLong());

        // eksplicitno [] briše šemu i vrednosti; bez polja varijanti ništa ne fali, pa ostaje aktivna
        service.izmeni(list.getId(), izmena("2 u 10", 3, List.of()));
        assertEquals("[]", list.getPolja());
        assertEquals("{}", aktivna.getOdgovori());
        assertEquals(StatusVarijante.AKTIVNA, aktivna.getStatus());
    }

    @Test
    void istaSemaNeCitaVarijante() {
        GrupaZadataka list = koren("2 → 10", 1);
        list.setTezina(2);
        list.setPolja(sema(BROJ_A));
        varijanta(list, StatusVarijante.AKTIVNA, RenderStatus.OK, ODGOVOR_A);

        service.izmeni(list.getId(), izmena("2 u 10", 2, List.of(BROJ_A)));

        verify(varijantaRepository, never()).findByGrupaIdForUpdate(anyLong());
        assertEquals("2 u 10", list.getNaziv());
    }

    // ---------------------------------------------------------------- obrisi

    @Test
    void brisanjeGrupeSaPodgrupamaJe409() {
        GrupaZadataka r = koren("Konverzije", 1);
        podgrupa(r, "2 → 10", 1);

        ocekuj(409, "Grupa ima podgrupe.", () -> service.obrisi(r.getId()));
        assertTrue(grupe.contains(r));
        ocekuj(404, "Grupa nije pronađena.", () -> service.obrisi(999L));
        assertFalse(service.koriscenoNaCasu(r.getId()));
    }

    @Test
    void brisanjeBriseVarijantePaGrupuPaSvgMedijeIPrenumerisaBracu() {
        GrupaZadataka r = koren("Konverzije", 1);
        GrupaZadataka a = podgrupa(r, "A", 1);
        GrupaZadataka b = podgrupa(r, "B", 2);
        GrupaZadataka c = podgrupa(r, "C", 3);
        Varijanta v = varijanta(b, StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
        v.setRenderSvg(medij(SVG));
        v.setRenderResenjeSvg(medij(SVG_RESENJA));
        VarijantaPrilog p = new VarijantaPrilog();
        p.setVarijanta(v);
        p.setIme("skica");
        p.setMedij(medij(PRILOG));
        v.getPrilozi().add(p);
        varijanta(b, StatusVarijante.NACRT, RenderStatus.CEKA, "{}");

        service.obrisi(b.getId());

        InOrder redosled = inOrder(predmetRepository, grupaRepository, varijantaRepository, medijService);
        redosled.verify(predmetRepository).findByIdForUpdate(7L);
        redosled.verify(grupaRepository).findByIdForUpdate(b.getId());
        redosled.verify(varijantaRepository).findByGrupaIdForUpdate(b.getId());
        redosled.verify(varijantaRepository).deleteAll(anyIterable());
        redosled.verify(grupaRepository).delete(b);
        redosled.verify(medijService).obrisi(SVG);
        redosled.verify(medijService).obrisi(SVG_RESENJA);
        verify(medijService, never()).obrisi(PRILOG);   // medij priloga se ne briše, samo red priloga (kaskada)
        assertTrue(varijante.isEmpty());
        assertFalse(grupe.contains(b));
        assertEquals(List.of("A", "C"), nazivi(deca(r)));
        assertEquals(List.of(1, 2), rb(List.of(a, c)));
    }

    static Medij medij(String id) {
        Medij m = new Medij();
        m.setId(id);
        return m;
    }

    // ---------------------------------------------------------------- premesti

    @Test
    void premestanjeUSopstvenoPodstabloJe400() {
        GrupaZadataka a = koren("A", 1);
        GrupaZadataka b = podgrupa(a, "B", 1);
        GrupaZadataka c = podgrupa(b, "C", 1);

        ocekuj(400, "Grupa se ne može premestiti u sopstveno podstablo.", () -> service.premesti(a.getId(), new PremestiCmd(c.getId(), null)));
        ocekuj(400, "Grupa se ne može premestiti u sopstveno podstablo.", () -> service.premesti(a.getId(), new PremestiCmd(a.getId(), null)));
        ocekuj(400, "Grupa se ne može premestiti u sopstveno podstablo.", () -> service.premesti(b.getId(), new PremestiCmd(c.getId(), 1)));
        assertNull(a.getRoditelj());
        assertSame(a, b.getRoditelj());
    }

    @Test
    void premestanjeUDrugiPredmetIliNamenuJe400() {
        GrupaZadataka a = koren("A", 1);
        GrupaZadataka test = koren(predmet, Namena.TEST, "Test", 1);
        GrupaZadataka drugi = koren(drugiPredmet, Namena.PREDAVANJE, "Drugi", 1);

        ocekuj(400, "Grupa se premešta samo unutar istog predmeta i namene.", () -> service.premesti(a.getId(), new PremestiCmd(test.getId(), null)));
        ocekuj(400, "Grupa se premešta samo unutar istog predmeta i namene.", () -> service.premesti(a.getId(), new PremestiCmd(drugi.getId(), null)));
        // drugi predmet se odbija pre zaključavanja (bez ključa tuđeg predmeta i tuđe grupe)
        verify(grupaRepository, never()).findByIdForUpdate(drugi.getId());
        ocekuj(404, "Grupa nije pronađena.", () -> service.premesti(a.getId(), new PremestiCmd(999L, null)));
        assertNull(a.getRoditelj());
    }

    @Test
    void premestanjePrekoDubine6Je400() {
        GrupaZadataka cetvrti = lanac(4);
        GrupaZadataka x = koren("X", 2);
        GrupaZadataka y = podgrupa(x, "Y", 1);
        podgrupa(y, "Z", 1);
        GrupaZadataka treci = cetvrti.getRoditelj();

        // X ima visinu 3: ispod četvrtog nivoa bi najdublji bio na 7.
        ocekuj(400, "Najveća dubina stabla je 6.", () -> service.premesti(x.getId(), new PremestiCmd(cetvrti.getId(), null)));
        assertNull(x.getRoditelj());

        GrupaDetails d = service.premesti(x.getId(), new PremestiCmd(treci.getId(), null));
        assertEquals(treci.getId(), d.roditeljId());
        assertEquals(4, d.putanja().size());
    }

    @Test
    void premestanjeUListSaVarijantamaJe409() {
        GrupaZadataka a = koren("A", 1);
        GrupaZadataka list = koren("List", 2);
        varijanta(list, StatusVarijante.NACRT, RenderStatus.CEKA, "{}");

        ocekuj(409, "Grupa sa varijantama ne može imati podgrupe.", () -> service.premesti(a.getId(), new PremestiCmd(list.getId(), null)));
        assertNull(a.getRoditelj());
    }

    @Test
    void premestanjeTraziJedinstvenNazivMedjuNovomBracom() {
        GrupaZadataka a = koren("A", 1);
        GrupaZadataka b = koren("B", 2);
        GrupaZadataka x = podgrupa(a, "Zbir", 1);
        podgrupa(b, "zbir", 1);

        ocekuj(400, "Grupa sa tim nazivom već postoji.", () -> service.premesti(x.getId(), new PremestiCmd(b.getId(), null)));
        assertSame(a, x.getRoditelj());
    }

    @Test
    void premestiUmeceIPrenumerisaBracu() {
        GrupaZadataka p = koren("P", 1);
        GrupaZadataka q = koren("Q", 2);
        GrupaZadataka c1 = podgrupa(p, "c1", 1);
        GrupaZadataka c2 = podgrupa(p, "c2", 2);
        GrupaZadataka c3 = podgrupa(p, "c3", 3);
        GrupaZadataka c4 = podgrupa(p, "c4", 4);
        GrupaZadataka q1 = podgrupa(q, "q1", 1);

        // unutar istih roditelja: c4 na drugo mesto
        GrupaDetails d = service.premesti(c4.getId(), new PremestiCmd(p.getId(), 2));
        assertEquals(2, d.rb());
        assertEquals(List.of("c1", "c4", "c2", "c3"), nazivi(deca(p)));
        assertEquals(List.of(1, 2, 3, 4), rb(deca(p)));

        // drugi roditelj, na početak: stari roditelj se prenumeriše, novi dobija c1 na 1
        d = service.premesti(c1.getId(), new PremestiCmd(q.getId(), 1));
        assertEquals(q.getId(), d.roditeljId());
        assertEquals(List.of(new GrupaPutanja(q.getId(), "Q"), new GrupaPutanja(c1.getId(), "c1")), d.putanja());
        assertEquals(List.of("c4", "c2", "c3"), nazivi(deca(p)));
        assertEquals(List.of(1, 2, 3), rb(deca(p)));
        assertEquals(List.of(c1, q1), deca(q));
        assertEquals(List.of(1, 2), rb(deca(q)));

        // rb null = na kraj; rb van opsega se svodi na granicu; koren (roditeljId null)
        service.premesti(c4.getId(), new PremestiCmd(q.getId(), null));
        assertEquals(List.of("c1", "q1", "c4"), nazivi(deca(q)));
        service.premesti(c2.getId(), new PremestiCmd(null, 99));
        assertEquals(List.of("P", "Q", "c2"), nazivi(koreni()));
        assertEquals(List.of(1, 2, 3), rb(koreni()));
        service.premesti(c3.getId(), new PremestiCmd(null, 0));
        assertEquals(List.of("c3", "P", "Q", "c2"), nazivi(koreni()));
        assertEquals(List.of(1, 2, 3, 4), rb(koreni()));
        assertTrue(deca(p).isEmpty());
        assertEquals(SADA, c3.getIzmenjeno());
    }

    // ---------------------------------------------------------------- redosled

    @Test
    void redosledMoraBitiPermutacijaDece() {
        GrupaZadataka p = koren("P", 1);
        GrupaZadataka a = podgrupa(p, "a", 1);
        GrupaZadataka b = podgrupa(p, "b", 2);
        GrupaZadataka c = podgrupa(p, "c", 3);
        GrupaZadataka tudja = koren("Q", 2);

        for (List<Long> ids : List.of(List.of(a.getId(), b.getId()), List.of(a.getId(), b.getId(), b.getId()),
                List.of(a.getId(), b.getId(), tudja.getId()))) {
            ocekuj(400, "Redosled mora sadržati sve podgrupe.",
                    () -> service.redosled(p.getId(), 7L, Namena.PREDAVANJE, new RedosledGrupaCmd(ids)));
        }
        ocekuj(400, "Redosled mora sadržati sve podgrupe.", () -> service.redosled(p.getId(), 7L, Namena.PREDAVANJE, new RedosledGrupaCmd(null)));
        assertEquals(List.of("a", "b", "c"), nazivi(deca(p)));
        // roditelj iz drugog stabla (namena, predmet) nije pronađen
        ocekuj(404, "Grupa nije pronađena.", () -> service.redosled(p.getId(), 7L, Namena.TEST, new RedosledGrupaCmd(List.of())));
        ocekuj(404, "Grupa nije pronađena.", () -> service.redosled(p.getId(), 8L, Namena.PREDAVANJE, new RedosledGrupaCmd(List.of())));

        service.redosled(p.getId(), 7L, Namena.PREDAVANJE, new RedosledGrupaCmd(List.of(c.getId(), a.getId(), b.getId())));
        assertEquals(List.of("c", "a", "b"), nazivi(deca(p)));
        assertEquals(List.of(1, 2, 3), rb(deca(p)));
        verify(predmetRepository, atLeastOnce()).findByIdForUpdate(7L);

        service.redosled(null, 7L, Namena.PREDAVANJE, new RedosledGrupaCmd(List.of(tudja.getId(), p.getId())));
        assertEquals(List.of("Q", "P"), nazivi(koreni()));
    }
}
