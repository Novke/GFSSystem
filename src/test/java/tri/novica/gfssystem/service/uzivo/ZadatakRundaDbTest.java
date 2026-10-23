package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.SeedRandom;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.service.banka.LatexRender;
import tri.novica.gfssystem.service.banka.VarijantaService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runda Zadatak slajda nad pravom MySQL bazom (Flyway, kao CI): izvlačenje pri otvaranju (samo izvučene u snimku,
 * naizmenično po listovima, bez isključenih podgrupa), ravnomerna dodela pod ključem reda izvođenja (otvaranje, ponovno
 * otvaranje, kasni ulazak, isti student sa drugog telefona), komande {@code BROJ_VARIJANTI} i {@code RESENJE}, rad na
 * papiru, promena tipa slajda usred runde i brisanje dodela u nečuvanom runu. JDBC fixture u PREDAVANJE stablu: koren K
 * -> A (list, 6 AKTIVNIH sa rešenjem), B (list, 4 AKTIVNE bez rešenja), oba sa šemom {@code a} BROJ; zasebni koreni P
 * (bez polja: rad na papiru, 2 AKTIVNE bez rešenja), E (bez AKTIVNIH) i G (prazan, za brisanje). Prezentacija: ZADATAK
 * nad K ({@code brojVarijanti} 4, 120 s), ZADATAK nad P, INFO; nebodovan run bez čuvanja sa 30 učesnika kroz
 * {@link UcesnikService#prijavi}. {@link SeedRandom} daje ponovljiv ishod, ali testovi proveravaju samo osobine (koliko,
 * iz kog lista, ravnomernost). Bez {@code @Transactional}: svaka komanda se commit-uje, pa test čisti za sobom.
 */
@SpringBootTest
@Import(SeedRandom.class)
class ZadatakRundaDbTest {

    static final String SEMA_A = """
            [{"kljuc":"a","oznaka":"a)","tip":"BROJ","odstupanje":0,"odstupanjeTip":"APSOLUTNO"}]""";
    static final String NEMA_AKTIVNIH = "Grupa nema aktivnih varijanti.";
    static final String GRUPA_OBRISANA = "Grupa zadatka je obrisana.";
    static final String NEMA_ZADATKA = "Na ovom slajdu nema zadatka.";
    static final String BROJ_PRE_OTVARANJA = "Broj različitih zadataka se menja pre otvaranja.";
    static final String BROJ_GRANICE = "Broj različitih zadataka mora biti od 1 do 50.";
    static final String NEMA_RESENJA = "Nijedna varijanta nema rešenje.";
    static final String NIJE_POSTAVLJENO = "Pitanje još nije postavljeno.";
    static final String PRVO_ZATVORI = "Prvo zatvori pitanje.";
    static final String NEMA_TACNOG = "Ovo pitanje nema tačan odgovor.";

    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired UcesnikService ucesnikService;
    @Autowired StanjeService stanjeService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired VarijantaService varijantaService;
    @MockitoBean LatexRender latexRender;

    final String oznaka = UUID.randomUUID().toString().substring(0, 8);
    final List<String> mediji = new ArrayList<>();
    final List<Long> studenti = new ArrayList<>();
    final List<Long> ucesnici = new ArrayList<>();
    Predmet predmet;
    Long studentskaGrupa;
    long k, a, b, p, e, g;
    Long prezentacijaId;
    Long sK;
    Long sP;
    Long sInfo;
    Long izvodjenjeId;
    String kod;

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Zadatak runda DB " + oznaka);
        predmet = predmetRepository.save(predmet);
        k = grupa(null, "K", 1, "[]");
        a = grupa(k, "A", 1, SEMA_A);
        b = grupa(k, "B", 2, SEMA_A);
        p = grupa(null, "P", 2, "[]");
        e = grupa(null, "E", 3, SEMA_A);
        g = grupa(null, "G", 4, SEMA_A);
        for (int n = 1; n <= 6; n++) {
            varijanta(a, "A" + n, "AKTIVNA", true);
        }
        for (int n = 1; n <= 4; n++) {
            varijanta(b, "B" + n, "AKTIVNA", false);
        }
        varijanta(p, "P1", "AKTIVNA", false);
        varijanta(p, "P2", "AKTIVNA", false);
        varijanta(e, "E-nacrt", "NACRT", false);

        prezentacijaId = prezentacijaService.kreiraj(
                new CreatePrezentacijaCmd(predmet.getId(), "Zadaci na času " + oznaka, null, null)).id();
        sK = prezentacijaService.dodajSlajd(prezentacijaId, zadatak(k, 120, 4), null).id();
        sP = prezentacijaService.dodajSlajd(prezentacijaId, zadatak(p, null, null), null).id();
        sInfo = prezentacijaService.dodajSlajd(prezentacijaId,
                new SlajdCmd(TipSlajda.INFO, "Kraj", null, null, null, false, null, null, null), null).id();

        IzvodjenjeInfo info = izvodjenjeService.pokreni(prezentacijaId, new PokreniCmd(false, null, null));
        izvodjenjeId = info.id();
        kod = info.kod();
        for (int n = 1; n <= 30; n++) {
            ucesnici.add(prijavi("Student " + n));
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", prezentacijaId);
        try {
            prezentacijaService.obrisi(prezentacijaId);   // izvođenja, runde, učesnici i dodele kaskadom
        } catch (SystemException ex) {
            // već obrisana
        }
        Long id = predmet.getId();
        jdbc.update("delete from iskljucene_teme where predmet_id = ?", id);
        jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", id);
        jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        mediji.forEach(m -> jdbc.update("delete from mediji where id = ?", m));
        studenti.forEach(s -> jdbc.update("delete from studenti where id = ?", s));
        if (studentskaGrupa != null) {
            jdbc.update("delete from grupe where id = ?", studentskaGrupa);
        }
        predmetRepository.deleteById(id);
    }

    // ---------------------------------------------------------------- fixture

    long grupa(Long roditelj, String naziv, int rb, String polja) {
        Map<String, Object> red = new HashMap<>();
        red.put("predmet_id", predmet.getId());
        red.put("roditelj_id", roditelj);
        red.put("namena", "PREDAVANJE");
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

    /** Varijanta sa SVG-om zadatka (AKTIVNA je kandidat), sa ili bez SVG-a rešenja. */
    long varijanta(long grupa, String naziv, String status, boolean saResenjem) {
        Map<String, Object> red = new HashMap<>();
        red.put("grupa_id", grupa);
        red.put("rb", 1);
        red.put("naziv", naziv);
        red.put("tekst", "Zadatak " + naziv);
        red.put("odgovori", grupa == p ? "{}" : "{\"a\":{\"broj\":45.0}}");
        red.put("status", status);
        red.put("izvor", "RUCNO");
        red.put("ikad_odobrena", "AKTIVNA".equals(status));
        red.put("render_status", "OK");
        red.put("render_svg_id", medij());
        red.put("render_resenje_svg_id", saResenjem ? medij() : null);
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        return new SimpleJdbcInsert(jdbc).withTableName("varijante").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(red).longValue();
    }

    static SlajdCmd zadatak(long grupaId, Integer vreme, Integer broj) {
        return new SlajdCmd(TipSlajda.ZADATAK, null, null, null, null, false, null, null, null,
                new ZadatakSlajda(grupaId, vreme, broj));
    }

    static SlajdCmd pitanje() {
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, new PitanjeCmd(TipPitanja.JEDAN_TACAN,
                "Koliko cifara ima 8 binarno?", null, null, List.of(new OpcijaCmd("4", true), new OpcijaCmd("3", false)),
                null, null, null, null, null, null, null, null), null, null);
    }

    Long prijavi(String ime) {
        return ucesnikService.prijavi(kod, ime).info().ucesnikId();
    }

    NastavnickoStanje k(TipKomande tip) {
        return izvodjenjeService.komanda(izvodjenjeId, new KomandaCmd(tip, null));
    }

    NastavnickoStanje k(TipKomande tip, Integer vrednost) {
        return izvodjenjeService.komanda(izvodjenjeId, new KomandaCmd(tip, vrednost));
    }

    static void greska(int kod, String poruka, Executable poziv) {
        SystemException ex = assertThrows(SystemException.class, poziv);
        assertEquals(kod, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    String faza() {
        return jdbc.queryForObject("select faza from izvodjenja where id = ?", String.class, izvodjenjeId);
    }

    Long trenutnaRunda() {
        return jdbc.queryForObject("select trenutna_runda_id from izvodjenja where id = ?", Long.class, izvodjenjeId);
    }

    boolean resenjePrikazano() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("select resenje_prikazano from izvodjenja where id = ?", Boolean.class, izvodjenjeId));
    }

    Integer izmenaBroja() {
        return jdbc.queryForObject("select zadatak_broj_varijanti from izvodjenja where id = ?", Integer.class, izvodjenjeId);
    }

    SnimakRunde snimak(Long rundaId) {
        return jsonMapper.readValue(
                jdbc.queryForObject("select snimak from pitanje_runde where id = ?", String.class, rundaId), SnimakRunde.class);
    }

    ZadatakSnimak zadatakSnimak(Long rundaId) {
        return assertInstanceOf(ZadatakSnimak.class, snimak(rundaId));
    }

    /** Dodele runde: učesnik -> varijanta. */
    Map<Long, Long> dodele(Long rundaId) {
        Map<Long, Long> m = new HashMap<>();
        jdbc.query("select ucesnik_id, varijanta_id from zadatak_dodele where runda_id = ?",
                rs -> {
                    m.put(rs.getLong(1), rs.getLong(2));
                }, rundaId);
        return m;
    }

    long brojDodelaUcesnika(Long ucesnikId) {
        return jdbc.queryForObject("select count(*) from zadatak_dodele where ucesnik_id = ?", Long.class, ucesnikId);
    }

    long brojRundiSlajda(Long slajdId) {
        return jdbc.queryForObject("select count(*) from pitanje_runde where izvodjenje_id = ? and slajd_id = ?",
                Long.class, izvodjenjeId, slajdId);
    }

    static Map<Long, Integer> poVarijanti(Map<Long, Long> dodele) {
        Map<Long, Integer> m = new HashMap<>();
        dodele.values().forEach(v -> m.merge(v, 1, Integer::sum));
        return m;
    }

    static Set<Long> idjevi(ZadatakSnimak s) {
        Set<Long> ids = new HashSet<>();
        s.varijante().forEach(v -> ids.add(v.varijantaId()));
        return ids;
    }

    /** Svaka dodeljena varijanta je u snimku, a brojevi po izvučenoj varijanti se razlikuju najviše za 1. */
    static void ravnomerno(ZadatakSnimak s, Map<Long, Long> dodele) {
        Set<Long> uSnimku = idjevi(s);
        assertTrue(uSnimku.containsAll(dodele.values()), "dodeljena je samo izvučena varijanta");
        Map<Long, Integer> broj = poVarijanti(dodele);
        List<Integer> brojevi = uSnimku.stream().map(v -> broj.getOrDefault(v, 0)).toList();
        assertTrue(Collections.max(brojevi) - Collections.min(brojevi) <= 1, "ravnomerno: " + broj);
    }

    /** Otvara ZADATAK nad K (prvi slajd) i vraća id runde. */
    Long otvoriK() {
        k(TipKomande.IDI_NA, 0);
        k(TipKomande.OTVORI_ZATVORI);
        return trenutnaRunda();
    }

    // ---------------------------------------------------------------- izvlačenje i dodela

    @Test
    void otvaranjeIzvlaci4Od10IRavnomernoDeli() {
        k(TipKomande.IDI_NA, 0);
        assertEquals("CEKA", faza());
        k(TipKomande.OTVORI_ZATVORI);

        assertEquals("OTVORENO", faza());
        Long r = trenutnaRunda();
        ZadatakSnimak s = zadatakSnimak(r);
        assertEquals(sK, s.slajdId());
        assertEquals(k, s.grupaId());
        assertEquals("K", s.grupaNaziv());
        assertEquals(120, s.vremeSekunde());
        assertEquals(4, s.varijante().size());
        assertEquals(2, s.varijante().stream().filter(v -> v.grupaId() == a).count(), "naizmenično po listovima");
        assertEquals(2, s.varijante().stream().filter(v -> v.grupaId() == b).count());
        assertEquals(4, idjevi(s).size(), "bez ponavljanja");
        s.varijante().forEach(v -> {
            assertNotNull(v.svgId());
            assertEquals("a", v.polja().get(0).kljuc());
            assertEquals(45.0, v.odgovori().get("a").broj());
        });
        // tajmer slajda
        assertEquals(120_000L, jdbc.queryForObject("select trajanje_ms from pitanje_runde where id = ?", Long.class, r));
        assertNotNull(jdbc.queryForObject("select rok from pitanje_runde where id = ?", LocalDateTime.class, r));

        Map<Long, Long> d = dodele(r);
        assertEquals(new HashSet<>(ucesnici), d.keySet(), "svih 30 učesnika ima dodelu");
        ravnomerno(s, d);
        assertEquals(4, poVarijanti(d).size(), "svaka izvučena je nekome dodeljena");
    }

    @Test
    void ponoviIzvlaciPonovo() {
        Long prva = otvoriK();
        k(TipKomande.OTVORI_ZATVORI);
        assertEquals("ZATVORENO", faza());
        k(TipKomande.PONOVI);

        Long druga = trenutnaRunda();
        assertNotEquals(prva, druga);
        assertEquals("OTVORENO", faza());
        ZadatakSnimak s = zadatakSnimak(druga);
        assertEquals(4, s.varijante().size());
        Map<Long, Long> d = dodele(druga);
        assertEquals(30, d.size());
        ravnomerno(s, d);
        assertEquals(30, dodele(prva).size(), "stara runda zadržava svoje dodele");
        assertEquals(2, brojRundiSlajda(sK));
    }

    @Test
    void kasniUlazakDobijaNajmanjeDodeljenu() {
        Long r = otvoriK();
        Map<Long, Integer> pre = poVarijanti(dodele(r));
        int najmanje = Collections.min(pre.values());

        Long kasni = prijavi("Kasni");

        assertEquals(1, brojDodelaUcesnika(kasni));
        Long varijanta = dodele(r).get(kasni);
        assertEquals(najmanje, pre.get(varijanta), "kasni dobija varijantu sa najmanje dodela: " + pre);
        assertEquals(31, dodele(r).size());
        ravnomerno(zadatakSnimak(r), dodele(r));
    }

    @Test
    void ponovnoOtvaranjeDodeljujeNovima() {
        Long r = otvoriK();
        k(TipKomande.OTVORI_ZATVORI);
        Long novi = prijavi("Posle zatvaranja");
        assertEquals(0, brojDodelaUcesnika(novi), "dok je zatvoreno nema dodele");

        k(TipKomande.OTVORI_ZATVORI);

        assertEquals("OTVORENO", faza());
        assertEquals(r, trenutnaRunda(), "ista runda");
        assertEquals(1, brojDodelaUcesnika(novi));
        Map<Long, Long> d = dodele(r);
        assertEquals(31, d.size());
        assertEquals(31L, jdbc.queryForObject("select count(*) from zadatak_dodele where runda_id = ?", Long.class, r),
                "niko nema dve dodele");
        ravnomerno(zadatakSnimak(r), d);
    }

    @Test
    void istiStudentDrugiTelefonIstaDodela() {
        jdbc.update("insert into grupe (naziv, godina_upisa) values (?, 2025)", "ZR-" + oznaka);
        studentskaGrupa = jdbc.queryForObject("select id from grupe where naziv = ?", Long.class, "ZR-" + oznaka);
        jdbc.update("insert into studenti (ime, prezime, godina, indeks, grupa_id) values ('Mira', 'Jović', 2025, ?, ?)",
                "ZR-" + oznaka, studentskaGrupa);
        Long student = jdbc.queryForObject("select id from studenti where indeks = ?", Long.class, "ZR-" + oznaka);
        studenti.add(student);
        Long r = otvoriK();

        Long prvi = ucesnikService.prijavi(kod, "Prvi telefon", Optional.of(student)).info().ucesnikId();
        assertEquals(1, brojDodelaUcesnika(prvi));
        Long varijanta = dodele(r).get(prvi);

        Long drugi = ucesnikService.prijavi(kod, "Drugi telefon", Optional.of(student)).info().ucesnikId();

        assertEquals(prvi, drugi, "isti student je isti učesnik");
        assertEquals(1, brojDodelaUcesnika(prvi));
        assertEquals(varijanta, dodele(r).get(prvi));
    }

    // ---------------------------------------------------------------- broj varijanti

    @Test
    void brojVarijantiUKonzoliPreOtvaranja() {
        Long sK2 = prezentacijaService.dodajSlajd(prezentacijaId, zadatak(k, null, 4), null).id();   // indeks 3
        Long sK3 = prezentacijaService.dodajSlajd(prezentacijaId, zadatak(k, null, 4), null).id();   // indeks 4
        Long sK4 = prezentacijaService.dodajSlajd(prezentacijaId, zadatak(k, null, 4), null).id();   // indeks 5

        k(TipKomande.IDI_NA, 2);
        greska(409, NEMA_ZADATKA, () -> k(TipKomande.BROJ_VARIJANTI, 2));

        k(TipKomande.IDI_NA, 0);
        greska(400, BROJ_GRANICE, () -> k(TipKomande.BROJ_VARIJANTI, 51));
        greska(400, BROJ_GRANICE, () -> k(TipKomande.BROJ_VARIJANTI, -1));
        k(TipKomande.BROJ_VARIJANTI, 2);
        assertEquals(2, izmenaBroja());
        k(TipKomande.OTVORI_ZATVORI);
        assertEquals(2, zadatakSnimak(trenutnaRunda()).varijante().size());
        greska(409, BROJ_PRE_OTVARANJA, () -> k(TipKomande.BROJ_VARIJANTI, 3));
        k(TipKomande.OTVORI_ZATVORI);
        greska(409, BROJ_PRE_OTVARANJA, () -> k(TipKomande.BROJ_VARIJANTI, 3));
        k(TipKomande.PONOVI);
        assertEquals(2, zadatakSnimak(trenutnaRunda()).varijante().size(), "izmena važi i za PONOVI");

        k(TipKomande.IDI_NA, 3);
        assertNull(izmenaBroja(), "prelazak na drugi slajd briše izmenu");
        k(TipKomande.BROJ_VARIJANTI, 0);
        k(TipKomande.OTVORI_ZATVORI);
        ZadatakSnimak sve = zadatakSnimak(trenutnaRunda());
        assertEquals(sK2, sve.slajdId());
        assertEquals(10, sve.varijante().size(), "0 = sve aktivne");

        k(TipKomande.IDI_NA, 4);
        k(TipKomande.BROJ_VARIJANTI, 2);
        k(TipKomande.BROJ_VARIJANTI, null);
        assertNull(izmenaBroja());
        k(TipKomande.OTVORI_ZATVORI);
        ZadatakSnimak kaoSlajd = zadatakSnimak(trenutnaRunda());
        assertEquals(sK3, kaoSlajd.slajdId());
        assertEquals(4, kaoSlajd.varijante().size(), "null = kao na slajdu");

        k(TipKomande.IDI_NA, 5);
        k(TipKomande.BROJ_VARIJANTI, 1);
        k(TipKomande.IDI_NA, 2);
        k(TipKomande.IDI_NA, 5);
        assertEquals("CEKA", faza());
        assertNull(izmenaBroja());
        k(TipKomande.OTVORI_ZATVORI);
        ZadatakSnimak posleIdiNa = zadatakSnimak(trenutnaRunda());
        assertEquals(sK4, posleIdiNa.slajdId());
        assertEquals(4, posleIdiNa.varijante().size(), "izmena je obrisana pri prelasku na drugi slajd i nazad");
    }

    @Test
    void brojVeciOdKandidataDajeSve() {
        prezentacijaService.dodajSlajd(prezentacijaId, zadatak(b, null, 50), null);
        k(TipKomande.IDI_NA, 3);
        k(TipKomande.OTVORI_ZATVORI);

        ZadatakSnimak s = zadatakSnimak(trenutnaRunda());
        assertEquals(4, s.varijante().size());
        assertTrue(s.varijante().stream().allMatch(v -> v.grupaId() == b));
        ravnomerno(s, dodele(trenutnaRunda()));
    }

    @Test
    void bezAktivnih409() {
        Long sE = prezentacijaService.dodajSlajd(prezentacijaId, zadatak(e, null, null), null).id();   // indeks 3
        Long sG = prezentacijaService.dodajSlajd(prezentacijaId, zadatak(g, null, null), null).id();   // indeks 4

        k(TipKomande.IDI_NA, 3);
        greska(409, NEMA_AKTIVNIH, () -> k(TipKomande.OTVORI_ZATVORI));
        greska(409, NEMA_AKTIVNIH, () -> k(TipKomande.SLEDECI));
        assertEquals("CEKA", faza());
        assertNull(trenutnaRunda());
        assertEquals(0, brojRundiSlajda(sE));

        jdbc.update("delete from grupe_zadataka where id = ?", g);   // slajd ostaje bez grupe (ON DELETE SET NULL)
        k(TipKomande.IDI_NA, 4);
        greska(409, GRUPA_OBRISANA, () -> k(TipKomande.OTVORI_ZATVORI));
        assertEquals("CEKA", faza());
        assertEquals(0, brojRundiSlajda(sG));
    }

    @Test
    void iskljucenaPodgrupaSeNeIzvlaci() {
        jdbc.update("insert into iskljucene_teme (predmet_id, opis, grupa_id, izvor, kreirano) values (?, ?, ?, 'RUCNO', now(6))",
                predmet.getId(), "Tema B", b);
        k(TipKomande.IDI_NA, 0);
        k(TipKomande.BROJ_VARIJANTI, 0);
        k(TipKomande.OTVORI_ZATVORI);

        ZadatakSnimak s = zadatakSnimak(trenutnaRunda());
        assertEquals(6, s.varijante().size());
        assertTrue(s.varijante().stream().allMatch(v -> v.grupaId() == a), "B je isključena");
    }

    // ---------------------------------------------------------------- rešenje, rad na papiru

    @Test
    void resenjeKomanda() {
        k(TipKomande.IDI_NA, 0);
        greska(409, NIJE_POSTAVLJENO, () -> k(TipKomande.RESENJE));
        k(TipKomande.OTVORI_ZATVORI);
        greska(409, PRVO_ZATVORI, () -> k(TipKomande.RESENJE));
        k(TipKomande.OTVORI_ZATVORI);

        k(TipKomande.RESENJE);
        assertTrue(resenjePrikazano());
        k(TipKomande.RESENJE);
        assertFalse(resenjePrikazano(), "prekidač");

        k(TipKomande.RESENJE);
        k(TipKomande.IDI_NA, 2);
        assertFalse(resenjePrikazano(), "IDI_NA gasi rešenje");
        greska(409, NEMA_ZADATKA, () -> k(TipKomande.RESENJE));

        k(TipKomande.IDI_NA, 0);
        assertEquals("ZATVORENO", faza());
        assertFalse(resenjePrikazano());
        k(TipKomande.RESENJE);
        k(TipKomande.PONOVI);
        assertFalse(resenjePrikazano(), "PONOVI gasi rešenje");

        // ponovno otvaranje iste runde gasi rešenje
        k(TipKomande.OTVORI_ZATVORI);
        k(TipKomande.RESENJE);
        k(TipKomande.OTVORI_ZATVORI);
        assertEquals("OTVORENO", faza());
        assertFalse(resenjePrikazano());

        // P: nijedna varijanta nema rešenje
        k(TipKomande.IDI_NA, 1);
        k(TipKomande.OTVORI_ZATVORI);
        k(TipKomande.OTVORI_ZATVORI);
        greska(409, NEMA_RESENJA, () -> k(TipKomande.RESENJE));
        assertFalse(resenjePrikazano());
    }

    @Test
    void radNaPapiru() {
        k(TipKomande.IDI_NA, 1);
        k(TipKomande.OTVORI_ZATVORI);
        Long r = trenutnaRunda();
        ZadatakSnimak s = zadatakSnimak(r);
        assertEquals(2, s.varijante().size());
        assertFalse(s.imaTacanOdgovor());
        assertTrue(s.varijante().stream().allMatch(v -> v.polja().isEmpty()));
        assertEquals(30, dodele(r).size(), "i rad na papiru ima dodele");
        greska(409, NEMA_TACNOG, () -> k(TipKomande.TACAN));

        k(TipKomande.OTVORI_ZATVORI);
        greska(409, NEMA_TACNOG, () -> k(TipKomande.TACAN));
        k(TipKomande.REZULTATI);
    }

    // ---------------------------------------------------------------- tip slajda usred runde, nečuvan run

    @Test
    void promenaTipaSlajdaUsredRunde() {
        Long zadatakRunda = otvoriK();

        prezentacijaService.izmeniSlajd(sK, pitanje());

        assertNotNull(jdbc.queryForObject("select zatvoreno from pitanje_runde where id = ?", LocalDateTime.class,
                zadatakRunda), "otvorena ZADATAK runda je zatvorena");
        assertEquals("CEKA", faza());
        assertNull(trenutnaRunda());
        assertDoesNotThrow(() -> stanjeService.snimci(izvodjenjeId));

        // PITANJE runda, pa slajd postaje ZADATAK
        k(TipKomande.OTVORI_ZATVORI);
        Long pitanjeRunda = trenutnaRunda();
        assertInstanceOf(PitanjeSnimak.class, snimak(pitanjeRunda));
        k(TipKomande.OTVORI_ZATVORI);
        assertEquals("ZATVORENO", faza());
        prezentacijaService.izmeniSlajd(sK, zadatak(k, 120, 4));
        assertEquals("CEKA", faza());
        assertNull(trenutnaRunda());
        assertDoesNotThrow(() -> stanjeService.snimci(izvodjenjeId));

        k(TipKomande.IDI_NA, 2);
        k(TipKomande.IDI_NA, 0);
        assertEquals("CEKA", faza(), "stara PITANJE runda se ne uzima za ZADATAK slajd");
        assertNull(trenutnaRunda());

        k(TipKomande.OTVORI_ZATVORI);
        Long nova = trenutnaRunda();
        assertNotEquals(pitanjeRunda, nova);
        assertEquals(sK, zadatakSnimak(nova).slajdId());
        assertEquals(30, dodele(nova).size());
        assertEquals(3, jdbc.queryForObject("select redni_broj from pitanje_runde where id = ?", Integer.class, nova));
        assertDoesNotThrow(() -> stanjeService.snimci(izvodjenjeId));
    }

    // ---------------------------------------------------------------- brisanje varijante i otvaranje runde

    List<Long> varijanteGrupe(long grupaId) {
        return jdbc.queryForList("select id from varijante where grupa_id = ? order by id", Long.class, grupaId);
    }

    /** Završava run iz setUp-a i pokreće nov sa {@code brojUcesnika} učesnika (isti slajdovi). */
    void novRun(int brojUcesnika) {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where id = ?", izvodjenjeId);
        IzvodjenjeInfo info = izvodjenjeService.pokreni(prezentacijaId, new PokreniCmd(false, null, null));
        izvodjenjeId = info.id();
        kod = info.kod();
        ucesnici.clear();
        for (int n = 1; n <= brojUcesnika; n++) {
            ucesnici.add(prijavi("Novi " + n));
        }
    }

    /**
     * Nalaz recenzije BE-5b: varijanta obrisana dok se runda otvara (brisanje drži ključ reda, otvaranje je već pročitalo
     * kandidate) ne sme ući u snimak; inače bi je svaki kasni ulazak birao (0 dodela) i pao na FK. Druga konekcija briše P1
     * i drži ključ dok otvaranje ne stigne do {@code zakljucajKandidate}, pa commit-uje.
     */
    @Test
    void varijantaObrisanaDokSeRundaOtvaraNijeUSnimku() throws Exception {
        List<Long> pVarijante = varijanteGrupe(p);
        Long p1 = pVarijante.get(0);
        Long p2 = pVarijante.get(1);
        k(TipKomande.IDI_NA, 1);

        CountDownLatch obrisano = new CountDownLatch(1);
        AtomicReference<Exception> greskaBrisanja = new AtomicReference<>();
        Thread brisanje = new Thread(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement("delete from varijante where id = ?")) {
                    ps.setLong(1, p1);
                    ps.executeUpdate();
                }
                obrisano.countDown();
                Thread.sleep(1500);   // otvaranje za to vreme pročita kandidate i čeka ključ P1
                c.commit();
            } catch (Exception ex) {
                greskaBrisanja.set(ex);
                obrisano.countDown();
            }
        });
        brisanje.start();
        assertTrue(obrisano.await(10, TimeUnit.SECONDS));
        k(TipKomande.OTVORI_ZATVORI);
        brisanje.join(10_000);
        assertNull(greskaBrisanja.get());

        Long r = trenutnaRunda();
        assertEquals("OTVORENO", faza());
        assertEquals(Set.of(p2), idjevi(zadatakSnimak(r)), "obrisana varijanta nije izvučena");
        assertEquals(Set.of(p2), new HashSet<>(dodele(r).values()));
        Long kasni = prijavi("Kasni");
        assertEquals(p2, dodele(r).get(kasni), "kasni ulazak radi i dobija varijantu koja postoji");
    }

    /** Sve varijante obrisane dok se runda otvara: 409 bez kandidata, faza ostaje CEKA, nema runde. */
    @Test
    void sveObrisaneDokSeRundaOtvara409() throws Exception {
        k(TipKomande.IDI_NA, 1);

        CountDownLatch obrisano = new CountDownLatch(1);
        AtomicReference<Exception> greskaBrisanja = new AtomicReference<>();
        Thread brisanje = new Thread(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement("delete from varijante where grupa_id = ?")) {
                    ps.setLong(1, p);
                    ps.executeUpdate();
                }
                obrisano.countDown();
                Thread.sleep(1500);
                c.commit();
            } catch (Exception ex) {
                greskaBrisanja.set(ex);
                obrisano.countDown();
            }
        });
        brisanje.start();
        assertTrue(obrisano.await(10, TimeUnit.SECONDS));
        greska(409, NEMA_AKTIVNIH, () -> k(TipKomande.OTVORI_ZATVORI));
        brisanje.join(10_000);
        assertNull(greskaBrisanja.get());

        assertEquals("CEKA", faza());
        assertNull(trenutnaRunda());
        assertEquals(0, brojRundiSlajda(sP));
    }

    /**
     * Manje učesnika od izvučenih varijanti: izvučena, a nedodeljena varijanta je "korišćena na času" (snimak), pa se ne
     * briše (409), i kasni učesnik je posle toga dobija bez greške.
     */
    @Test
    void izvucenaNedodeljenaSeNeBriseKasniUlazi() {
        novRun(1);
        Long r = otvoriK();
        ZadatakSnimak s = zadatakSnimak(r);
        assertEquals(4, s.varijante().size());
        Set<Long> dodeljene = new HashSet<>(dodele(r).values());
        assertEquals(1, dodeljene.size());
        Long nedodeljena = idjevi(s).stream().filter(v -> !dodeljene.contains(v)).findFirst().orElseThrow();

        greska(409, "Korišćeno na času; arhiviraj.", () -> varijantaService.obrisi(nedodeljena));

        Long kasni = prijavi("Kasni");
        Long dobio = dodele(r).get(kasni);
        assertNotNull(dobio);
        assertFalse(dodeljene.contains(dobio), "kasni dobija varijantu bez dodela");
        assertEquals(1, jdbc.queryForObject("select count(*) from varijante where id = ?", Integer.class, nedodeljena));
    }

    @Test
    void necuvanRunBriseDodele() {
        Long r = otvoriK();
        assertEquals(30, dodele(r).size());

        k(TipKomande.ZAVRSI);

        assertEquals(0L, jdbc.queryForObject("select count(*) from zadatak_dodele where runda_id = ?", Long.class, r));
        assertEquals(0L, jdbc.queryForObject("select count(*) from pitanje_runde where izvodjenje_id = ?", Long.class,
                izvodjenjeId));
    }

    // ---------------------------------------------------------------- nivo varijanti

    static final String NIVO_PRE_OTVARANJA = "Nivo se menja pre otvaranja.";

    /** A1, A2 i B3, B4 su nivo 1; B1, B2 nivo 3; ostale (A3-A6) ostaju na podrazumevanom 2. */
    void postaviNivoe() {
        jdbc.update("update varijante set nivo = 1 where grupa_id = ? and naziv in ('A1', 'A2')", a);
        jdbc.update("update varijante set nivo = 1 where grupa_id = ? and naziv in ('B3', 'B4')", b);
        jdbc.update("update varijante set nivo = 3 where grupa_id = ? and naziv in ('B1', 'B2')", b);
    }

    static SlajdCmd zadatakNivoa(long grupaId, int nivo) {
        return new SlajdCmd(TipSlajda.ZADATAK, null, null, null, null, false, null, null, null,
                new ZadatakSlajda(grupaId, null, null, nivo));
    }

    static void sviNivoa(int nivo, ZadatakSnimak s) {
        assertEquals(nivo, s.nivo());
        assertTrue(s.varijante().stream().allMatch(v -> v.nivo() == nivo), "svi izvučeni su nivoa " + nivo);
    }

    @Test
    void nivoSaSlajdaIzvlaciSamoTajNivoIzSvihListova() {
        postaviNivoe();
        prezentacijaService.dodajSlajd(prezentacijaId, zadatakNivoa(k, 1), null);   // indeks 3
        k(TipKomande.IDI_NA, 3);
        k(TipKomande.OTVORI_ZATVORI);

        ZadatakSnimak s = zadatakSnimak(trenutnaRunda());
        assertEquals(4, s.varijante().size(), "A1, A2, B3, B4");
        sviNivoa(1, s);
        assertEquals(Set.of(a, b), new HashSet<>(s.varijante().stream().map(VarijantaSnimak::grupaId).toList()),
                "unutrašnji čvor i dalje izvlači naizmenično iz listova");
        Map<Long, Long> d = dodele(trenutnaRunda());
        assertEquals(30, d.size());
        ravnomerno(s, d);

        Long kasni = prijavi("Kasni");
        Long varijantaKasnog = dodele(trenutnaRunda()).get(kasni);
        assertTrue(idjevi(s).contains(varijantaKasnog), "kasni dobija varijantu istog nivoa");
    }

    @Test
    void nivoBezKandidataJe409SaNivoomUPoruci() {
        postaviNivoe();
        prezentacijaService.dodajSlajd(prezentacijaId, zadatakNivoa(a, 3), null);   // indeks 3: A nema nivo 3
        k(TipKomande.IDI_NA, 3);
        greska(409, "Grupa nema aktivnih varijanti nivoa ★★★.", () -> k(TipKomande.OTVORI_ZATVORI));
        assertEquals("CEKA", faza());
        assertNull(trenutnaRunda());
    }

    @Test
    void nivoUKonzoliPreOtvaranjaVaziZaPonoviIBriseSePrelaskom() {
        postaviNivoe();
        k(TipKomande.IDI_NA, 2);
        greska(409, NEMA_ZADATKA, () -> k(TipKomande.NIVO, 1));

        k(TipKomande.IDI_NA, 0);   // sK: 4 varijante, bez nivoa
        greska(400, "Nivo mora biti od 1 do 3.", () -> k(TipKomande.NIVO, 4));
        greska(400, "Nivo mora biti od 1 do 3.", () -> k(TipKomande.NIVO, -1));
        NastavnickoStanje st = k(TipKomande.NIVO, 3);
        assertEquals(3, st.zadatak().izmenaNivoa());
        assertEquals(3, st.zadatak().nivoZaOtvaranje());
        assertEquals(2, st.zadatak().brojZaOtvaranje(), "min(4 sa slajda, 2 kandidata nivoa 3)");
        k(TipKomande.OTVORI_ZATVORI);
        ZadatakSnimak s = zadatakSnimak(trenutnaRunda());
        assertEquals(2, s.varijante().size());
        sviNivoa(3, s);
        greska(409, NIVO_PRE_OTVARANJA, () -> k(TipKomande.NIVO, 1));
        k(TipKomande.OTVORI_ZATVORI);
        greska(409, NIVO_PRE_OTVARANJA, () -> k(TipKomande.NIVO, 1));
        k(TipKomande.PONOVI);
        sviNivoa(3, zadatakSnimak(trenutnaRunda()));

        k(TipKomande.IDI_NA, 1);
        k(TipKomande.IDI_NA, 0);
        assertNull(jdbc.queryForObject("select zadatak_nivo from izvodjenja where id = ?", Integer.class, izvodjenjeId),
                "prelazak na drugi slajd briše izmenu nivoa");

        // slajd sa nivoom 1: konzola 0 = svi nivoi, null = kao na slajdu
        prezentacijaService.dodajSlajd(prezentacijaId, zadatakNivoa(k, 1), null);   // indeks 3
        k(TipKomande.IDI_NA, 3);
        k(TipKomande.BROJ_VARIJANTI, 0);
        NastavnickoStanje svi = k(TipKomande.NIVO, 0);
        assertNull(svi.zadatak().nivoZaOtvaranje());
        assertEquals(10, svi.zadatak().brojZaOtvaranje());
        k(TipKomande.NIVO, null);
        k(TipKomande.OTVORI_ZATVORI);
        ZadatakSnimak kaoSlajd = zadatakSnimak(trenutnaRunda());
        assertEquals(4, kaoSlajd.varijante().size());
        sviNivoa(1, kaoSlajd);
    }

    @Test
    void bezNivoaIzvlaciIzSvihNivoaKaoRanije() {
        postaviNivoe();
        k(TipKomande.IDI_NA, 0);
        k(TipKomande.BROJ_VARIJANTI, 0);
        k(TipKomande.OTVORI_ZATVORI);
        ZadatakSnimak s = zadatakSnimak(trenutnaRunda());
        assertNull(s.nivo());
        assertEquals(10, s.varijante().size());
        assertEquals(Set.of(1, 2, 3), new HashSet<>(s.varijante().stream().map(VarijantaSnimak::nivo).toList()));
    }
}
