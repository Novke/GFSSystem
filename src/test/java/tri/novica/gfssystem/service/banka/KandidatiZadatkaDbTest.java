package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Kandidati;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.KandidatVarijante;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KandidatiZadatka} nad pravom MySQL bazom (Flyway, kao CI), JDBC fixture u PREDAVANJE stablu: koren K -> A (list:
 * 3 AKTIVNE sa SVG-om, jedna AKTIVNA bez SVG-a, NACRT i ARHIVIRANA), B (unutrašnji) -> B1 (2 AKTIVNE), B2 (1 AKTIVNA, isključena
 * tema), C (list, bez AKTIVNIH); uz to TEST koren T sa 1 AKTIVNOM. Grupe su upisane tako da redosled stabla (po {@code rb})
 * nije redosled id-jeva. Bez {@code @Transactional}: svaki upis se commit-uje, pa test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class KandidatiZadatkaDbTest {

    static final String SEMA_A = """
            [{"kljuc":"a","oznaka":"a)","tip":"BROJ","odstupanje":0.5,"odstupanjeTip":"APSOLUTNO","jedinica":"cm"}]""";
    static final String SEMA_B1 = """
            [{"kljuc":"x","oznaka":"x)","tip":"JEDAN_TACAN","opcije":[{"id":1,"tekst":"da"},{"id":2,"tekst":"ne"}]}]""";
    static final String SEMA_B2 = """
            [{"kljuc":"y","oznaka":"y)","tip":"TEKST","zanemariVelicinu":true}]""";

    @Autowired KandidatiZadatka kandidati;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JdbcTemplate jdbc;

    Predmet predmet;
    Predmet prazanPredmet;
    final List<String> mediji = new ArrayList<>();
    long k, a, b, b1, b2, c, t;
    /** Medij teksta po nazivu varijante. */
    final Map<String, String> svg = new HashMap<>();
    String resenjeA1;

    @BeforeEach
    void setUp() {
        predmet = noviPredmet();
        prazanPredmet = noviPredmet();
        // upisano tako da id-jevi nisu redom stabla: C (rb 3), B (rb 2), A (rb 1); B2 (rb 2) pre B1 (rb 1)
        k = grupa(null, "PREDAVANJE", "K", 1, "[]");
        c = grupa(k, "PREDAVANJE", "C", 3, "[]");
        b = grupa(k, "PREDAVANJE", "B", 2, "[]");
        a = grupa(k, "PREDAVANJE", "A", 1, SEMA_A);
        b2 = grupa(b, "PREDAVANJE", "B2", 2, SEMA_B2);
        b1 = grupa(b, "PREDAVANJE", "B1", 1, SEMA_B1);
        t = grupa(null, "TEST", "T", 1, "[]");

        resenjeA1 = medij();
        varijanta(a, "A1", "AKTIVNA", true, resenjeA1);
        varijanta(a, "A2", "AKTIVNA", true, null);
        varijanta(a, "A3", "AKTIVNA", true, null);
        varijanta(a, "A-bez-svg", "AKTIVNA", false, null);
        varijanta(a, "A-nacrt", "NACRT", true, null);
        varijanta(a, "A-arhiva", "ARHIVIRANA", true, null);
        varijanta(b1, "B1-1", "AKTIVNA", true, null);
        varijanta(b1, "B1-2", "AKTIVNA", true, null);
        varijanta(b2, "B2-1", "AKTIVNA", true, null);
        varijanta(c, "C-nacrt", "NACRT", true, null);
        varijanta(t, "T-1", "AKTIVNA", true, null);
        tema(b2, "Podgrupa B2");
    }

    @AfterEach
    void tearDown() {
        for (Predmet p : List.of(predmet, prazanPredmet)) {
            jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", p.getId());
            jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", p.getId());
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", p.getId());
            predmetRepository.deleteById(p.getId());
            assertEquals(0, jdbc.queryForObject("select count(*) from iskljucene_teme where predmet_id = ?",
                    Integer.class, p.getId()));
        }
        for (String id : mediji) {
            jdbc.update("delete from mediji where id = ?", id);
        }
    }

    Predmet noviPredmet() {
        Predmet p = new Predmet();
        p.setNaziv("Kandidati zadatka DB test " + UUID.randomUUID().toString().substring(0, 8));
        return predmetRepository.save(p);
    }

    long grupa(Long roditelj, String namena, String naziv, int rb, String polja) {
        Map<String, Object> red = new HashMap<>();
        red.put("predmet_id", predmet.getId());
        red.put("roditelj_id", roditelj);
        red.put("namena", namena);
        red.put("naziv", naziv);
        red.put("rb", rb);
        red.put("tezina", roditelj == null ? null : 3);
        red.put("polja", polja);
        red.put("izvor", "RUCNO");
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        return new SimpleJdbcInsert(jdbc).withTableName("grupe_zadataka").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(red).longValue();
    }

    String medij() {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into mediji (id, naziv, mime, velicina, vrsta, kreirano) "
                + "values (?, 'render.svg', 'image/svg+xml', 10, 'RENDER_SVG', now(6))", id);
        mediji.add(id);
        return id;
    }

    long varijanta(long grupa, String naziv, String status, boolean saSvg, String resenjeSvg) {
        String svgId = saSvg ? medij() : null;
        if (saSvg) {
            svg.put(naziv, svgId);
        }
        Map<String, Object> red = new HashMap<>();
        red.put("grupa_id", grupa);
        red.put("rb", 1);
        red.put("naziv", naziv);
        red.put("tekst", "Zadatak " + naziv);
        red.put("odgovori", "{\"a\":{\"broj\":45.0}}");
        red.put("status", status);
        red.put("izvor", "RUCNO");
        red.put("ikad_odobrena", "AKTIVNA".equals(status) || "ARHIVIRANA".equals(status));
        red.put("render_status", saSvg ? "OK" : "CEKA");
        red.put("render_svg_id", svgId);
        red.put("render_resenje_svg_id", resenjeSvg);
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        red.put("nivo", 2);
        return new SimpleJdbcInsert(jdbc).withTableName("varijante").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(red).longValue();
    }

    void tema(long grupa, String opis) {
        jdbc.update("insert into iskljucene_teme (predmet_id, opis, grupa_id, izvor, kreirano) values (?, ?, ?, 'RUCNO', now(6))",
                predmet.getId(), opis, grupa);
    }

    static List<GrupaPutanja> putanja(Object... parovi) {
        List<GrupaPutanja> rezultat = new ArrayList<>();
        for (int i = 0; i < parovi.length; i += 2) {
            rezultat.add(new GrupaPutanja((Long) parovi[i], (String) parovi[i + 1]));
        }
        return rezultat;
    }

    @Test
    void opisPreskaceIskljucenuPodgrupu() {
        Opis opis = kandidati.stablo(predmet.getId()).opis(k);

        assertEquals(k, opis.grupaId());
        assertEquals("K", opis.naziv());
        assertEquals(putanja(k, "K"), opis.putanja());
        assertEquals(5, opis.brojKandidata(), "A 3 + B1 2 + C 0, bez B2");
        assertEquals(List.of(a, b1, c), opis.listovi(), "redom stabla (rb), ne redom id-jeva");
        assertEquals(putanja(b2, "B2"), opis.iskljucenePodgrupe());
        assertFalse(opis.iskljucena());
        assertFalse(opis.list());
        assertNull(opis.polja());
    }

    @Test
    void iskljucenaPodgrupaSamaJeListIIskljucena() {
        Opis opis = kandidati.stablo(predmet.getId()).opis(b2);

        assertTrue(opis.iskljucena());
        assertEquals(List.of(b2), opis.listovi(), "list koji je sam isključen daje sebe (samo upozorenje)");
        assertEquals(1, opis.brojKandidata());
        assertTrue(opis.list());
        assertEquals(List.of(), opis.iskljucenePodgrupe());
        assertEquals(putanja(k, "K", b, "B", b2, "B2"), opis.putanja());
        assertEquals(1, opis.polja().size());
        assertEquals("y", opis.polja().get(0).kljuc());
        assertEquals(TipPolja.TEKST, opis.polja().get(0).tip());
    }

    @Test
    void opisListaDajeSemuIPutanju() {
        Opis opis = kandidati.stablo(predmet.getId()).opis(a);

        assertEquals(putanja(k, "K", a, "A"), opis.putanja());
        assertTrue(opis.list());
        assertFalse(opis.iskljucena());
        assertEquals(List.of(a), opis.listovi());
        assertEquals(3, opis.brojKandidata(), "bez AKTIVNE bez SVG-a, NACRT-a i ARHIVIRANE");
        List<Polje> polja = opis.polja();
        assertEquals(1, polja.size());
        assertEquals("a", polja.get(0).kljuc());
        assertEquals("a)", polja.get(0).oznaka());
        assertEquals(TipPolja.BROJ, polja.get(0).tip());
        assertEquals(0.5, polja.get(0).odstupanje());
        assertEquals("cm", polja.get(0).jedinica());
    }

    @Test
    void listBezPoljaJeRadNaPapiru() {
        Opis opis = kandidati.stablo(predmet.getId()).opis(c);

        assertTrue(opis.list());
        assertEquals(List.of(), opis.polja(), "prazna šema, ne null");
        assertEquals(List.of(c), opis.listovi());
        assertEquals(0, opis.brojKandidata());
    }

    @Test
    void temaNaPrecuIskljucujeSveIspodNjega() {
        tema(k, "Ceo koren");
        StabloPredavanja stablo = kandidati.stablo(predmet.getId());

        assertTrue(stablo.opis(a).iskljucena());
        assertTrue(stablo.opis(b1).iskljucena());
        assertTrue(stablo.opis(k).iskljucena());
        // koren je isključen, ali se sam ne preskače; preskaču se samo deca sa sopstvenom temom
        Opis opis = stablo.opis(k);
        assertEquals(List.of(a, b1, c), opis.listovi());
        assertEquals(putanja(b2, "B2"), opis.iskljucenePodgrupe());
        assertEquals(5, opis.brojKandidata());
    }

    @Test
    void iskljucenoDeteSePreskaceSaCelimPodstablom() {
        tema(b, "Cela grupa B");
        Opis opis = kandidati.stablo(predmet.getId()).opis(k);

        assertEquals(putanja(b, "B"), opis.iskljucenePodgrupe(), "B2 ima svoju temu, ali je ispod preskočenog B");
        assertEquals(List.of(a, c), opis.listovi());
        assertEquals(3, opis.brojKandidata());
        // sam isključeni unutrašnji čvor se obilazi (samo upozorenje), ali mu je B2 i dalje preskočen
        Opis opisB = kandidati.stablo(predmet.getId()).opis(b);
        assertTrue(opisB.iskljucena());
        assertEquals(List.of(b1), opisB.listovi());
        assertEquals(putanja(b2, "B2"), opisB.iskljucenePodgrupe());
    }

    @Test
    void stabloNePoznajeTudjeGrupeINamenuTest() {
        StabloPredavanja stablo = kandidati.stablo(predmet.getId());

        assertNull(stablo.opis(t), "namena TEST nije u stablu PREDAVANJE");
        assertNull(stablo.opis(999_999L));
        assertNull(stablo.opis(null));
        assertEquals(List.of(), stablo.polja(t));
        assertNull(kandidati.stablo(prazanPredmet.getId()).opis(k), "stablo drugog predmeta ne poznaje ove grupe");
        assertNull(kandidati.stablo(prazanPredmet.getId()).opis(a));
    }

    @Test
    void kandidatiPoListovimaSaSlikamaISemomSvogLista() {
        Kandidati rezultat = kandidati.kandidati(k);

        assertEquals(k, rezultat.opis().grupaId());
        assertEquals(List.of(3, 2), rezultat.poListovima().stream().map(List::size).toList(), "A pa B1, C je bez kandidata");

        List<KandidatVarijante> listA = rezultat.poListovima().get(0);
        assertEquals(List.of("A1", "A2", "A3"), listA.stream().map(KandidatVarijante::naziv).toList(), "po id-ju");
        for (KandidatVarijante v : listA) {
            assertEquals(a, v.grupaId());
            assertEquals(svg.get(v.naziv()), v.svgId());
            assertEquals(1, v.polja().size());
            assertEquals("a", v.polja().get(0).kljuc());
            assertEquals(Map.of("a", new OdgovorPolja(45.0, null, null, null)), v.odgovori());
        }
        assertEquals(resenjeA1, listA.get(0).resenjeSvgId());
        assertNull(listA.get(1).resenjeSvgId());

        List<KandidatVarijante> listB1 = rezultat.poListovima().get(1);
        assertEquals(List.of("B1-1", "B1-2"), listB1.stream().map(KandidatVarijante::naziv).toList());
        for (KandidatVarijante v : listB1) {
            assertEquals(b1, v.grupaId());
            assertEquals("x", v.polja().get(0).kljuc(), "šema svog lista, ne šema A");
            assertEquals(TipPolja.JEDAN_TACAN, v.polja().get(0).tip());
        }
    }

    @Test
    void kandidatiListaIIskljucenogListaIstiKaoOpis() {
        Kandidati lista = kandidati.kandidati(a);
        assertEquals(List.of(3), lista.poListovima().stream().map(List::size).toList());

        Kandidati iskljuceni = kandidati.kandidati(b2);
        assertTrue(iskljuceni.opis().iskljucena());
        assertEquals(List.of(1), iskljuceni.poListovima().stream().map(List::size).toList(),
                "isključen list se i dalje izvlači, upozorenje je na slajdu");

        Kandidati bez = kandidati.kandidati(c);
        assertEquals(0, bez.opis().brojKandidata());
        assertEquals(List.of(), bez.poListovima());
    }

    @Test
    void kandidatiNepostojeceIliTestGrupeNemajuOpis() {
        for (Long id : Arrays.asList(t, 999_999L, null)) {
            Kandidati rezultat = kandidati.kandidati(id);
            assertNull(rezultat.opis(), "grupa " + id);
            assertEquals(List.of(), rezultat.poListovima());
        }
    }

    long id(long grupa, String naziv) {
        return jdbc.queryForObject("select id from varijante where grupa_id = ? and naziv = ?", Long.class, grupa, naziv);
    }

    @Test
    void primerJePrviKandidatRedomStablaPoNivou() {
        long a1 = id(a, "A1");
        long a2 = id(a, "A2");
        long b11 = id(b1, "B1-1");
        jdbc.update("update varijante set nivo = 3 where id = ?", a1);
        StabloPredavanja stablo = kandidati.stablo(predmet.getId());

        assertEquals(a1, stablo.primer(stablo.opis(k), null), "prvi list A (rb 1), najmanji id bez obzira na nivo");
        assertEquals(a2, stablo.primer(stablo.opis(k), 2), "nivo slajda: A1 je ★★★, pa A2");
        assertEquals(a1, stablo.primer(stablo.opis(k), 3));
        assertNull(stablo.primer(stablo.opis(k), 1), "nema kandidata nivoa 1");
        assertEquals(b11, stablo.primer(stablo.opis(b), null), "B2 je isključena, B1 je prvi list");
        assertNull(stablo.primer(stablo.opis(c), null), "list bez aktivnih");
        assertNull(stablo.primer(null, null));

        assertEquals(Map.of(a1, svg.get("A1"), b11, svg.get("B1-1")), kandidati.svgTeksta(List.of(a1, b11)));
        assertEquals(Map.of(), kandidati.svgTeksta(List.of()));
    }
}
