package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;
import tri.novica.gfssystem.validation.BankaPP;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KopijaGrupeService} nad pravom MySQL bazom (Flyway V1..V12, kao CI): kopija podstabla u drugu namenu istog
 * predmeta (grupe CLAUDE sa serijom, šema sa istim id-jevima opcija, varijante osim arhiviranih kao Klodovi nacrti sa
 * istim prilozima, uspeo render kao nov SVG medij istih bajtova i istog heša), sve ili ništa (kolizija imena na pola
 * kopije ne ostavlja ni red ni fajl) i provera cilja. Bez {@code @Transactional}: svaki poziv se zaista commit-uje, pa test
 * čisti za sobom kroz JDBC.
 */
@SpringBootTest
class KopijaGrupeDbTest {

    static final byte[] SVG_ZADATAK = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>".getBytes(StandardCharsets.UTF_8);
    static final byte[] SVG_RESENJE = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h2\"/></svg>".getBytes(StandardCharsets.UTF_8);
    static final byte[] PRILOG_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final String HASH = "a".repeat(64);
    static final String CILJ = "Cilj kopije mora biti u istom predmetu i nameni.";
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Polje IZBOR_C = new Polje("c", "Koji automat?", TipPolja.JEDAN_TACAN, null, null, null, null, null,
            List.of(new PoljeOpcija(1L, "Mur"), new PoljeOpcija(2L, "Mili"), new PoljeOpcija(5L, "Tjuring")));

    @Autowired KopijaGrupeService service;
    @Autowired GrupaZadatakaService grupaService;
    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Value("${gfs.mediji.dir}") String medijiDir;

    Predmet predmet;
    Predmet drugiPredmet;
    final List<String> mediji = new ArrayList<>();

    /** Izvorno stablo u TEST-u: koren sa dva lista, tri varijante (jedna arhivirana). */
    GrupaDetails koren;
    GrupaDetails konverzije;
    GrupaDetails automati;
    Long aktivna;
    Long arhivirana;
    Long pala;
    String svgZadatka;
    String svgResenja;
    String prilog;

    @BeforeEach
    void setUp() {
        predmet = predmet("Banka kopija DB test ");
        drugiPredmet = predmet("Banka kopija DB test (drugi) ");

        koren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum 1", "Prvi kolokvijum",
                null, "Brojevi do 255.", List.of()));
        konverzije = grupaService.kreiraj(new GrupaCmd(null, koren.id(), null, "Konverzije", null, 2, "Bez kalkulatora.",
                List.of(BROJ_A, IZBOR_C)));
        automati = grupaService.kreiraj(new GrupaCmd(null, koren.id(), null, "Automati", "Mur i Mili", 3, null, List.of(IZBOR_C)));

        aktivna = varijanta(konverzije.id(), 1, StatusVarijante.AKTIVNA, RenderStatus.OK, "{\"a\":{\"broj\":11.0},\"c\":{\"tacna\":2}}");
        arhivirana = varijanta(konverzije.id(), 2, StatusVarijante.ARHIVIRANA, RenderStatus.OK, "{\"a\":{\"broj\":5.0},\"c\":{\"tacna\":1}}");
        pala = varijanta(automati.id(), 1, StatusVarijante.NACRT, RenderStatus.GRESKA, "{\"c\":{\"tacna\":5}}");

        svgZadatka = medij(medijService.sacuvajRender(SVG_ZADATAK, "zadatak-" + aktivna + ".svg").id());
        svgResenja = medij(medijService.sacuvajRender(SVG_RESENJE, "resenje-" + aktivna + ".svg").id());
        prilog = medij(medijService.sacuvajPrilog(new MockMultipartFile("fajl", "skica.png", "image/png", PRILOG_PNG)).id());
        jdbc.update("update varijante set naziv = 'V1', resenje = 'Rešenje: $11$', render_svg_id = ?, render_resenje_svg_id = ?, "
                + "render_hash = ?, ikad_odobrena = 1 where id = ?", svgZadatka, svgResenja, HASH, aktivna);
        jdbc.update("insert into varijanta_prilozi (varijanta_id, ime, medij_id) values (?, 'skica', ?)", aktivna, prilog);
        // pala: stari SVG ostaje uz grešku, ali se kopira samo uspeo render
        String stari = medij(medijService.sacuvajRender(SVG_ZADATAK, "zadatak-" + pala + ".svg").id());
        jdbc.update("update varijante set render_svg_id = ?, render_hash = ?, render_greska = 'tekst:3: Undefined control sequence.' "
                + "where id = ?", stari, "b".repeat(64), pala);
    }

    @AfterEach
    void tearDown() {
        for (Predmet p : List.of(predmet, drugiPredmet)) {
            Long id = p.getId();
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
            jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
            svg.forEach(medijService::obrisi);
            predmetRepository.deleteById(id);
        }
        mediji.forEach(medijService::obrisi);
    }

    Predmet predmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv + UUID.randomUUID().toString().substring(0, 8));
        return predmetRepository.save(p);
    }

    String medij(String id) {
        mediji.add(id);
        return id;
    }

    Long varijanta(Long grupaId, int rb, StatusVarijante status, RenderStatus render, String odgovori) {
        Varijanta v = new Varijanta();
        v.setGrupa(grupaRepository.findById(grupaId).orElseThrow());
        v.setRb(rb);
        v.setTekst("Pretvori $1011_2$ u dekadni. (" + rb + ")");
        v.setOdgovori(odgovori);
        v.setStatus(status);
        v.setIzvor(IzvorVarijante.RUCNO);
        v.setRenderStatus(render);
        v.setKreirano(LocalDateTime.now(clock));
        v.setIzmenjeno(LocalDateTime.now(clock));
        return varijantaRepository.save(v).getId();
    }

    /** Grupe predmeta u nameni, po id-ju (pre-order kopije = redosled upisa). */
    List<Map<String, Object>> grupe(Namena namena) {
        return jdbc.queryForList("select id, roditelj_id, naziv, opis, tezina, uputstvo, polja, rb, izvor, serija "
                + "from grupe_zadataka where predmet_id = ? and namena = ? order by id", predmet.getId(), namena.name());
    }

    List<Map<String, Object>> varijanteGrupe(Object grupaId) {
        return jdbc.queryForList("select id, rb, naziv, tekst, resenje, odgovori, status, izvor, serija, ikad_odobrena, "
                + "zamenjuje_id, render_status, render_svg_id, render_resenje_svg_id, render_hash, render_greska "
                + "from varijante where grupa_id = ? order by rb", grupaId);
    }

    /** Broj redova i fajlova koje bi kopija mogla da ostavi: grupe i varijante predmeta, prilozi, mediji i fajlovi. */
    List<Long> stanje() throws IOException {
        Long grupa = jdbc.queryForObject("select count(*) from grupe_zadataka where predmet_id = ?", Long.class, predmet.getId());
        Long varijanti = jdbc.queryForObject("select count(*) from varijante v join grupe_zadataka g on g.id = v.grupa_id "
                + "where g.predmet_id = ?", Long.class, predmet.getId());
        Long priloga = jdbc.queryForObject("select count(*) from varijanta_prilozi", Long.class);
        Long medija = jdbc.queryForObject("select count(*) from mediji", Long.class);
        long fajlova;
        try (Stream<Path> fajlovi = Files.list(Path.of(medijiDir))) {
            fajlova = fajlovi.count();
        }
        return List.of(grupa, varijanti, priloga, medija, fajlova);
    }

    byte[] fajl(Object medijId) throws IOException {
        return Files.readAllBytes(Path.of(medijiDir).resolve((String) medijId));
    }

    static void ocekuj(int kod, String poruka, Runnable poziv) {
        SystemException e = assertThrows(SystemException.class, poziv::run);
        assertEquals(kod, e.getCode());
        assertEquals(poruka, e.getMessage());
    }

    @Test
    void kopijaPodstablaUDruguNamenu() throws IOException {
        GrupaDetails kopija = service.kopiraj(koren.id(), Namena.PREDAVANJE, null, "kopija-kol1");

        assertEquals(Namena.PREDAVANJE, kopija.namena());
        assertNull(kopija.roditeljId());
        assertEquals("Kolokvijum 1", kopija.naziv());
        assertEquals(IzvorVarijante.CLAUDE, kopija.izvor());
        assertEquals("kopija-kol1", kopija.serija());
        assertEquals(List.of(2, 0, 2, 0), List.of(kopija.brojVarijanti(), kopija.brojAktivnih(), kopija.brojNacrta(),
                kopija.brojGresaka()));

        // tri grupe, pre-order, isti sadržaj i šema (isti id-jevi opcija), CLAUDE sa serijom
        List<Map<String, Object>> izvor = grupe(Namena.TEST);
        List<Map<String, Object>> nove = grupe(Namena.PREDAVANJE);
        assertEquals(3, nove.size());
        assertEquals(kopija.id(), ((Number) nove.get(0).get("id")).longValue());
        for (int i = 0; i < 3; i++) {
            Map<String, Object> g = nove.get(i);
            Map<String, Object> o = izvor.get(i);
            for (String kolona : List.of("naziv", "opis", "tezina", "uputstvo", "rb")) {
                assertEquals(o.get(kolona), g.get(kolona), kolona);
            }
            assertEquals(PoljaJson.izKolone((String) o.get("polja")), PoljaJson.izKolone((String) g.get("polja")));
            assertEquals("CLAUDE", g.get("izvor"));
            assertEquals("kopija-kol1", g.get("serija"));
        }
        assertEquals(kopija.id(), ((Number) nove.get(1).get("roditelj_id")).longValue());
        assertEquals(kopija.id(), ((Number) nove.get(2).get("roditelj_id")).longValue());
        assertEquals(List.of(5L), PoljaJson.izKolone((String) nove.get(2).get("polja")).get(0).opcije().stream()
                .map(PoljeOpcija::id).skip(2).toList());

        // uspela varijanta: nacrt CLAUDE, isti sadržaj i prilog, nov SVG istih bajtova i isti heš
        List<Map<String, Object>> uKonverzijama = varijanteGrupe(nove.get(1).get("id"));
        assertEquals(1, uKonverzijama.size(), "arhivirana se ne kopira");
        Map<String, Object> v = uKonverzijama.get(0);
        Map<String, Object> original = varijanteGrupe(konverzije.id()).get(0);
        for (String kolona : List.of("naziv", "tekst", "resenje", "odgovori", "render_hash")) {
            assertEquals(original.get(kolona), v.get(kolona), kolona);
        }
        assertEquals(1, ((Number) v.get("rb")).intValue());
        assertEquals("NACRT", v.get("status"));
        assertEquals("CLAUDE", v.get("izvor"));
        assertEquals("kopija-kol1", v.get("serija"));
        assertFalse((Boolean) v.get("ikad_odobrena"));
        assertNull(v.get("zamenjuje_id"));
        assertEquals("OK", v.get("render_status"));
        assertNotEquals(svgZadatka, v.get("render_svg_id"));
        assertNotEquals(svgResenja, v.get("render_resenje_svg_id"));
        assertArrayEquals(SVG_ZADATAK, fajl(v.get("render_svg_id")));
        assertArrayEquals(SVG_RESENJE, fajl(v.get("render_resenje_svg_id")));
        Long kopijaId = ((Number) v.get("id")).longValue();
        assertEquals("zadatak-" + kopijaId + ".svg", jdbc.queryForObject("select naziv from mediji where id = ?", String.class,
                v.get("render_svg_id")));
        assertEquals("RENDER_SVG", jdbc.queryForObject("select vrsta from mediji where id = ?", String.class, v.get("render_svg_id")));
        assertEquals(List.of("skica:" + prilog), jdbc.queryForList("select concat(ime, ':', medij_id) from varijanta_prilozi "
                + "where varijanta_id = ?", String.class, kopijaId));
        // odgovori važe za kopiranu šemu
        List<Polje> sema = BankaPP.normalizujPolja(PoljaJson.izKolone((String) nove.get(1).get("polja")));
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(sema, PoljaJson.odgovoriIzKolone((String) v.get("odgovori"))));

        // pala varijanta: bez SVG-a i heša, čeka render
        List<Map<String, Object>> uAutomatima = varijanteGrupe(nove.get(2).get("id"));
        assertEquals(1, uAutomatima.size());
        Map<String, Object> p = uAutomatima.get(0);
        assertEquals("CEKA", p.get("render_status"));
        assertNull(p.get("render_svg_id"));
        assertNull(p.get("render_resenje_svg_id"));
        assertNull(p.get("render_hash"));
        assertNull(p.get("render_greska"));
        assertEquals("{\"c\":{\"tacna\":5}}", p.get("odgovori"));
        assertEquals("CLAUDE", p.get("izvor"));

        // izvor netaknut
        assertEquals(List.of("AKTIVNA", "ARHIVIRANA"), varijanteGrupe(konverzije.id()).stream().map(r -> r.get("status")).toList());
        assertEquals(svgZadatka, varijanteGrupe(konverzije.id()).get(0).get("render_svg_id"));
        assertEquals(List.of("RUCNO", "RUCNO", "RUCNO"), izvor.stream().map(g -> g.get("izvor")).toList());

        // stablo cilja vidi kopiju sa Klodovim nacrtima
        List<GrupaCvor> stablo = grupaService.stablo(predmet.getId(), Namena.PREDAVANJE);
        assertEquals(1, stablo.size());
        assertEquals(List.of("Konverzije", "Automati"), stablo.get(0).deca().stream().map(GrupaCvor::naziv).toList());
        assertEquals(2, stablo.get(0).brojClaudeNacrta());
    }

    @Test
    void kopijaPodRoditeljaIBrojStavki() {
        GrupaDetails predavanja = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "Nedelja 3", null,
                null, null, List.of()));
        grupaService.kreiraj(new GrupaCmd(null, predavanja.id(), null, "Uvod", null, null, null, List.of()));

        assertEquals(5, service.brojStavki(koren.id()));
        assertEquals(2, service.brojStavki(automati.id()));
        ocekuj(404, "Grupa nije pronađena.", () -> service.brojStavki(-1L));

        GrupaDetails kopija = service.kopiraj(automati.id(), Namena.PREDAVANJE, predavanja.id(), "kopija-kol1");

        assertEquals(predavanja.id(), kopija.roditeljId());
        assertEquals(2, kopija.rb());
        assertEquals("Automati", kopija.naziv());
        assertEquals(3, kopija.tezina());
        assertEquals(1, kopija.brojNacrta());
        assertEquals(List.of(predavanja.id(), kopija.id()), kopija.putanja().stream().map(GrupaPutanja::id).toList());

        // ista namena pod drugim roditeljem je dozvoljena (kopija ostaje u istom stablu)
        GrupaDetails uTestu = service.kopiraj(automati.id(), Namena.TEST, null, "kopija-kol1");
        assertEquals(Namena.TEST, uTestu.namena());
        assertNull(uTestu.roditeljId());
        assertEquals(2, uTestu.rb());
    }

    @Test
    void kopijaSaKolizijomImenaNeUpisujeNista() throws IOException {
        // stari red mimo pravila (razlikuje se samo po velikim slovima i razmaku): drugi list pada tek kad je prvi već kopiran
        jdbc.update("update grupe_zadataka set naziv = 'konverzije ' where id = ?", automati.id());
        List<Long> pre = stanje();

        ocekuj(400, "Grupa sa tim nazivom već postoji.",
                () -> service.kopiraj(koren.id(), Namena.PREDAVANJE, null, "kopija-kol1"));

        assertEquals(pre, stanje());
        assertTrue(grupe(Namena.PREDAVANJE).isEmpty());

        // cilj već ima koren istog imena: pada prva grupa
        jdbc.update("update grupe_zadataka set naziv = 'Automati' where id = ?", automati.id());
        grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "KOLOKVIJUM 1", null, null, null, List.of()));
        List<Long> preDrugog = stanje();
        ocekuj(400, "Grupa sa tim nazivom već postoji.",
                () -> service.kopiraj(koren.id(), Namena.PREDAVANJE, null, "kopija-kol1"));
        assertEquals(preDrugog, stanje());
    }

    @Test
    void ciljMoraBitiUIstomPredmetuINameni() throws IOException {
        GrupaDetails tudj = grupaService.kreiraj(new GrupaCmd(drugiPredmet.getId(), null, Namena.PREDAVANJE, "Tuđi koren", null,
                null, null, List.of()));
        GrupaDetails drugiTest = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Ispit", null, null,
                null, List.of()));
        List<Long> pre = stanje();

        ocekuj(400, CILJ, () -> service.kopiraj(koren.id(), Namena.PREDAVANJE, tudj.id(), "kopija-kol1"));
        ocekuj(400, CILJ, () -> service.kopiraj(koren.id(), Namena.PREDAVANJE, drugiTest.id(), "kopija-kol1"));
        ocekuj(400, "Namena je obavezna.", () -> service.kopiraj(koren.id(), null, null, "kopija-kol1"));
        ocekuj(400, Serija.PORUKA, () -> service.kopiraj(koren.id(), Namena.PREDAVANJE, null, "Kopija"));
        ocekuj(404, "Grupa nije pronađena.", () -> service.kopiraj(-1L, Namena.PREDAVANJE, null, "kopija-kol1"));
        ocekuj(404, "Grupa nije pronađena.", () -> service.kopiraj(koren.id(), Namena.PREDAVANJE, -1L, "kopija-kol1"));
        // list sa varijantama ne prima podgrupu (pravilo kreiranja grupe)
        ocekuj(409, "Grupa sa varijantama ne može imati podgrupe.",
                () -> service.kopiraj(automati.id(), Namena.TEST, konverzije.id(), "kopija-kol1"));

        assertEquals(pre, stanje());
        assertEquals(List.of(), jdbc.queryForList("select id from grupe_zadataka where predmet_id = ?", Long.class,
                drugiPredmet.getId()).stream().filter(id -> !id.equals(tudj.id())).toList());
    }
}
