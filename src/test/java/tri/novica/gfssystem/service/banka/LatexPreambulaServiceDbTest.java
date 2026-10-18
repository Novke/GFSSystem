package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * {@link LatexPreambulaService} nad pravom MySQL bazom (Flyway V1..V11, kao CI): preambula se zaista upisuje, a bulk
 * update keša rendera dira samo varijante tog predmeta i samo kad se vrednost promenila; preambula koja ne prođe probni
 * render (lažan {@link LatexRender} bean) ne menja ništa. Bez {@code @Transactional}: svaki poziv servisa se commit-uje, pa
 * test čisti za sobom kroz JDBC.
 */
@SpringBootTest
class LatexPreambulaServiceDbTest {

    static final String HES = "a".repeat(64);
    static final String PREAMBULA = "\\usepackage{xcolor}";

    @Autowired LatexPreambulaService service;
    @Autowired PredmetRepository predmetRepository;
    @Autowired GrupaZadatakaRepository grupaRepository;
    @Autowired VarijantaRepository varijantaRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean LatexRender latexRender;

    Predmet predmet;
    Predmet drugiPredmet;
    List<Long> varijante;
    Long tudjaVarijanta;

    @BeforeEach
    void setUp() {
        predmet = predmet();
        drugiPredmet = predmet();
        varijante = List.of(varijanta(predmet), varijanta(predmet));
        tudjaVarijanta = varijanta(drugiPredmet);
        when(latexRender.renderuj(eq("svg"), anyString(), anyList())).thenReturn(new RenderOdgovor(true,
                Base64.getEncoder().encodeToString("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()), null, null, null, 5L, false));
    }

    @AfterEach
    void tearDown() {
        for (Predmet p : List.of(predmet, drugiPredmet)) {
            jdbc.update("delete from varijante where grupa_id in (select id from grupe_zadataka where predmet_id = ?)", p.getId());
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", p.getId());
            predmetRepository.deleteById(p.getId());
        }
    }

    Predmet predmet() {
        Predmet p = new Predmet();
        p.setNaziv("Banka preambula DB test " + UUID.randomUUID().toString().substring(0, 8));
        return predmetRepository.save(p);
    }

    Long varijanta(Predmet p) {
        GrupaZadataka g = new GrupaZadataka();
        g.setPredmet(p);
        g.setNamena(Namena.PREDAVANJE);
        g.setNaziv("Grupa");
        g.setRb(1);
        g.setTezina(2);
        g.setKreirano(LocalDateTime.now());
        g.setIzmenjeno(LocalDateTime.now());
        Varijanta v = new Varijanta();
        v.setGrupa(grupaRepository.save(g));
        v.setRb(1);
        v.setTekst("Zadatak.");
        v.setStatus(StatusVarijante.AKTIVNA);
        v.setIzvor(IzvorVarijante.RUCNO);
        v.setRenderStatus(RenderStatus.OK);
        v.setRenderHash(HES);
        v.setKreirano(LocalDateTime.now());
        v.setIzmenjeno(LocalDateTime.now());
        return varijantaRepository.save(v).getId();
    }

    String preambulaUBazi(Predmet p) {
        return jdbc.queryForObject("select latex_preambula from predmeti where id = ?", String.class, p.getId());
    }

    String hes(Long varijantaId) {
        return jdbc.queryForObject("select render_hash from varijante where id = ?", String.class, varijantaId);
    }

    void vratiHesove() {
        jdbc.update("update varijante set render_hash = ? where grupa_id in "
                + "(select id from grupe_zadataka where predmet_id in (?, ?))", HES, predmet.getId(), drugiPredmet.getId());
    }

    @Test
    void promenaUpisujePreambuluIPonistavaHesSamoOvomPredmetu() {
        PreambulaInfo info = service.postavi(predmet.getId(), new PreambulaCmd("  " + PREAMBULA + "\n"));

        assertEquals(PREAMBULA, info.preambula());
        assertEquals(PREAMBULA, preambulaUBazi(predmet));
        assertEquals(PREAMBULA, service.preambula(predmet.getId()).preambula());
        varijante.forEach(id -> assertNull(hes(id)));
        assertEquals(HES, hes(tudjaVarijanta));
        assertNull(preambulaUBazi(drugiPredmet));
    }

    @Test
    void istaVrednostNeDiraKes() {
        service.postavi(predmet.getId(), new PreambulaCmd(PREAMBULA));
        vratiHesove();

        service.postavi(predmet.getId(), new PreambulaCmd(PREAMBULA + "\n"));

        varijante.forEach(id -> assertEquals(HES, hes(id)));
    }

    @Test
    void praznoVracaPodrazumevanuIPonistavaKes() {
        service.postavi(predmet.getId(), new PreambulaCmd(PREAMBULA));
        vratiHesove();

        PreambulaInfo info = service.postavi(predmet.getId(), new PreambulaCmd("   "));

        assertTrue(info.podrazumevana());
        assertNull(preambulaUBazi(predmet));
        varijante.forEach(id -> assertNull(hes(id)));
        assertEquals(HES, hes(tudjaVarijanta));
    }

    @Test
    void odbijenaPreambulaNeMenjaNistaUBazi() {
        service.postavi(predmet.getId(), new PreambulaCmd(PREAMBULA));
        vratiHesove();

        assertThrows(SystemException.class, () -> service.postavi(predmet.getId(), new PreambulaCmd("\\write18{ls}")));

        assertEquals(PREAMBULA, preambulaUBazi(predmet));
        varijante.forEach(id -> assertEquals(HES, hes(id)));
    }

    @Test
    void preambulaKojaSeNeKompajliraNeMenjaNistaUBazi() {
        service.postavi(predmet.getId(), new PreambulaCmd(PREAMBULA));
        vratiHesove();
        when(latexRender.renderuj(eq("svg"), contains("nepostoji"), anyList())).thenReturn(new RenderOdgovor(false, null, null,
                new RenderGreskaInfo(2, "LaTeX Error: File `nepostoji.sty' not found."), null, null, false));

        SystemException x = assertThrows(SystemException.class,
                () -> service.postavi(predmet.getId(), new PreambulaCmd("\\usepackage{nepostoji}")));

        assertEquals(400, x.getCode());
        assertEquals("Preambula se ne kompajlira (linija 1): LaTeX Error: File `nepostoji.sty' not found.", x.getMessage());
        assertEquals(PREAMBULA, preambulaUBazi(predmet));
        varijante.forEach(id -> assertEquals(HES, hes(id)));
    }

    @Test
    void preambulaOdPedesetHiljadaZnakovaStajeUKolonu() {
        String duga = "%" + "š".repeat(49_999);
        service.postavi(predmet.getId(), new PreambulaCmd(duga));
        assertEquals(duga, preambulaUBazi(predmet));
    }
}
