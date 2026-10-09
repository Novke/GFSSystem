package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledTestInfo;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.TipTesta;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Pregled studenta: prag i prolaz računa server po pravilu {@link Prolaz}, sačuvano polje {@code polozio} se ne izlaže. */
class StudentMapperTest {

    private Polaganje polaganje(Integer prag, Double poeni, Boolean prepisivao, Boolean polozio) {
        var test = new tri.novica.gfssystem.entity.Test();
        test.setId(7L);
        test.setDatum(LocalDate.of(2025, 11, 1));
        test.setTipTesta(new TipTesta("Kolokvijum", new Predmet()));
        test.setMaxPoena(40);
        test.setPragProlaza(prag);
        Polaganje p = Polaganje.defaultPolaganje(test, null);
        p.setId(1L);
        p.setOstvareniPoeni(poeni);
        p.setPrepisivao(prepisivao);
        p.setPolozio(polozio);
        return p;
    }

    private StudentPregledTestInfo map(Polaganje p) {
        return StudentMapper.INSTANCE.toTestDetails(p);
    }

    @Test
    void polozenNaPragu() {
        StudentPregledTestInfo i = map(polaganje(20, 20.0, false, null));
        assertEquals(20, i.getPragProlaza());
        assertEquals(Boolean.TRUE, i.getPolozeno());
        assertEquals(7L, i.getTestId());
        assertEquals(20.0, i.getOstvareniPoeni());
    }

    @Test
    void padIspodPragaIgnorisePoljePolozio() {
        StudentPregledTestInfo i = map(polaganje(20, 19.5, false, true));
        assertEquals(Boolean.FALSE, i.getPolozeno());
        assertEquals(Boolean.TRUE, map(polaganje(20, 30.0, false, false)).getPolozeno());
    }

    @Test
    void prepisivaoNijePolozio() {
        StudentPregledTestInfo i = map(polaganje(20, 40.0, true, null));
        assertEquals(Boolean.FALSE, i.getPolozeno());
        assertTrue(i.isPrepisivao());
    }

    @Test
    void bezPragaPolozenoJeNull() {
        StudentPregledTestInfo i = map(polaganje(null, 30.0, false, true));
        assertNull(i.getPragProlaza());
        assertNull(i.getPolozeno());
    }

    @Test
    void bezPoenaSaPragomNijeOcenjeno() {
        // nije upisano, pa ni "položio" ni "pao" (isti imenilac kao statistika testa: samo polaganja sa poenima)
        assertNull(map(polaganje(20, null, false, null)).getPolozeno());
        assertNull(map(polaganje(20, null, true, true)).getPolozeno());
    }
}
