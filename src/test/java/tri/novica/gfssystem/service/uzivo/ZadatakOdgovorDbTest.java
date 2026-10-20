package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.SeedRandom;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.dto.banka.VarijantaCmd;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.service.banka.LatexPreambulaService;
import tri.novica.gfssystem.service.banka.LatexRender;
import tri.novica.gfssystem.service.banka.VarijantaService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Odgovor na Zadatak slajd nad pravom MySQL bazom: ocena po dodeljenoj varijanti iz snimka runde (ne po trenutnoj banci),
 * upis polja, odbijanja (bez dodele, već odgovorio, zatvoreno, rad na papiru) i opstanak SVG-ova snimka posle izmene i
 * prisilnog rendera varijante. Fixture: koren K (list, šema {@code a} BROJ) sa A (a=45) i B (a=13), obe AKTIVNE i
 * odobrene, sa SVG-om zadatka i rešenja kroz {@link MedijService} (fajl postoji); koren P bez polja (papir). Prezentacija:
 * ZADATAK nad K (sve varijante), ZADATAK nad P, PITANJE JEDAN_TACAN; nebodovan run sa dva učesnika, runda nad K otvorena:
 * svaki učesnik ima drugu varijantu. Bez {@code @Transactional}; test čisti za sobom.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SeedRandom.class)
class ZadatakOdgovorDbTest {

    static final String SEMA_A = """
            [{"kljuc":"a","oznaka":"a)","tip":"BROJ","odstupanje":0,"odstupanjeTip":"APSOLUTNO"}]""";
    static final AtomicInteger BROJAC = new AtomicInteger();

    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired UcesnikService ucesnikService;
    @Autowired OdgovorService odgovorService;
    @Autowired VarijantaService varijantaService;
    @Autowired LatexPreambulaService preambulaService;
    @Autowired MedijService medijService;
    @Autowired PredmetRepository predmetRepository;
    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean LatexRender latexRender;

    final String oznaka = UUID.randomUUID().toString().substring(0, 8);
    final List<String> mediji = new ArrayList<>();
    Predmet predmet;
    long k, p, varA, varB;
    Long prezentacijaId, izvodjenjeId, rundaK;
    String kod;
    Long naA, naB;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(anyString(), anyString(), anyList())).thenAnswer(i -> render());
        when(latexRender.renderuj(anyList(), anyString(), anyList())).thenAnswer(i -> render());

        predmet = new Predmet();
        predmet.setNaziv("Zadatak odgovor DB " + oznaka);
        predmet = predmetRepository.save(predmet);
        k = grupa("K", 1, SEMA_A);
        p = grupa("P", 2, "[]");
        varA = varijanta(k, "A", "{\"a\":{\"broj\":45.0}}");
        varB = varijanta(k, "B", "{\"a\":{\"broj\":13.0}}");
        varijanta(p, "P1", "{}");

        prezentacijaId = prezentacijaService.kreiraj(
                new CreatePrezentacijaCmd(predmet.getId(), "Odgovori " + oznaka, null, null)).id();
        prezentacijaService.dodajSlajd(prezentacijaId, zadatak(k), null);
        prezentacijaService.dodajSlajd(prezentacijaId, zadatak(p), null);
        prezentacijaService.dodajSlajd(prezentacijaId, new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false,
                new PitanjeCmd(TipPitanja.JEDAN_TACAN, "Koliko cifara ima 8 binarno?", null, null,
                        List.of(new OpcijaCmd("4", true), new OpcijaCmd("3", false)),
                        null, null, null, null, null, null, null, null), null, null), null);

        IzvodjenjeInfo info = izvodjenjeService.pokreni(prezentacijaId, new PokreniCmd(false, null, null));
        izvodjenjeId = info.id();
        kod = info.kod();
        Long u1 = ucesnikService.prijavi(kod, "Prvi").info().ucesnikId();
        Long u2 = ucesnikService.prijavi(kod, "Drugi").info().ucesnikId();
        k(TipKomande.IDI_NA, 0);
        k(TipKomande.OTVORI_ZATVORI, null);
        rundaK = trenutnaRunda();
        Long v1 = dodela(u1);
        assertNotEquals(v1, dodela(u2), "dva učesnika, dve varijante: svako svoju");
        naA = v1 == varA ? u1 : u2;
        naB = v1 == varA ? u2 : u1;
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", prezentacijaId);
        try {
            prezentacijaService.obrisi(prezentacijaId);
        } catch (SystemException ex) {
            // već obrisana
        }
        Long id = predmet.getId();
        List<String> svg = jdbc.queryForList("select render_svg_id from varijante where render_svg_id is not null and grupa_id in "
                + "(select id from grupe_zadataka where predmet_id = ?) union select render_resenje_svg_id from varijante "
                + "where render_resenje_svg_id is not null and grupa_id in (select id from grupe_zadataka where predmet_id = ?)",
                String.class, id, id);
        jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        mediji.addAll(svg);
        mediji.forEach(m -> jdbc.update("delete from mediji where id = ?", m));
        predmetRepository.deleteById(id);
    }

    // ---------------------------------------------------------------- fixture

    static RenderOdgovor render() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>" + BROJAC.incrementAndGet() + "</text></svg>";
        return new RenderOdgovor(true, Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)),
                null, null, null, 9L, false);
    }

    long grupa(String naziv, int rb, String polja) {
        Map<String, Object> red = new HashMap<>();
        red.put("predmet_id", predmet.getId());
        red.put("namena", "PREDAVANJE");
        red.put("naziv", naziv);
        red.put("rb", rb);
        red.put("tezina", 3);
        red.put("polja", polja);
        red.put("izvor", "RUCNO");
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        return new SimpleJdbcInsert(jdbc).withTableName("grupe_zadataka").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(red).longValue();
    }

    String svg(String naziv) {
        String id = medijService.sacuvajRender(
                ("<svg xmlns=\"http://www.w3.org/2000/svg\"><text>" + naziv + "</text></svg>").getBytes(StandardCharsets.UTF_8),
                naziv + ".svg").id();
        mediji.add(id);
        return id;
    }

    long varijanta(long grupa, String naziv, String odgovori) {
        Map<String, Object> red = new HashMap<>();
        red.put("grupa_id", grupa);
        red.put("rb", 1);
        red.put("naziv", naziv);
        red.put("tekst", "Zadatak " + naziv);
        red.put("resenje", "Rešenje " + naziv);
        red.put("odgovori", odgovori);
        red.put("status", "AKTIVNA");
        red.put("izvor", "RUCNO");
        red.put("ikad_odobrena", true);
        red.put("render_status", "OK");
        red.put("render_svg_id", svg(naziv));
        red.put("render_resenje_svg_id", svg(naziv + "-resenje"));
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        return new SimpleJdbcInsert(jdbc).withTableName("varijante").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(red).longValue();
    }

    static SlajdCmd zadatak(long grupaId) {
        return new SlajdCmd(TipSlajda.ZADATAK, null, null, null, null, false, null, null, null,
                new ZadatakSlajda(grupaId, null, null));
    }

    void k(TipKomande tip, Integer vrednost) {
        izvodjenjeService.komanda(izvodjenjeId, new KomandaCmd(tip, vrednost));
    }

    Long trenutnaRunda() {
        return jdbc.queryForObject("select trenutna_runda_id from izvodjenja where id = ?", Long.class, izvodjenjeId);
    }

    Long dodela(Long ucesnik) {
        return jdbc.queryForObject("select varijanta_id from zadatak_dodele where runda_id = ? and ucesnik_id = ?",
                Long.class, rundaK, ucesnik);
    }

    void odgovori(Long ucesnik, Map<String, Object> polja) {
        odgovorService.odgovori(izvodjenjeId, ucesnik, new OdgovorCmd(trenutnaRunda(), null, null, null, null, polja));
    }

    static void odbijen(String poruka, Runnable poziv) {
        OdgovorOdbijen ex = assertThrows(OdgovorOdbijen.class, poziv::run);
        assertEquals(poruka, ex.getMessage());
    }

    Map<String, Object> red(Long ucesnik) {
        return jdbc.queryForMap("select varijanta_id, tacno, polja, broj, opcije, tekst from odgovori "
                + "where runda_id = ? and ucesnik_id = ?", rundaK, ucesnik);
    }

    // ---------------------------------------------------------------- testovi

    @Test
    void ocenaPoDodeljenojVarijanti() {
        odgovori(naA, Map.of("a", "45"));
        Map<String, Object> a = red(naA);
        assertEquals(varA, ((Number) a.get("varijanta_id")).longValue());
        assertEquals(Boolean.TRUE, a.get("tacno"));
        assertEquals(jsonMapper.readTree("{\"a\":{\"vrednost\":\"45\",\"tacno\":true}}"),
                jsonMapper.readTree((String) a.get("polja")));
        assertNull(a.get("broj"));
        assertNull(a.get("opcije"));
        assertNull(a.get("tekst"));

        odgovori(naB, Map.of("a", "45"));
        Map<String, Object> b = red(naB);
        assertEquals(varB, ((Number) b.get("varijanta_id")).longValue());
        assertEquals(Boolean.FALSE, b.get("tacno"), "ocena po varijanti B (a=13)");

        odbijen("Već si odgovorio.", () -> odgovori(naA, Map.of("a", "45")));
    }

    @Test
    void odbijanja() {
        odbijen("Popuni sva polja.", () -> odgovori(naA, Map.of()));
        odbijen("Polje „a)“: unesi broj.", () -> odgovori(naA, Map.of("a", "četrdeset")));
        jdbc.update("delete from zadatak_dodele where runda_id = ? and ucesnik_id = ?", rundaK, naA);
        odbijen("Zadatak ti nije dodeljen.", () -> odgovori(naA, Map.of("a", "45")));
        k(TipKomande.OTVORI_ZATVORI, null);
        odbijen("Pitanje je zatvoreno.", () -> odgovorService.odgovori(izvodjenjeId, naB,
                new OdgovorCmd(rundaK, null, null, null, null, Map.of("a", "13"))));
    }

    @Test
    void ocenaPoSnimkuPosleIzmeneVarijante() {
        jdbc.update("update varijante set odgovori = '{\"a\":{\"broj\":99.0}}', status = 'ARHIVIRANA' where id = ?", varA);
        odgovori(naA, Map.of("a", "45"));
        assertEquals(Boolean.TRUE, red(naA).get("tacno"), "ocena po snimku, ne po banci");
    }

    @Test
    void svgSnimkaPrezivljavaIzmenu() throws Exception {
        VarijantaSnimak uSnimku = ((ZadatakSnimak) jsonMapper.readValue(jdbc.queryForObject(
                "select snimak from pitanje_runde where id = ?", String.class, rundaK), SnimakRunde.class))
                .varijanta(varA).orElseThrow();
        assertNotNull(uSnimku.resenjeSvgId());

        varijantaService.izmeni(varA, new VarijantaCmd("A", "Nov tekst A", "Novo rešenje A",
                Map.of("a", new OdgovorPolja(45.0, null, null, null)), null, null));
        preambulaService.postavi(predmet.getId(), new PreambulaCmd("\\usepackage{amsmath}"));
        varijantaService.renderujSve(predmet.getId());

        String novi = jdbc.queryForObject("select render_svg_id from varijante where id = ?", String.class, varA);
        assertNotEquals(uSnimku.svgId(), novi, "izmena je napravila nov SVG");
        for (String id : List.of(uSnimku.svgId(), uSnimku.resenjeSvgId())) {
            assertEquals(1, jdbc.queryForObject("select count(*) from mediji where id = ?", Integer.class, id));
            assertNotNull(medijService.bajtovi(id), "fajl postoji");
            mvc.perform(get("/public/mediji/" + id)).andExpect(status().isOk());
        }
    }

    @Test
    void papirOdbija() {
        k(TipKomande.OTVORI_ZATVORI, null);
        k(TipKomande.IDI_NA, 1);
        k(TipKomande.OTVORI_ZATVORI, null);
        Long u = jdbc.queryForObject("select ucesnik_id from zadatak_dodele where runda_id = ? limit 1",
                Long.class, trenutnaRunda());
        odbijen("Ovaj zadatak se radi na papiru.", () -> odgovori(u, Map.of()));
    }

    @Test
    void pitanjeIgnorisePolja() {
        k(TipKomande.OTVORI_ZATVORI, null);
        k(TipKomande.IDI_NA, 2);
        k(TipKomande.OTVORI_ZATVORI, null);
        Long r = trenutnaRunda();
        Long tacna = jdbc.queryForObject("select id from pitanje_opcije where tacna = 1 and pitanje_id = "
                + "(select pitanje_id from slajdovi where id = (select slajd_id from pitanje_runde where id = ?))", Long.class, r);
        odgovorService.odgovori(izvodjenjeId, naA, new OdgovorCmd(r, List.of(tacna), null, null, null, Map.of("x", "1")));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select tacno from odgovori where runda_id = ? and ucesnik_id = ?",
                Boolean.class, r, naA));
    }
}
