package tri.novica.gfssystem.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Lista stringova kao JSON niz u tekstualnoj koloni ({@code ["a","b"]}); {@code null} ostaje {@code null}.
 * Konverter pravi Hibernate, ne Spring, pa ima sopstveni {@link JsonMapper} (bez podešavanja: samo niz stringova).
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LISTA = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<String> lista) {
        return lista == null ? null : JSON.writeValueAsString(lista);
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        return json == null ? null : JSON.readValue(json, LISTA);
    }
}
