package tri.novica.gfssystem.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tri.novica.gfssystem.entity.TestGrupa;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class TestGrupaConverter implements AttributeConverter<Set<TestGrupa>, String> {

    @Override
    public String convertToDatabaseColumn(Set<TestGrupa> attribute) {
        return attribute != null ? attribute.stream()
                .map(Enum::name)
                .collect(Collectors.joining(",")) : "";
    }

    @Override
    public Set<TestGrupa> convertToEntityAttribute(String dbData) {
        return dbData != null && !dbData.isEmpty()
                ? Arrays.stream(dbData.split(","))
                .map(TestGrupa::valueOf)
                .collect(Collectors.toSet())
                : Set.of();
    }
}
