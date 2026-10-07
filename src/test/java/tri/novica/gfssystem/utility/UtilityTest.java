package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;

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
}
