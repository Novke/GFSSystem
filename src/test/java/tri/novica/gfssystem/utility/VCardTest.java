package tri.novica.gfssystem.utility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VCardTest {

    private static final String BS = "\\";

    @Test
    void kontaktSaSvimPoljima() {
        String v = VCard.zapisi(List.of(new VCard.Kontakt("Đorđe", "Šović", "GD-2026", "+381641234567", " djordje@example.com ",
                "GFS GD-2026, indeks GD12")));
        assertEquals("""
                BEGIN:VCARD\r
                VERSION:3.0\r
                N:Šović;Đorđe;;;GD-2026\r
                FN:Đorđe Šović GD-2026\r
                TEL;TYPE=CELL:+381641234567\r
                EMAIL;TYPE=INTERNET:djordje@example.com\r
                CATEGORIES:GD-2026\r
                NOTE:GFS GD-2026\\, indeks GD12\r
                END:VCARD\r
                """, v);
    }

    @Test
    void bezTelefonaIEmailaNemaTihRedova() {
        String v = VCard.zapisi(List.of(new VCard.Kontakt("Ana", null, "GD-2026", null, "  ", null)));
        assertFalse(v.contains("TEL"));
        assertFalse(v.contains("EMAIL"));
        assertFalse(v.contains("NOTE"));
        assertTrue(v.contains("N:;Ana;;;GD-2026\r\n"));
        assertTrue(v.contains("FN:Ana GD-2026\r\n"));
    }

    @Test
    void specijalniZnakoviNePomerajuPolja() {
        assertEquals("a" + BS + ";b" + BS + ",c" + BS + BS + "d" + BS + "ne", VCard.tekst("a;b,c\\d\ne"));
        String v = VCard.zapisi(List.of(new VCard.Kontakt("Ana;Marija", "Petrović\r\nX", "", null, null, null)));
        assertTrue(v.contains("N:Petrović" + BS + "nX;Ana" + BS + ";Marija;;;\r\n"), v);
    }

    @Test
    void viseKontakataJedanZaDrugim() {
        String v = VCard.zapisi(List.of(new VCard.Kontakt("A", "B", "G", "1", null, null), new VCard.Kontakt("C", "D", "G", "2", null, null)));
        assertEquals(2, v.split("BEGIN:VCARD", -1).length - 1);
        assertEquals(2, v.split("END:VCARD\r\n", -1).length - 1);
    }
}
