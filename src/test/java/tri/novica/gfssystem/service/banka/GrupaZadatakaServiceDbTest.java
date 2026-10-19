package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.banka.*;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GrupaZadatakaService} nad pravom MySQL bazom (Flyway V1..V11, kao CI): brojači iz agregatnog upita, brisanje
 * kroz JPA (varijante, prilozi, SVG mediji i fajlovi pre grupe, bez RESTRICT greške), prenumeracija braće u bazi,
 * promena šeme nad odgovorima varijanti i zaključavanje reda predmeta. Bez {@code @Transactional}: svaki poziv servisa
 * se zaista commit-uje, pa test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class GrupaZadatakaServiceDbTest {

    @Autowired GrupaZadatakaService service;
    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijRepository medijRepository;
    @Autowired MedijService medijService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transakcije;
    @Autowired Clock clock;
    @Autowired EntityManagerFactory emf;
    @Value("${gfs.mediji.dir}") String medijiDir;

    Predmet predmet;
    final List<String> mediji = new ArrayList<>();

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Banka stablo DB test " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
    }

    @AfterEach
    void tearDown() {
        Long id = predmet.getId();
        String grupePredmeta = "(select id from grupe_zadataka where predmet_id = ?)";
        jdbc.update("delete from varijanta_prilozi where varijanta_id in (select v.id from varijante v "
                + "join grupe_zadataka g on g.id = v.grupa_id where g.predmet_id = ?)", id);
        jdbc.update("delete from varijante where grupa_id in " + grupePredmeta, id);
        jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
        mediji.forEach(medijService::obrisi);
        predmetRepository.deleteById(id);
    }

    GrupaDetails koren(String naziv) {
        return service.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, naziv, null, null, null, List.of()));
    }

    GrupaDetails podgrupa(GrupaDetails roditelj, String naziv) {
        return service.kreiraj(new GrupaCmd(null, roditelj.id(), null, naziv, null, null, null, List.of()));
    }

    Varijanta varijanta(Long grupaId, StatusVarijante status, RenderStatus render, String odgovori) {
        Varijanta v = new Varijanta();
        v.setGrupa(grupaRepository.findById(grupaId).orElseThrow());
        v.setRb(1);
        v.setTekst("Pretvori $1011_2$ u dekadni.");
        v.setOdgovori(odgovori);
        v.setStatus(status);
        v.setIzvor(IzvorVarijante.RUCNO);
        v.setRenderStatus(render);
        v.setKreirano(LocalDateTime.now(clock));
        v.setIzmenjeno(LocalDateTime.now(clock));
        return varijantaRepository.save(v);
    }

    String svg(String naziv) {
        String id = medijService.sacuvajRender("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8),
                naziv).id();
        mediji.add(id);
        return id;
    }

    List<String> deca(Long roditeljId) {
        return jdbc.queryForList("select concat(naziv, ':', rb) from grupe_zadataka where roditelj_id = ? order by rb",
                String.class, roditeljId);
    }

    List<String> koreni() {
        return jdbc.queryForList("select concat(naziv, ':', rb) from grupe_zadataka where predmet_id = ? and roditelj_id is null "
                + "and namena = 'PREDAVANJE' order by rb", String.class, predmet.getId());
    }

    static GrupaCmd izmena(String naziv, Integer tezina, List<Polje> polja) {
        return new GrupaCmd(null, null, null, naziv, null, tezina, null, polja);
    }

    @Test
    void stabloImaTacneBrojeveIzJednogUpita() {
        GrupaDetails r = koren("Konverzije brojeva");
        GrupaDetails a = podgrupa(r, "2 → 10");
        GrupaDetails b = podgrupa(r, "16 → 2");
        varijanta(a.id(), StatusVarijante.NACRT, RenderStatus.OK, "{}");
        varijanta(a.id(), StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
        varijanta(a.id(), StatusVarijante.NACRT, RenderStatus.GRESKA, "{}");
        varijanta(b.id(), StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
        varijanta(b.id(), StatusVarijante.ARHIVIRANA, RenderStatus.GRESKA, "{}");
        service.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum", null, null, null, List.of()));

        List<GrupaCvor> stablo = service.stablo(predmet.getId(), Namena.PREDAVANJE);

        assertEquals(1, stablo.size());
        GrupaCvor cr = stablo.get(0);
        assertEquals(List.of(5, 2, 2, 2), List.of(cr.brojVarijanti(), cr.brojAktivnih(), cr.brojNacrta(), cr.brojGresaka()));
        assertFalse(cr.list());
        assertEquals(List.of("2 → 10", "16 → 2"), cr.deca().stream().map(GrupaCvor::naziv).toList());
        GrupaCvor ca = cr.deca().get(0);
        assertEquals(List.of(3, 1, 2, 1), List.of(ca.brojVarijanti(), ca.brojAktivnih(), ca.brojNacrta(), ca.brojGresaka()));
        assertTrue(ca.list());
        GrupaCvor cb = cr.deca().get(1);
        assertEquals(List.of(2, 1, 0, 1), List.of(cb.brojVarijanti(), cb.brojAktivnih(), cb.brojNacrta(), cb.brojGresaka()));
        assertEquals(List.of(1, 2), List.of(ca.rb(), cb.rb()));
        assertEquals(List.of("Kolokvijum"), service.stablo(predmet.getId(), Namena.TEST).stream().map(GrupaCvor::naziv).toList());

        GrupaDetails dr = service.detalji(r.id());
        assertEquals(List.of(5, 2, 2, 2), List.of(dr.brojVarijanti(), dr.brojAktivnih(), dr.brojNacrta(), dr.brojGresaka()));
        assertFalse(dr.list());
        GrupaDetails db = service.detalji(b.id());
        assertEquals(List.of(new GrupaPutanja(r.id(), "Konverzije brojeva"), new GrupaPutanja(b.id(), "16 → 2")), db.putanja());
        assertEquals(Namena.PREDAVANJE, db.namena());
        assertEquals(r.id(), db.roditeljId());
    }

    @Test
    void brisanjeGrupeSaPodgrupomJe409AListBriseSaVarijantamaIMedijima() throws Exception {
        GrupaDetails r = koren("Konverzije");
        GrupaDetails a = podgrupa(r, "A");
        GrupaDetails b = podgrupa(r, "B");
        GrupaDetails c = podgrupa(r, "C");
        Varijanta v = varijanta(b.id(), StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
        String zadatak = svg("zadatak.svg");
        String resenje = svg("resenje.svg");
        Medij prilog = new Medij();
        prilog.setId(UUID.randomUUID().toString());
        prilog.setNaziv("skica.png");
        prilog.setMime("image/png");
        prilog.setVelicina(10);
        prilog.setVrsta(VrstaMedija.SLIKA);
        prilog.setKreirano(LocalDateTime.now(clock));
        medijRepository.save(prilog);
        mediji.add(prilog.getId());
        jdbc.update("update varijante set render_svg_id = ?, render_resenje_svg_id = ? where id = ?", zadatak, resenje, v.getId());
        jdbc.update("insert into varijanta_prilozi (varijanta_id, ime, medij_id) values (?, 'skica', ?)", v.getId(), prilog.getId());
        varijanta(b.id(), StatusVarijante.NACRT, RenderStatus.CEKA, "{}");

        SystemException e = assertThrows(SystemException.class, () -> service.obrisi(r.id()));
        assertEquals(409, e.getCode());
        assertEquals("Grupa ima podgrupe.", e.getMessage());
        assertTrue(grupaRepository.existsById(r.id()));

        service.obrisi(b.id());

        assertFalse(grupaRepository.existsById(b.id()));
        assertEquals(0, varijantaRepository.countByGrupaId(b.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from varijanta_prilozi where varijanta_id = ?", Long.class, v.getId()));
        assertFalse(medijRepository.existsById(zadatak));
        assertFalse(medijRepository.existsById(resenje));
        assertFalse(Files.exists(Path.of(medijiDir).resolve(zadatak)));
        assertFalse(Files.exists(Path.of(medijiDir).resolve(resenje)));
        assertTrue(medijRepository.existsById(prilog.getId()));   // medij priloga ostaje, nestaje samo red priloga
        assertEquals(List.of("A:1", "C:2"), deca(r.id()));

        service.obrisi(a.id());
        service.obrisi(c.id());
        service.obrisi(r.id());
        assertFalse(grupaRepository.existsById(r.id()));
    }

    @Test
    void premestiPrenumerisaBracu1DoN() {
        GrupaDetails r = koren("R");
        GrupaDetails s = koren("S");
        GrupaDetails c1 = podgrupa(r, "c1");
        GrupaDetails c2 = podgrupa(r, "c2");
        GrupaDetails c3 = podgrupa(r, "c3");
        GrupaDetails c4 = podgrupa(r, "c4");
        assertEquals(List.of("c1:1", "c2:2", "c3:3", "c4:4"), deca(r.id()));

        service.premesti(c4.id(), new PremestiCmd(r.id(), 1));
        assertEquals(List.of("c4:1", "c1:2", "c2:3", "c3:4"), deca(r.id()));

        GrupaDetails d = service.premesti(c2.id(), new PremestiCmd(s.id(), null));
        assertEquals(s.id(), d.roditeljId());
        assertEquals(List.of("c4:1", "c1:2", "c3:3"), deca(r.id()));
        assertEquals(List.of("c2:1"), deca(s.id()));

        service.premesti(c3.id(), new PremestiCmd(null, 1));
        assertEquals(List.of("c3:1", "R:2", "S:3"), koreni());
        assertEquals(List.of("c4:1", "c1:2"), deca(r.id()));

        service.redosled(null, predmet.getId(), Namena.PREDAVANJE, new RedosledGrupaCmd(List.of(s.id(), r.id(), c3.id())));
        assertEquals(List.of("S:1", "R:2", "c3:3"), koreni());
        assertEquals(List.of("S", "R", "c3"), service.stablo(predmet.getId(), Namena.PREDAVANJE).stream().map(GrupaCvor::naziv).toList());
    }

    @Test
    void izmenaSemePrilagodjavaOdgovoreAPromenaTipaSaAktivnimJe409BezUpisa() {
        Polje a = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
        Polje b = new Polje("b", "b) heks u binarni", TipPolja.TEKST, null, null, null, null, null, null);
        GrupaDetails g = service.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "2 → 10", null, 2, null, List.of(a)));
        Varijanta v = varijanta(g.id(), StatusVarijante.AKTIVNA, RenderStatus.OK, "{\"a\":{\"broj\":45.0}}");
        String poljaPre = jdbc.queryForObject("select polja from grupe_zadataka where id = ?", String.class, g.id());

        Polje aTekst = new Polje("a", "a) binarni u dekadni", TipPolja.TEKST, null, null, null, null, null, null);
        SystemException e = assertThrows(SystemException.class, () -> service.izmeni(g.id(), izmena("Novi", 2, List.of(aTekst))));
        assertEquals(409, e.getCode());
        assertEquals("Tip polja 'a' se ne menja dok grupa ima aktivne varijante.", e.getMessage());
        assertEquals("2 → 10", jdbc.queryForObject("select naziv from grupe_zadataka where id = ?", String.class, g.id()));
        assertEquals(poljaPre, jdbc.queryForObject("select polja from grupe_zadataka where id = ?", String.class, g.id()));
        assertEquals("AKTIVNA", jdbc.queryForObject("select status from varijante where id = ?", String.class, v.getId()));

        // bez polja (null): šema i odgovori ostaju, menjaju se samo naziv i težina
        GrupaDetails bezPolja = service.izmeni(g.id(), izmena("2 u 10", 3, null));
        assertEquals(3, bezPolja.tezina());
        assertEquals(poljaPre, jdbc.queryForObject("select polja from grupe_zadataka where id = ?", String.class, g.id()));
        assertEquals("AKTIVNA", jdbc.queryForObject("select status from varijante where id = ?", String.class, v.getId()));
        assertEquals("{\"a\":{\"broj\":45.0}}", jdbc.queryForObject("select odgovori from varijante where id = ?", String.class, v.getId()));

        // dodato polje: varijanta u nacrt, vrednost postojećeg ostaje
        GrupaDetails d = service.izmeni(g.id(), izmena("2 → 10", 2, List.of(a, b)));
        assertEquals(List.of(1, 0, 1), List.of(d.brojVarijanti(), d.brojAktivnih(), d.brojNacrta()));
        assertEquals("NACRT", jdbc.queryForObject("select status from varijante where id = ?", String.class, v.getId()));
        assertEquals("{\"a\":{\"broj\":45.0}}", jdbc.queryForObject("select odgovori from varijante where id = ?", String.class, v.getId()));

        // uklonjeno polje: ključ nestaje iz odgovora
        service.izmeni(g.id(), izmena("2 → 10", 2, List.of(b)));
        assertEquals("{}", jdbc.queryForObject("select odgovori from varijante where id = ?", String.class, v.getId()));
        assertEquals(List.of(b.kljuc()), service.detalji(g.id()).polja().stream().map(Polje::kljuc).toList());
    }

    /**
     * Carry-forward Task 1 (EAGER roditelj): stablo od 29 grupa u 4 nivoa je stalan broj upita, ne N+1: predmet postoji,
     * grupe, predmet grupa, brojači, (MCP) Klodovi nacrti po grupi i isključene grupe predmeta.
     */
    @Test
    void stabloJeNajvise6UpitaBezObziraNaBrojGrupa() {
        GrupaDetails r = koren("R");
        for (int i = 0; i < 4; i++) {
            GrupaDetails a = podgrupa(r, "A" + i);
            for (int j = 0; j < 3; j++) {
                GrupaDetails b = podgrupa(a, "B" + j);
                varijanta(podgrupa(b, "C").id(), StatusVarijante.AKTIVNA, RenderStatus.OK, "{}");
            }
            // isključena tema nad svakom granom: oznaka je i dalje jedan upit (teme nestaju sa predmetom u tearDown-u)
            jdbc.update("insert into iskljucene_teme (predmet_id, opis, grupa_id, kreirano) values (?, ?, ?, now(6))",
                    predmet.getId(), "Grana A" + i, a.id());
        }
        Statistics statistika = emf.unwrap(SessionFactory.class).getStatistics();
        boolean bila = statistika.isStatisticsEnabled();
        statistika.setStatisticsEnabled(true);
        try {
            statistika.clear();
            List<GrupaCvor> stablo = service.stablo(predmet.getId(), Namena.PREDAVANJE);
            long upita = statistika.getPrepareStatementCount();

            assertEquals(12, stablo.get(0).brojAktivnih());
            assertTrue(stablo.get(0).deca().stream().allMatch(GrupaCvor::iskljucena));
            assertTrue(statistika.getEntityLoadCount() >= 29, "statistika mora da broji");
            assertTrue(upita >= 1 && upita <= 6, "stablo: " + upita + " SQL upita");
        } finally {
            statistika.setStatisticsEnabled(bila);
        }
    }

    @Test
    void izmenaCekaKljucPredmetaIDveUzastopneIzmeneSeVide() throws Exception {
        GrupaDetails g = koren("Prva");
        CountDownLatch drziPredmet = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        CompletableFuture<Void> drugaTransakcija = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transakcije).executeWithoutResult(tx -> {
                    jdbc.queryForList("select id from predmeti where id = ? for update", predmet.getId());
                    drziPredmet.countDown();
                    await(pusti);
                }));
        assertTrue(drziPredmet.await(10, TimeUnit.SECONDS));

        CompletableFuture<GrupaDetails> izmena = CompletableFuture.supplyAsync(() -> service.izmeni(g.id(), izmena("Druga", null, List.of())));
        Thread.sleep(1500);
        assertFalse(izmena.isDone(), "izmena mora čekati ključ predmeta");
        assertEquals("Prva", jdbc.queryForObject("select naziv from grupe_zadataka where id = ?", String.class, g.id()));

        pusti.countDown();
        drugaTransakcija.get(30, TimeUnit.SECONDS);
        assertEquals("Druga", izmena.get(30, TimeUnit.SECONDS).naziv());

        GrupaDetails treca = service.izmeni(g.id(), izmena("Treća", null, List.of()));
        assertEquals("Treća", treca.naziv());
        assertEquals("Treća", service.detalji(g.id()).naziv());
        assertEquals("Treća", jdbc.queryForObject("select naziv from grupe_zadataka where id = ?", String.class, g.id()));
    }

    @Test
    void istovremenoKreiranjeIstogNazivaUpisujeJedanRed() throws Exception {
        GrupaDetails r = koren("Konverzije");
        CountDownLatch drziPredmet = new CountDownLatch(1);
        CountDownLatch pusti = new CountDownLatch(1);
        CompletableFuture<Void> drugaTransakcija = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transakcije).executeWithoutResult(tx -> {
                    jdbc.queryForList("select id from predmeti where id = ? for update", predmet.getId());
                    drziPredmet.countDown();
                    await(pusti);
                }));
        assertTrue(drziPredmet.await(10, TimeUnit.SECONDS));

        // oba čekaju isti ključ; posle njega drugi vidi upis prvog (READ COMMITTED)
        List<CompletableFuture<String>> pokusaji = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            pokusaji.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return podgrupa(r, "Binarni").naziv();
                } catch (SystemException e) {
                    return e.getCode() + " " + e.getMessage();
                }
            }));
        }
        Thread.sleep(1000);
        pusti.countDown();
        drugaTransakcija.get(30, TimeUnit.SECONDS);
        List<String> ishodi = new ArrayList<>();
        for (CompletableFuture<String> p : pokusaji) ishodi.add(p.get(30, TimeUnit.SECONDS));

        assertTrue(ishodi.contains("Binarni"), ishodi.toString());
        assertTrue(ishodi.contains("400 Grupa sa tim nazivom već postoji."), ishodi.toString());
        assertEquals(List.of("Binarni:1"), deca(r.id()));
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
