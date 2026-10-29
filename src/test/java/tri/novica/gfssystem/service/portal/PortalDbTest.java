package tri.novica.gfssystem.service.portal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.SeedRandom;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.dto.portal.*;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.TipAktivnosti;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.LatexRender;
import tri.novica.gfssystem.service.cas.CasPodaci;
import tri.novica.gfssystem.service.uzivo.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Nalog studenta, faze 2 i 3, nad pravom MySQL bazom (spec studentskog dela 5, 6, 7.2, 12). Tri studenta: {@code s1} u
 * grupi G1, {@code s2} u grupi G2 (ista generacija, druga grupa) i {@code s3} koji ponavlja predmet (sopstvena grupa G0,
 * starija generacija, sa aktivnošću na predavanju G1). Banka predmeta: koren R sa listovima K (polje {@code a} BROJ,
 * varijante A=45 i B=13) i M (nikad izvučen), koren P (rad na papiru) i čvor T namene TEST. Prezentacija: INFO "Uvod",
 * ZADATAK nad K, PITANJE, INFO "Sledeći čas" (nikad prikazan). Bez {@code @Transactional}; test čisti za sobom.
 */
@SpringBootTest
@Import(SeedRandom.class)
class PortalDbTest {

    static final String SEMA_A = """
            [{"kljuc":"a","oznaka":"a)","tip":"BROJ","odstupanje":0,"odstupanjeTip":"APSOLUTNO"}]""";
    static final AtomicInteger BROJAC = new AtomicInteger();
    /** Polja koja nikad ne smeju u studentski JSON (spec 7.3). */
    static final List<String> ZABRANJENO = List.of("\"beleske\"", "\"serija\"", "\"izvor\"", "\"uputstvo\"", "\"opis\"",
            "\"napomene\"", "\"renderGreska\"", "\"resenje\"", "\"tekst\":\"Zadatak", "cekiranjeTajna");

    @Autowired IzvodjenjeService izvodjenjeService;
    @Autowired PrezentacijaService prezentacijaService;
    @Autowired UcesnikService ucesnikService;
    @Autowired OdgovorService odgovorService;
    @Autowired MedijService medijService;
    @Autowired PredmetiNaNalogu predmeti;
    @Autowired GrupeStudenta grupeStudenta;
    @Autowired VezbanjeStudenta vezbanje;
    @Autowired IstorijaStudenta istorija;
    @Autowired TemeZaVezbanje teme;
    @Autowired ObjavaOcenaServis objava;
    @Autowired StatistikaVezbanja statistika;
    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean LatexRender latexRender;

    CasPodaci d;
    final List<String> mediji = new ArrayList<>();
    Long g0, g1, g2, g3, s1, s2, s3, s4, l1, l2;
    long r, k, m, p, t, varA, varB;
    Long prezId, slajdUvod, slajdK, slajdPitanje, slajdSledeci, tacnaOpcija;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(anyString(), anyString(), anyList())).thenAnswer(i -> render());
        when(latexRender.renderuj(anyList(), anyString(), anyList())).thenAnswer(i -> render());
        d = new CasPodaci(jdbc);
        g0 = d.grupa(2025);
        g1 = d.grupa(2026);
        g2 = d.grupa(2026);
        g3 = d.grupa(2026);   // grupa bez predavanja iz ovog predmeta
        s1 = d.student(g1, "Prvi");
        s2 = d.student(g2, "Drugi");
        s3 = d.student(g0, "Ponavlja");
        s4 = d.student(g3, "Stranac");
        l1 = d.predavanje(g1, LocalDate.now().minusDays(1));
        l2 = d.predavanje(g2, LocalDate.now().minusDays(1));
        d.aktivnost(l1, s1, "ZADATAK");
        d.aktivnost(l1, s3, "PRISUSTVO");   // s3 ponavlja predmet uz G1

        r = grupaZadataka(null, "Konverzije", 1, "[]", "PREDAVANJE");
        k = grupaZadataka(r, "Binarno u dekadno", 1, SEMA_A, "PREDAVANJE");
        m = grupaZadataka(r, "Dekadno u binarno", 2, SEMA_A, "PREDAVANJE");
        p = grupaZadataka(null, "Na papiru", 2, "[]", "PREDAVANJE");
        t = grupaZadataka(null, "Kolokvijum", 1, SEMA_A, "TEST");
        varA = varijanta(k, "A", "{\"a\":{\"broj\":45.0}}", "AKTIVNA");
        varB = varijanta(k, "B", "{\"a\":{\"broj\":13.0}}", "AKTIVNA");
        varijanta(k, "Nacrt", "{\"a\":{\"broj\":1.0}}", "NACRT");
        varijanta(m, "M1", "{\"a\":{\"broj\":7.0}}", "AKTIVNA");
        varijanta(p, "P1", "{}", "AKTIVNA");
        varijanta(t, "T1", "{\"a\":{\"broj\":2.0}}", "AKTIVNA");

        prezId = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(d.predmetId, "Vežbe " + d.oznaka, null, null)).id();
        slajdUvod = prezentacijaService.dodajSlajd(prezId, new SlajdCmd(TipSlajda.INFO, "Uvod", "Dobro *došli*", null,
                "TAJNA BELEŠKA", false, null, null, null, null), null).id();
        slajdK = prezentacijaService.dodajSlajd(prezId, new SlajdCmd(TipSlajda.ZADATAK, "Zadatak 1", null, null, null, false,
                null, null, null, new ZadatakSlajda(k, null, null)), null).id();
        SlajdDetails pit = prezentacijaService.dodajSlajd(prezId, new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false,
                new PitanjeCmd(TipPitanja.JEDAN_TACAN, "Koliko cifara ima 8 binarno?", null, null,
                        List.of(new OpcijaCmd("4", true), new OpcijaCmd("3", false)),
                        null, null, null, null, null, null, null, null), null, null, null), null);
        slajdPitanje = pit.id();
        tacnaOpcija = pit.pitanje().opcije().stream().filter(OpcijaDetails::tacna).findFirst().orElseThrow().id();
        slajdSledeci = prezentacijaService.dodajSlajd(prezId, new SlajdCmd(TipSlajda.INFO, "Sledeći čas", "Tajno unapred",
                null, null, false, null, null, null, null), null).id();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where prezentacija_id = ?", prezId);
        try {
            prezentacijaService.obrisi(prezId);
        } catch (SystemException ex) {
            // već obrisana
        }
        Long id = d.predmetId;
        for (Long s : List.of(s1, s2, s3, s4)) jdbc.update("delete from vezbanja where student_id = ?", s);
        jdbc.update("delete from objave_ocena where predmet_id = ?", id);
        jdbc.update("delete from koeficijenti_tip_testa where koeficijenti_id in (select id from koeficijenti_ocenjivanja where predmet_id = ?)", id);
        jdbc.update("delete from koeficijenti_ocenjivanja where predmet_id = ?", id);
        jdbc.update("delete from otvorene_teme where grupa_zadataka_id in (select id from grupe_zadataka where predmet_id = ?)", id);
        jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", id);
        jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        mediji.forEach(x -> jdbc.update("delete from mediji where id = ?", x));
        jdbc.update("delete from cekiranja where predavanje_id in (?, ?)", l1, l2);
        d.obrisi();
    }

    // ---------------------------------------------------------------- fixture

    static RenderOdgovor render() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>" + BROJAC.incrementAndGet() + "</text></svg>";
        return new RenderOdgovor(true, Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8)),
                null, null, null, 9L, false);
    }

    long grupaZadataka(Long roditelj, String naziv, int rb, String polja, String namena) {
        Map<String, Object> red = new HashMap<>();
        red.put("predmet_id", d.predmetId);
        red.put("roditelj_id", roditelj);
        red.put("namena", namena);
        red.put("naziv", naziv);
        red.put("opis", "OPIS ZA NOVICU");
        red.put("uputstvo", "UPUTSTVO ZA CLAUDE");
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

    long varijanta(long grupa, String naziv, String odgovori, String status) {
        Map<String, Object> red = new HashMap<>();
        red.put("grupa_id", grupa);
        red.put("rb", 1);
        red.put("naziv", naziv);
        red.put("tekst", "Zadatak " + naziv);
        red.put("resenje", "Rešenje " + naziv);
        red.put("odgovori", odgovori);
        red.put("status", status);
        red.put("izvor", "RUCNO");
        red.put("ikad_odobrena", true);
        red.put("render_status", "OK");
        red.put("render_svg_id", svg(naziv + d.oznaka));
        red.put("render_resenje_svg_id", svg(naziv + d.oznaka + "-resenje"));
        red.put("kreirano", LocalDateTime.now());
        red.put("izmenjeno", LocalDateTime.now());
        red.put("nivo", 2);
        return new SimpleJdbcInsert(jdbc).withTableName("varijante").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(red).longValue();
    }

    /** Izvođenje za grupu sa čuvanjem ili bez, s1 i anoniman učesnik; vraća id izvođenja. */
    record Cas(Long id, String kod, Long u1, Long u2) {
    }

    Cas pokreni(Long grupa, Long predavanje, boolean cuvanje, Long student) {
        IzvodjenjeInfo info = izvodjenjeService.pokreni(prezId, new PokreniCmd(cuvanje, grupa, predavanje));
        Long u1 = ucesnikService.prijavi(info.kod(), "Student", Optional.of(student)).info().ucesnikId();
        Long u2 = ucesnikService.prijavi(info.kod(), "Gost").info().ucesnikId();
        return new Cas(info.id(), info.kod(), u1, u2);
    }

    void k(Cas c, TipKomande tip, Integer vrednost) {
        izvodjenjeService.komanda(c.id(), new KomandaCmd(tip, vrednost));
    }

    Long runda(Cas c) {
        return jdbc.queryForObject("select trenutna_runda_id from izvodjenja where id = ?", Long.class, c.id());
    }

    Long dodela(Long runda, Long ucesnik) {
        return jdbc.queryForObject("select varijanta_id from zadatak_dodele where runda_id = ? and ucesnik_id = ?",
                Long.class, runda, ucesnik);
    }

    /** Ceo čas G1: Uvod, Zadatak K (s1 odgovori tačno), Pitanje (s1 odgovori tačno), bez poslednjeg slajda. Ne završava. */
    Cas casG1(boolean cuvanje) {
        Cas c = pokreni(g1, l1, cuvanje, s1);
        k(c, TipKomande.IDI_NA, 0);
        k(c, TipKomande.IDI_NA, 1);
        k(c, TipKomande.OTVORI_ZATVORI, null);
        Long rk = runda(c);
        Long v = dodela(rk, c.u1());
        odgovorService.odgovori(c.id(), c.u1(), new OdgovorCmd(rk, null, null, null, null,
                Map.of("a", v == varA ? "45" : "13")));
        k(c, TipKomande.OTVORI_ZATVORI, null);
        k(c, TipKomande.IDI_NA, 2);
        k(c, TipKomande.OTVORI_ZATVORI, null);
        odgovorService.odgovori(c.id(), c.u1(), new OdgovorCmd(runda(c), List.of(tacnaOpcija), null, null, null));
        k(c, TipKomande.OTVORI_ZATVORI, null);
        return c;
    }

    static int status(Runnable poziv) {
        SystemException ex = assertThrows(SystemException.class, poziv::run);
        return ex.getCode();
    }

    String json(Object o) {
        return jsonMapper.writeValueAsString(o);
    }

    void bezZabranjenog(Object o) {
        String j = json(o);
        for (String z : ZABRANJENO) assertFalse(j.contains(z), "studentski JSON ne sme da sadrži " + z + ": " + j);
    }

    // ---------------------------------------------------------------- grupe i predmeti

    @Test
    void grupeStudentaIPredmeti() {
        assertEquals(List.of(g0, g1), new ArrayList<>(grupeStudenta.za(s3)), "ponavljač: sopstvena + G1");
        List<PredmetStudenta> p1 = predmeti.predmeti(s1);
        assertEquals(1, p1.size());
        assertEquals(1, p1.getFirst().prisutan());
        assertEquals(1, p1.getFirst().predavanja());
        assertFalse(p1.getFirst().ocenaObjavljena());
        assertEquals(List.of(), predmeti.predmeti(s4), "grupa bez predavanja iz predmeta: ništa");
        assertEquals(404, status(() -> predmeti.prisustvo(s4, d.predmetId)));
        assertEquals(404, status(() -> predmeti.prisustvo(s1, -1L)));

        PrisustvoStudenta pr3 = predmeti.prisustvo(s3, d.predmetId);
        assertEquals(1, pr3.ukupno(), "ponavljač vidi samo predavanje G1 na kome je bio (G0 nema predavanja)");
        assertFalse(pr3.predavanja().getFirst().svojaGrupa());
        assertEquals(TipAktivnosti.PRISUSTVO, pr3.predavanja().getFirst().aktivnost());
        PrisustvoStudenta pr2 = predmeti.prisustvo(s2, d.predmetId);
        assertEquals(0, pr2.prisutan());
        assertEquals(1, pr2.ukupno());
        assertNull(pr2.predavanja().getFirst().aktivnost());
        assertEquals(1, pr2.predavanja().getFirst().redniBroj());
        bezZabranjenog(pr3);
    }

    // ---------------------------------------------------------------- otvaranje tipova i slajdova

    @Test
    void dokCasTrajeNistaNijeOtvorenoPosleKrajaSamoIzvuceni() {
        Cas c = casG1(true);
        assertEquals(List.of(), vezbanje.stablo(s1, d.predmetId), "dok izvođenje traje tip nije otvoren");
        assertEquals(404, status(() -> vezbanje.novi(s1, new NoviZadatakCmd(k, null))));
        assertEquals(404, status(() -> istorija.pregled(s1, prezId)), "slajdovi izvođenja koje traje nisu vidljivi");
        assertEquals(List.of(), istorija.mojiZadaci(s1, d.predmetId), "zadaci sa časa tek posle kraja");
        assertEquals(List.of(), istorija.prezentacije(s1, d.predmetId));

        k(c, TipKomande.ZAVRSI, null);

        List<TemaVezbanja> st = vezbanje.stablo(s1, d.predmetId);
        assertEquals(1, st.size());
        assertEquals("Konverzije", st.getFirst().naziv());
        assertEquals(List.of(k), st.getFirst().tipovi().stream().map(TipVezbanja::tipId).toList(), "samo K, ne i M");
        assertEquals(List.of(0, 2, 0), st.getFirst().tipovi().getFirst().poNivou(), "nacrt se ne broji");
        assertEquals(1, vezbanje.stablo(s3, d.predmetId).size(), "ponavljač vežba sa G1");
        assertEquals(List.of(), vezbanje.stablo(s2, d.predmetId), "druga grupa ne vidi");
        assertEquals("CAS", jdbc.queryForObject("select izvor from otvorene_teme where grupa_id = ? and grupa_zadataka_id = ?",
                String.class, g1, k));

        PregledPrezentacije pr = istorija.pregled(s1, prezId);
        assertEquals(List.of(slajdUvod, slajdK, slajdPitanje), pr.slajdovi().stream().map(SlajdPregleda::id).toList(),
                "samo prikazani slajdovi, ne i sledeći čas");
        bezZabranjenog(pr);
        assertFalse(json(pr).contains("TAJNA BELEŠKA"));
        SlajdPregleda pitanje = pr.slajdovi().get(2);
        assertEquals("4", pitanje.mojOdgovor().tekst());
        assertEquals(Boolean.TRUE, pitanje.mojOdgovor().tacno());
        ZadatakPregleda z = pr.slajdovi().get(1).zadatak();
        assertEquals(1, z.moji().size(), "samo sopstvena dodela, ne gostova");
        assertEquals(List.of(k), z.tipovi().stream().map(TipZaVezbanje::tipId).toList());
        assertEquals(404, status(() -> istorija.pregled(s2, prezId)), "druga grupa: 404");
        assertEquals(1, istorija.pregled(s3, prezId).slajdovi().stream().filter(s -> s.zadatak() != null).count(),
                "ponavljač vidi slajd, bez tuđih zadataka");
        assertTrue(istorija.pregled(s3, prezId).slajdovi().get(1).zadatak().moji().isEmpty());

        List<MojZadatak> moji = istorija.mojiZadaci(s1, d.predmetId);
        assertEquals(1, moji.size());
        MojZadatak mz = moji.getFirst();
        assertEquals(Boolean.TRUE, mz.tacno());
        assertNotNull(mz.resenjeSvgId(), "rešenje posle kraja i kad ga Novica nije pustio");
        assertTrue(mz.vezbanjeOtvoreno());
        assertEquals(k, mz.tipId());
        assertFalse(mz.uneto().isEmpty());
        bezZabranjenog(moji);
        assertEquals(List.of(), istorija.mojiZadaci(s2, d.predmetId));

        List<PrezentacijaStudenta> lista = istorija.prezentacije(s1, d.predmetId);
        assertEquals(1, lista.size());
        assertEquals(3, lista.getFirst().slajdova());
    }

    @Test
    void neCuvajIpakOtvaraTip() {
        Cas c = pokreni(g2, null, false, s2);   // izvođenje sa predavanja uvek čuva
        k(c, TipKomande.IDI_NA, 1);
        k(c, TipKomande.OTVORI_ZATVORI, null);
        k(c, TipKomande.ZAVRSI, null);
        assertEquals(0, jdbc.queryForObject("select count(*) from pitanje_runde where izvodjenje_id = ?", Integer.class, c.id()),
                "Ne čuvaj briše runde");
        assertEquals(List.of(k), vezbanje.stablo(s2, d.predmetId).getFirst().tipovi().stream().map(TipVezbanja::tipId).toList());
        assertEquals(List.of(slajdK), istorija.pregled(s2, prezId).slajdovi().stream().map(SlajdPregleda::id).toList());
    }

    @Test
    void rucnoOtvaranjeIZatvaranje() {
        assertEquals(404, status(() -> teme.otvori(t, g2)), "TEST čvor");
        assertEquals(404, status(() -> teme.otvori(r, -5L)), "nepostojeća grupa");
        List<OtvorenoZaGrupu> posle = teme.otvori(r, g2);
        OtvorenoZaGrupu zaG2 = posle.stream().filter(o -> o.grupaId().equals(g2)).findFirst().orElseThrow();
        assertEquals("OTVORENO", zaG2.stanje());
        assertEquals(2, zaG2.otvorenihListova());
        List<Long> tipovi = vezbanje.stablo(s2, d.predmetId).getFirst().tipovi().stream().map(TipVezbanja::tipId).toList();
        assertEquals(List.of(k, m), tipovi, "ručno otvoren unutrašnji čvor otvara sve listove");
        assertEquals("PREKO_TEME", teme.otvoreno(k).stream().filter(o -> o.grupaId().equals(g2)).findFirst().orElseThrow().stanje());

        teme.otvori(m, g1);
        teme.zatvori(r, g2);
        assertEquals(List.of(), vezbanje.stablo(s2, d.predmetId));
        assertEquals("DELIMICNO", teme.otvoreno(r).stream().filter(o -> o.grupaId().equals(g1)).findFirst().orElseThrow().stanje());
        teme.zatvori(r, g1);
        assertEquals(0, jdbc.queryForObject("select count(*) from otvorene_teme where grupa_id = ?", Integer.class, g1),
                "zatvaranje unutrašnjeg čvora briše i zapise podstabla");
    }

    // ---------------------------------------------------------------- vežbanje

    @Test
    void vezbanjeTokIIdor() {
        teme.otvori(k, g1);
        teme.otvori(p, g1);
        ZadatakVezbanja z = vezbanje.novi(s1, new NoviZadatakCmd(k, null));
        assertEquals(k, z.tipId());
        assertEquals(1, z.polja().size());
        bezZabranjenog(z);
        assertFalse(json(z).contains("odgovori") || json(z).contains("\"broj\":") || json(z).contains("tacn"),
                "polja bez tačnih vrednosti: " + json(z));
        Long var = jdbc.queryForObject("select varijanta_id from vezbanja where id = ?", Long.class, z.pokusajId());
        String tacno = var == varA ? "45" : "13";

        assertEquals(404, status(() -> vezbanje.provera(s2, z.pokusajId(), new ProveraCmd(Map.of("a", tacno)))), "tuđi pokušaj");
        assertEquals(404, status(() -> vezbanje.resenje(s3, z.pokusajId())), "tuđi pokušaj");
        assertEquals(400, status(() -> vezbanje.provera(s1, z.pokusajId(), new ProveraCmd(Map.of("a", "x")))));
        ProveraVezbanja lose = vezbanje.provera(s1, z.pokusajId(), new ProveraCmd(Map.of("a", "99")));
        assertFalse(lose.tacno());
        assertEquals(Map.of("a", false), lose.ishod());
        assertTrue(vezbanje.provera(s1, z.pokusajId(), new ProveraCmd(Map.of("a", tacno))).tacno());
        ResenjeVezbanja res = vezbanje.resenje(s1, z.pokusajId());
        assertNotNull(res.resenjeSvgId());
        assertEquals(var == varA ? 45.0 : 13.0, res.tacneVrednosti().get("a").broj());
        Map<String, Object> red = jdbc.queryForMap("select broj_provera, prva_provera_tacno, tacno, resenje_prikazano from vezbanja where id = ?",
                z.pokusajId());
        assertEquals(2, ((Number) red.get("broj_provera")).intValue());
        assertEquals(Boolean.FALSE, red.get("prva_provera_tacno"));
        assertEquals(Boolean.TRUE, red.get("tacno"));

        List<TipVezbanja> tipovi = vezbanje.stablo(s1, d.predmetId).stream().flatMap(x -> x.tipovi().stream()).toList();
        TipVezbanja tk = tipovi.stream().filter(x -> x.tipId().equals(k)).findFirst().orElseThrow();
        assertEquals(1, tk.provereno());
        assertEquals(1, tk.tacno());
        assertTrue(tipovi.stream().filter(x -> x.tipId().equals(p)).findFirst().orElseThrow().papir());

        // sledeći zadatak izbegava poslednji kad ima izbora
        Long sledeca = jdbc.queryForObject("select varijanta_id from vezbanja where id = ?", Long.class,
                vezbanje.novi(s1, new NoviZadatakCmd(k, null)).pokusajId());
        assertNotEquals(var, sledeca);

        assertEquals(404, status(() -> vezbanje.novi(s1, new NoviZadatakCmd(m, null))), "zatvoren list");
        assertEquals(404, status(() -> vezbanje.novi(s1, new NoviZadatakCmd(r, null))), "unutrašnji čvor nije tip");
        assertEquals(404, status(() -> vezbanje.novi(s1, new NoviZadatakCmd(t, null))), "TEST");
        assertEquals(404, status(() -> vezbanje.novi(s2, new NoviZadatakCmd(k, null))), "druga grupa");
        assertEquals(409, status(() -> vezbanje.novi(s1, new NoviZadatakCmd(k, 3))), "nema nivoa 3");

        // varijanta arhivirana posle izvlačenja: pokušaj više ne daje ni proveru ni rešenje
        ZadatakVezbanja z2 = vezbanje.novi(s1, new NoviZadatakCmd(k, null));
        Long v2 = jdbc.queryForObject("select varijanta_id from vezbanja where id = ?", Long.class, z2.pokusajId());
        jdbc.update("update varijante set status = 'ARHIVIRANA' where id = ?", v2);
        assertEquals(404, status(() -> vezbanje.resenje(s1, z2.pokusajId())));
        // zatvoren tip posle izvlačenja: isto
        jdbc.update("update varijante set status = 'AKTIVNA' where id = ?", v2);
        teme.zatvori(k, g1);
        assertEquals(404, status(() -> vezbanje.resenje(s1, z2.pokusajId())));

        List<StatistikaTipa> stat = statistika.statistika(d.predmetId, null, null, null);
        assertEquals(1, stat.size());
        assertEquals(1, stat.getFirst().studenata());
        assertEquals(3, stat.getFirst().pokusaja());
        assertEquals(0, stat.getFirst().prvaTacno());
        assertEquals(List.of(), statistika.statistika(d.predmetId, g2, null, null));
    }

    // ---------------------------------------------------------------- prvo punjenje (V19)

    /** Dva INSERT-a prvog punjenja iz V19, tačno kako su u migraciji. */
    static List<String> prvoPunjenje() throws Exception {
        String sql;
        try (var in = PortalDbTest.class.getResourceAsStream("/db/migration/V19__studentski_portal.sql")) {
            sql = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> rez = new ArrayList<>();
        for (String pocetak : List.of("INSERT INTO `otvorene_teme`", "INSERT INTO `prikazani_slajdovi`")) {
            int i = sql.indexOf(pocetak);
            rez.add(sql.substring(i, sql.indexOf(';', i)));
        }
        return rez;
    }

    @Test
    void prvoPunjenjeIzMigracije() throws Exception {
        Cas c = casG1(true);
        k(c, TipKomande.IDI_NA, 1);   // izvođenje staje na slajdu K (rb 2)
        jdbc.update("insert into pitanje_runde (izvodjenje_id, slajd_id, redni_broj, snimak, otvoreno) values (?, ?, 99, 'nije json', ?)",
                c.id(), slajdSledeci, LocalDateTime.now());
        k(c, TipKomande.ZAVRSI, null);
        Cas trajno = pokreni(g2, l2, true, s2);   // aktivno izvođenje: ništa iz njega
        k(trajno, TipKomande.IDI_NA, 3);
        jdbc.update("delete from otvorene_teme where grupa_id in (?, ?)", g1, g2);
        jdbc.update("delete from prikazani_slajdovi where grupa_id in (?, ?)", g1, g2);

        for (int i = 0; i < 2; i++) {   // dva puta: idempotentno
            prvoPunjenje().forEach(jdbc::update);
        }
        assertEquals(List.of(k), jdbc.queryForList("select grupa_zadataka_id from otvorene_teme where grupa_id = ?", Long.class, g1));
        assertEquals(Set.of(slajdUvod, slajdK, slajdPitanje, slajdSledeci),
                new HashSet<>(jdbc.queryForList("select slajd_id from prikazani_slajdovi where grupa_id = ?", Long.class, g1)),
                "do mesta gde je stalo plus slajdovi sa rundom (i neispravna runda na poslednjem slajdu)");
        assertEquals(0, jdbc.queryForObject("select count(*) from prikazani_slajdovi where grupa_id = ?", Integer.class, g2),
                "izvođenje koje traje ne puni ništa");
        assertEquals(0, jdbc.queryForObject("select count(*) from otvorene_teme where grupa_id = ?", Integer.class, g2));
        jdbc.update("update izvodjenja set status = 'ZAVRSENO', aktivan_kod = null where id = ?", trajno.id());
    }

    // ---------------------------------------------------------------- ocene

    @Test
    void oceneTekPosleObjaveISamoSvoje() {
        assertFalse(objava.zaStudenta(s1, d.predmetId).objavljeno());
        assertNull(objava.zaStudenta(s1, d.predmetId).ocena());
        ObjavaOcenaInfo info = objava.objavi(d.predmetId, g1);
        assertNotNull(info.objavljeno());
        assertEquals(1, info.studenata());
        OcenaStudenta o1 = objava.zaStudenta(s1, d.predmetId);
        assertTrue(o1.objavljeno());
        assertNotNull(o1.ocena());
        assertTrue(o1.ocena().poeniAktivnost() > 0, "aktivnost ZADATAK nosi bodove");
        bezZabranjenog(o1);
        assertFalse(json(o1).contains("Prvi"), "snimak bez podataka studenta");
        assertFalse(objava.zaStudenta(s3, d.predmetId).objavljeno(), "ponavljač gleda svoju grupu, ne G1");
        assertFalse(objava.zaStudenta(s2, d.predmetId).objavljeno());
        assertTrue(predmeti.predmeti(s1).getFirst().ocenaObjavljena());
        assertEquals(404, status(() -> objava.zaStudenta(s4, d.predmetId)));

        objava.objavi(d.predmetId, g1);   // ponovo: zamenjuje snimak
        assertEquals(1, jdbc.queryForObject("select count(*) from objavljene_ocene o join objave_ocena x on x.id = o.objava_id "
                + "where x.predmet_id = ?", Integer.class, d.predmetId));
        objava.povuci(d.predmetId, g1);
        assertFalse(objava.zaStudenta(s1, d.predmetId).objavljeno());
        assertNull(objava.stanje(d.predmetId, g1).objavljeno());
    }
}
