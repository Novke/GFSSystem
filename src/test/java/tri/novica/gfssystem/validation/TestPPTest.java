package tri.novica.gfssystem.validation;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Polaganje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.TestGrupa;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pravilo "student na testu": isti smer ili stariji (ponavlja predmet), nikad mlađi. */
class TestPPTest {

    final TestPP testPP = new TestPP();

    static Grupa grupa(long id, String naziv, Integer godina) {
        Grupa g = new Grupa();
        g.setId(id);
        g.setNaziv(naziv);
        g.setGodinaUpisa(godina);
        return g;
    }

    static Polaganje polaganje(Grupa grupaTesta, Grupa grupaStudenta) {
        var test = new tri.novica.gfssystem.entity.Test();
        test.setGrupa(grupaTesta);
        test.setMaxPoena(100);
        test.setPregledan(false);
        test.setGrupe(Set.of(TestGrupa.A));
        Student s = new Student();
        s.setId(1L);
        s.setIndeks("GD1/22");
        s.setGrupa(grupaStudenta);
        Polaganje p = new Polaganje();
        p.setTest(test);
        p.setStudent(s);
        p.setGrupa(TestGrupa.A);
        p.setOstvareniPoeni(50.0);
        return p;
    }

    @Test
    void studentIzIsteGrupeJeDozvoljen() {
        Grupa g = grupa(1, "GD-2025", 2025);
        assertDoesNotThrow(() -> testPP.checkCreatePolaganje(polaganje(g, g)));
    }

    @Test
    void studentIzIsteGrupeJeDozvoljenIKadaJeDrugaInstancaIstogReda() {
        assertDoesNotThrow(() -> testPP.checkCreatePolaganje(
                polaganje(grupa(1, "GD-2025", 2025), grupa(1, "GD-2025", 2025))));
    }

    @Test
    void studentIzStarijeGrupeJeDozvoljen() {
        assertDoesNotThrow(() -> testPP.checkCreatePolaganje(
                polaganje(grupa(2, "GD-2025", 2025), grupa(1, "GD-2024", 2024))));
    }

    @Test
    void studentIzMladjeGrupeNijeDozvoljen() {
        var ex = assertThrows(SystemException.class, () -> testPP.checkCreatePolaganje(
                polaganje(grupa(2, "GD-2025", 2025), grupa(3, "GD-2026", 2026))));
        assertEquals("Student GD1/22 ne pripada grupi GD-2025", ex.getMessage());
    }

    @Test
    void studentBezGrupeNijeDozvoljen() {
        var ex = assertThrows(SystemException.class, () -> testPP.checkCreatePolaganje(
                polaganje(grupa(2, "GD-2025", 2025), null)));
        assertEquals("Student GD1/22 ne pripada grupi GD-2025", ex.getMessage());
    }
}
