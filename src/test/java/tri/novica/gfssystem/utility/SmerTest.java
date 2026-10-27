package tri.novica.gfssystem.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmerTest {

    @Test
    void geodezijaJeGdISeNalaziPoSifri() {
        assertEquals("GD", Smer.GEODEZIJA.sifra());
        assertEquals("Geodezija", Smer.GEODEZIJA.naziv());
        assertEquals(List.of("GD"), Smer.sifre());
        assertEquals(Smer.GEODEZIJA, Smer.poSifri("GD").orElseThrow());
        assertTrue(Smer.poSifri("gd").isEmpty());
        assertTrue(Smer.poSifri(null).isEmpty());
    }

    @Test
    void sifreSuJedinstveneIUOblikuKojiKanonskiIndeksPrepoznaje() {
        assertEquals(Smer.values().length, new HashSet<>(Smer.sifre()).size());
        for (Smer s : Smer.values()) {
            assertTrue(s.sifra().matches("[A-Z]{1,4}"), s.sifra());
            assertFalse(s.naziv().isBlank(), s.name());
            assertEquals(s.sifra() + "7", IndeksUtil.kanonski(s.sifra().toLowerCase() + " 007", null));
            assertTrue(IndeksUtil.jeJavniOblik(s.sifra() + "123"), s.sifra());
        }
        assertFalse(IndeksUtil.jeJavniOblik("XX12"));
    }
}
