package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;

import static org.junit.jupiter.api.Assertions.*;

class UtilityTest {

    @Test
    void index2intCitaBrojNaKrajuIndeksa() {
        assertEquals(12, Utility.index2int("GD12"));
        assertEquals(5, Utility.index2int("GD005"));
    }

    @Test
    void index2intPredugackiBrojIdeNaKrajUmestoGreske() {
        // npr. broj telefona upisan kao indeks; Integer.parseInt bi bacio NumberFormatException (500 na stranici grupe)
        assertEquals(Integer.MAX_VALUE, Utility.index2int("381641234567"));
    }

    private static Grupa g(long id, int godina) {
        Grupa grupa = new Grupa();
        grupa.setId(id);
        grupa.setGodinaUpisa(godina);
        return grupa;
    }

    private static Student s(Grupa grupa) {
        Student student = new Student();
        student.setGrupa(grupa);
        return student;
    }

    @Test
    void smeNaNastavuIsteGrupe() {
        assertTrue(Utility.smeNaNastavuGrupe(s(g(1, 2025)), g(1, 2025)));
    }

    @Test
    void smeNaNastavuMladjeGrupeAkoJeStariji() {
        assertTrue(Utility.smeNaNastavuGrupe(s(g(1, 2024)), g(2, 2025)));
    }

    @Test
    void neSmeNaNastavuStarijeGrupeAkoJeMladji() {
        assertFalse(Utility.smeNaNastavuGrupe(s(g(3, 2026)), g(2, 2025)));
    }

    @Test
    void drugaGrupaIsteGodineNijeDozvoljena() {
        assertFalse(Utility.smeNaNastavuGrupe(s(g(3, 2025)), g(2, 2025)));
    }

    @Test
    void studentBezGrupeNeSmeNigde() {
        assertFalse(Utility.smeNaNastavuGrupe(s(null), g(2, 2025)));
        assertFalse(Utility.smeNaNastavuGrupe(null, g(2, 2025)));
    }

    @Test
    void grupaBezGodineDozvoljavaSamoSvojeStudente() {
        assertFalse(Utility.smeNaNastavuGrupe(s(g(1, 2024)), grupaBezGodine(2)));
        assertTrue(Utility.smeNaNastavuGrupe(s(grupaBezGodine(2)), grupaBezGodine(2)));
    }

    private static Grupa grupaBezGodine(long id) {
        Grupa grupa = new Grupa();
        grupa.setId(id);
        return grupa;
    }
}
