package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.service.banka.LatexRender;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Osnova testova MCP alata nad pravom MySQL bazom (Flyway V1..V12, kao CI): pravi {@code /mcp} kroz MockMvc sa test ključem
 * ({@link McpTestKlijent}), lažni {@link LatexRender} (podklasa ga podešava) i bez {@code @Transactional}: svaki poziv
 * alata se zaista commit-uje, pa {@link #ocistiPredmete()} posle svakog testa JDBC-om briše sve što pripada predmetima iz
 * {@link #noviPredmet} (prilozi, varijante, grupe, isključene teme, prezentacije sa slajdovima i pitanjima, domaći) i
 * medije sa spiska {@link #mediji} i render SVG-ove varijanti, po uzoru na {@code VarijantaServiceDbTest.tearDown}.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)   // MockMvc bi posle palog testa ispisao zaglavlja, i ključ
public abstract class McpDbTestOsnova {

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PredmetRepository predmetRepository;
    @Autowired protected MedijService medijService;
    @MockitoBean protected LatexRender latexRender;

    protected McpTestKlijent mcp;

    /** Id-jevi medija koje je test napravio (prilozi, slike); brišu se posle testa, posle redova koji ih koriste. */
    protected final List<String> mediji = new ArrayList<>();

    private final List<Long> predmeti = new ArrayList<>();

    @BeforeEach
    void mcpKlijent() {
        mcp = new McpTestKlijent(mvc);
    }

    /** Nov predmet koji se briše posle testa, zajedno sa svim što mu pripada. */
    protected Predmet noviPredmet(String naziv) {
        Predmet p = new Predmet();
        p.setNaziv(naziv);
        p = predmetRepository.save(p);
        predmeti.add(p.getId());
        return p;
    }

    @AfterEach
    void ocistiPredmete() {
        Set<String> svg = new LinkedHashSet<>();
        for (Long id : predmeti) {
            String grupePredmeta = "(select id from grupe_zadataka where predmet_id = ?)";
            svg.addAll(jdbc.queryForList("select render_svg_id from varijante where render_svg_id is not null "
                    + "and grupa_id in " + grupePredmeta, String.class, id));
            svg.addAll(jdbc.queryForList("select render_resenje_svg_id from varijante where render_resenje_svg_id is not null "
                    + "and grupa_id in " + grupePredmeta, String.class, id));
            jdbc.update("delete from varijanta_prilozi where varijanta_id in (select v.id from varijante v "
                    + "join grupe_zadataka g on g.id = v.grupa_id where g.predmet_id = ?)", id);
            jdbc.update("update varijante set zamenjuje_id = null where grupa_id in " + grupePredmeta, id);
            jdbc.update("delete from varijante where grupa_id in " + grupePredmeta, id);
            jdbc.update("delete from iskljucene_teme where predmet_id = ?", id);
            jdbc.update("update grupe_zadataka set roditelj_id = null where predmet_id = ?", id);   // deca pa koreni, bilo koje dubine
            jdbc.update("delete from grupe_zadataka where predmet_id = ?", id);
            List<Long> pitanja = jdbc.queryForList("select s.pitanje_id from slajdovi s join prezentacije p "
                    + "on p.id = s.prezentacija_id where p.predmet_id = ? and s.pitanje_id is not null", Long.class, id);
            // slajdovi i izvođenja (sa učesnicima, rundama i odgovorima) idu kaskadno sa prezentacijom; pitanja posle slajdova
            jdbc.update("delete from prezentacije where predmet_id = ?", id);
            pitanja.forEach(p -> jdbc.update("delete from pitanja where id = ?", p));
            jdbc.update("delete from domaci where predmet_id = ?", id);
        }
        svg.forEach(medijService::obrisi);
        mediji.forEach(medijService::obrisi);
        predmeti.forEach(id -> jdbc.update("delete from predmeti where id = ?", id));
        predmeti.clear();
        mediji.clear();
    }
}
