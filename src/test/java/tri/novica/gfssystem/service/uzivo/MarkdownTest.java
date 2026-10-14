package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownTest {

    @Test void brojiStavkeBezUvlacenja() { assertEquals(3, Markdown.brojStavki("- a\n- b\n  - c\n\ntekst\n1. d")); }

    @Test void nullIPrazno() {
        assertEquals(0, Markdown.brojStavki(null));
        assertEquals(0, Markdown.brojStavki(""));
    }

    @Test void svaTriZnakaIBrojeviSaZagradom() { assertEquals(4, Markdown.brojStavki("* a\n+ b\n10) c\n2. d")); }

    @Test void bezRazmakaNijeStavka() { assertEquals(0, Markdown.brojStavki("-a\n1.b\n**bold**")); }

    @Test void windowsKrajeviLinija() { assertEquals(2, Markdown.brojStavki("- a\r\n- b\r\ntekst")); }
}
