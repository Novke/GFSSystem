package tri.novica.gfssystem.entity.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringListJsonConverterTest {

    final StringListJsonConverter konverter = new StringListJsonConverter();

    @Test
    void nullOstajeNull() {
        assertNull(konverter.convertToDatabaseColumn(null));
        assertNull(konverter.convertToEntityAttribute(null));
    }

    @Test
    void praznaListaJePrazanNiz() {
        assertEquals("[]", konverter.convertToDatabaseColumn(List.of()));
        assertEquals(List.of(), konverter.convertToEntityAttribute("[]"));
    }

    @Test
    void listaKrozKrugOstajeIsta() {
        List<String> lista = List.of("a", "Čvor");
        String json = konverter.convertToDatabaseColumn(lista);
        assertEquals("[\"a\",\"Čvor\"]", json);
        assertEquals(lista, konverter.convertToEntityAttribute(json));
    }
}
