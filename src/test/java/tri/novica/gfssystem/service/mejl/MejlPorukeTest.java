package tri.novica.gfssystem.service.mejl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Izgled mejla sa kodom i obaveštenja nastavniku ({@link MejlPoruke}, {@link MejlSablon}); sintetički podaci. */
class MejlPorukeTest {

    static final String FRONT = "https://gfs.example.test, http://localhost:4200";

    @Test
    void kodImaTekstIHtmlSaImenomKodomIRokom() {
        MejlPoruka p = new MejlPoruke(FRONT).kod("ana@example.test", "Ana", "042917", 10, false);
        assertEquals("ana@example.test", p.za());
        assertEquals("Kod za prijavu u GFS", p.naslov());
        assertFalse(p.naslov().contains("042917"), "kod ne ide u naslov");
        assertTrue(p.telo().startsWith("Zdravo, Ana!"), p.telo());
        assertTrue(p.telo().contains("042917"));
        assertTrue(p.telo().contains("važi 10 minuta"));
        assertTrue(p.telo().contains("upisan tvoj broj indeksa"));
        assertTrue(p.telo().contains("zanemari ovu poruku"));
        assertTrue(p.telo().contains("--\n" + MejlSablon.POTPIS + "\n"), p.telo());

        assertNotNull(p.html());
        assertTrue(p.html().contains("Zdravo, Ana!"));
        assertTrue(p.html().contains("042917"));
        assertTrue(p.html().contains(MejlSablon.POTPIS));
        assertTrue(p.html().contains("src=\"https://gfs.example.test/assets/images/mejl-logo.png\""), p.html());
        assertTrue(p.html().contains("alt=\"GFS\""));
        assertFalse(p.toString().contains("042917"), "toString bez koda");
    }

    @Test
    void bezImenaJeOpstiPozdravAOdNastavnikaDrugaRecenica() {
        MejlPoruka p = new MejlPoruke(FRONT).kod("ana@example.test", "  ​ ", "111111", 10, true);
        assertTrue(p.telo().startsWith("Zdravo!"), p.telo());
        assertTrue(p.telo().contains("poslao nastavnik"));
        assertNull(MejlPoruke.ime(null));
        assertEquals("Ana Marija", MejlPoruke.ime(" Ana\n  Marija\u0007 "));
        assertEquals(40, MejlPoruke.ime("x".repeat(80)).length());
    }

    @Test
    void imeSeEskejpujeUHtmlu() {
        MejlPoruka p = new MejlPoruke(FRONT).kod("ana@example.test", "<b>Ana</b>&", "123456", 10, false);
        assertFalse(p.html().contains("<b>Ana</b>"), p.html());
        assertTrue(p.html().contains("&lt;b&gt;Ana&lt;/b&gt;&amp;"), p.html());
    }

    @Test
    void logoSamoSaHttpsAdrese() {
        assertFalse(new MejlPoruke("http://localhost:4200").kod("a@example.test", "A", "123456", 10, false)
                .html().contains("<img"));
        assertFalse(new MejlPoruke("").kod("a@example.test", "A", "123456", 10, false).html().contains("<img"));
    }

    @Test
    void obavestenjeNastavnikuZadrzavaTekstIDobijaHtml() {
        String telo = "Student ne dobija kod.\n\nIndeks: GD5/2026\nIP: 192.0.2.1";
        MejlPoruka p = new MejlPoruke(FRONT).nastavniku("nastavnik@example.test", "GFS: obaveštenje", telo);
        assertEquals(telo, p.telo());
        assertTrue(p.html().contains("Student ne dobija kod."));
        assertTrue(p.html().contains("Indeks: GD5/2026 · IP: 192.0.2.1"), p.html());
    }
}
