package tri.novica.gfssystem.repository.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.entity.banka.Varijanta;
import tri.novica.gfssystem.entity.banka.VarijantaPrilog;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Šema V11 i model banke zadataka nad pravom MySQL bazom (Flyway V1..V11, kao CI): ono što Mockito ne vidi. Strani
 * ključevi (RESTRICT na grupama i varijantama, CASCADE na prilozima), podrazumevana {@code mediji.vrsta} za redove
 * upisane bez nje i upiti repozitorijuma. Bez {@code @Transactional}: svaki poziv se zaista commit-uje, pa test
 * čisti za sobom kroz JDBC (deca pre roditelja).
 */
@SpringBootTest
class BankaRepositoryDbTest {

    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired VarijantaPrilogRepository prilogRepository;
    @Autowired PredmetRepository predmetRepository;
    @Autowired MedijRepository medijRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired Clock clock;

    Predmet predmet;
    final List<String> mediji = new ArrayList<>();

    @BeforeEach
    void setUp() {
        predmet = new Predmet();
        predmet.setNaziv("Banka DB test " + UUID.randomUUID().toString().substring(0, 8));
        predmet = predmetRepository.save(predmet);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from varijanta_prilozi where varijanta_id in (select v.id from varijante v "
                + "join grupe_zadataka g on g.id = v.grupa_id where g.predmet_id = ?)", predmet.getId());
        jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)",
                predmet.getId());
        jdbc.update("delete from grupe_zadataka where predmet_id = ? and roditelj_id is not null", predmet.getId());
        jdbc.update("delete from grupe_zadataka where predmet_id = ?", predmet.getId());
        for (String id : mediji) jdbc.update("delete from mediji where id = ?", id);
        predmetRepository.deleteById(predmet.getId());
    }

    GrupaZadataka grupa(GrupaZadataka roditelj, Namena namena, String naziv, int rb) {
        GrupaZadataka g = new GrupaZadataka();
        g.setPredmet(predmet);
        g.setRoditelj(roditelj);
        g.setNamena(namena);
        g.setNaziv(naziv);
        g.setRb(rb);
        g.setTezina(roditelj == null ? null : 3);
        g.setKreirano(LocalDateTime.now(clock));
        g.setIzmenjeno(LocalDateTime.now(clock));
        return grupaRepository.save(g);
    }

    Medij medij(VrstaMedija vrsta) {
        Medij m = new Medij();
        m.setId(UUID.randomUUID().toString());
        m.setNaziv("render.svg");
        m.setMime("image/svg+xml");
        m.setVelicina(42);
        m.setVrsta(vrsta);
        m.setKreirano(LocalDateTime.now(clock));
        mediji.add(m.getId());
        return medijRepository.save(m);
    }

    Varijanta varijanta(GrupaZadataka grupa, Medij render, String imePriloga) {
        Varijanta v = new Varijanta();
        v.setGrupa(grupa);
        v.setRb(1);
        v.setTekst("Odredi reakciju oslonca A.");
        v.setStatus(StatusVarijante.NACRT);
        v.setIzvor(IzvorVarijante.RUCNO);
        v.setRenderStatus(RenderStatus.CEKA);
        v.setRenderSvg(render);
        v.setKreirano(LocalDateTime.now(clock));
        v.setIzmenjeno(LocalDateTime.now(clock));
        VarijantaPrilog p = new VarijantaPrilog();
        p.setVarijanta(v);
        p.setIme(imePriloga);
        p.setMedij(render);
        v.getPrilozi().add(p);
        return varijantaRepository.save(v);
    }

    @Test
    void stabloISiroce() {
        GrupaZadataka koren = grupa(null, Namena.PREDAVANJE, "Statika", 1);
        GrupaZadataka podgrupa = grupa(koren, Namena.PREDAVANJE, "Proste grede", 1);
        Medij render = medij(VrstaMedija.RENDER_SVG);
        Varijanta varijanta = varijanta(podgrupa, render, "skica.svg");

        // podrazumevane vrednosti JSON kolona
        assertEquals("[]", grupaRepository.findById(koren.getId()).orElseThrow().getPolja());
        assertEquals("{}", varijantaRepository.findById(varijanta.getId()).orElseThrow().getOdgovori());

        List<Object[]> brojaci = varijantaRepository.brojaciPoGrupi(List.of(podgrupa.getId()));
        assertEquals(1, brojaci.size());
        assertArrayEquals(new Object[]{podgrupa.getId(), StatusVarijante.NACRT, RenderStatus.CEKA, 1L}, brojaci.get(0));

        // RESTRICT: grupa sa podgrupom i grupa sa varijantom se ne brišu
        assertThrows(DataIntegrityViolationException.class, () -> grupaRepository.deleteById(koren.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> grupaRepository.deleteById(podgrupa.getId()));
        assertEquals(1, grupaRepository.countByRoditeljId(koren.getId()));
        assertEquals(1, varijantaRepository.countByGrupaId(podgrupa.getId()));

        // CASCADE: brisanje varijante u bazi briše njene priloge (JPA put je pokriven orphanRemoval-om)
        Long prilogId = varijanta.getPrilozi().get(0).getId();
        assertTrue(prilogRepository.existsById(prilogId));
        assertEquals(1, jdbc.queryForObject("select count(*) from varijanta_prilozi where varijanta_id = ?",
                Integer.class, varijanta.getId()));
        jdbc.update("delete from varijante where id = ?", varijanta.getId());
        assertEquals(0, jdbc.queryForObject("select count(*) from varijanta_prilozi where varijanta_id = ?",
                Integer.class, varijanta.getId()));
        assertFalse(prilogRepository.existsById(prilogId));

        // vrsta medija: učitana kao RENDER_SVG, a red upisan bez kolone dobija SLIKA
        assertEquals(VrstaMedija.RENDER_SVG, medijRepository.findById(render.getId()).orElseThrow().getVrsta());
        String stari = UUID.randomUUID().toString();
        mediji.add(stari);
        jdbc.update("insert into mediji (id, naziv, mime, velicina, kreirano) values (?, 'stara.png', 'image/png', 7, now(6))",
                stari);
        assertEquals("SLIKA", jdbc.queryForObject("select vrsta from mediji where id = ?", String.class, stari));
        assertEquals(VrstaMedija.SLIKA, medijRepository.findById(stari).orElseThrow().getVrsta());
        assertEquals(VrstaMedija.SLIKA, new Medij().getVrsta());
    }

    @Test
    void upitiRepozitorijuma() {
        GrupaZadataka koren = grupa(null, Namena.PREDAVANJE, "Statika", 2);
        GrupaZadataka drugiKoren = grupa(null, Namena.PREDAVANJE, "Dinamika", 1);
        GrupaZadataka testKoren = grupa(null, Namena.TEST, "Statika", 1);
        GrupaZadataka podgrupa = grupa(koren, Namena.PREDAVANJE, "Grede", 1);
        Medij render = medij(VrstaMedija.RENDER_SVG);
        Varijanta varijanta = varijanta(podgrupa, render, "skica.svg");

        // redosled: koreni po rb, pa deca; namena razdvaja stabla
        List<GrupaZadataka> predavanja = grupaRepository.findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(
                predmet.getId(), Namena.PREDAVANJE);
        assertEquals(List.of(drugiKoren.getId(), koren.getId(), podgrupa.getId()),
                predavanja.stream().map(GrupaZadataka::getId).toList());
        assertEquals(List.of(testKoren.getId()), grupaRepository.findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(
                predmet.getId(), Namena.TEST).stream().map(GrupaZadataka::getId).toList());
        assertEquals(List.of(podgrupa.getId()),
                grupaRepository.findByRoditeljIdOrderByRb(koren.getId()).stream().map(GrupaZadataka::getId).toList());

        // koreni jedne namene po rb i id predmeta pre zaključavanja (GrupaZadatakaService)
        assertEquals(List.of(drugiKoren.getId(), koren.getId()), grupaRepository.findByPredmetIdAndNamenaAndRoditeljIsNullOrderByRb(
                predmet.getId(), Namena.PREDAVANJE).stream().map(GrupaZadataka::getId).toList());
        assertEquals(Optional.of(predmet.getId()), grupaRepository.findPredmetId(podgrupa.getId()));
        assertTrue(grupaRepository.findPredmetId(-1L).isEmpty());

        // varijante: po grupi, po statusu, brojači, zaključavanje i upiti po predmetu
        assertEquals(1, varijantaRepository.findByGrupaIdOrderByRb(podgrupa.getId()).size());
        assertEquals(1, varijantaRepository.findByGrupaIdAndStatusOrderByRb(podgrupa.getId(), StatusVarijante.NACRT).size());
        assertEquals(0, varijantaRepository.findByGrupaIdAndStatusOrderByRb(podgrupa.getId(), StatusVarijante.AKTIVNA).size());
        assertEquals(1, varijantaRepository.countByGrupaIdAndStatus(podgrupa.getId(), StatusVarijante.NACRT));
        assertEquals(0, varijantaRepository.countByGrupaIdAndStatus(podgrupa.getId(), StatusVarijante.ARHIVIRANA));
        assertTrue(tx.execute(s -> varijantaRepository.findByIdForUpdate(varijanta.getId())).isPresent());
        assertEquals(List.of(varijanta.getId()),
                varijantaRepository.svePredmeta(predmet.getId()).stream().map(Varijanta::getId).toList());
        assertEquals(List.of("skica.svg"), tx.execute(s -> varijantaRepository.findById(varijanta.getId()).orElseThrow()
                .getPrilozi().stream().map(VarijantaPrilog::getIme).toList()));

        // poništavanje heša cele banke predmeta (renderHash je char(64), SHA-256 heksadecimalno)
        jdbc.update("update varijante set render_hash = ? where id = ?", "a".repeat(64), varijanta.getId());
        assertEquals("a".repeat(64), varijantaRepository.findById(varijanta.getId()).orElseThrow().getRenderHash());
        Integer ponisteno = tx.execute(s -> varijantaRepository.ponistiHashPredmeta(predmet.getId()));
        assertEquals(1, ponisteno);
        assertNull(varijantaRepository.findById(varijanta.getId()).orElseThrow().getRenderHash());
    }

    @Test
    void predmetImaNullablePreambulu() {
        assertNull(predmetRepository.findById(predmet.getId()).orElseThrow().getLatexPreambula());
        predmet.setLatexPreambula("\\usepackage{amsmath}\n\\newcommand{\\kN}{\\,\\mathrm{kN}}");
        predmetRepository.save(predmet);
        assertEquals(predmet.getLatexPreambula(), predmetRepository.findById(predmet.getId()).orElseThrow().getLatexPreambula());
    }
}
