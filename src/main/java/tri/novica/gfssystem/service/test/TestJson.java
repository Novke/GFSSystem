package tri.novica.gfssystem.service.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.test.online.OcenaPoljaNastavnik;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON kolone online testa: snimak zadatka, unos studenta, ocena po polju, izvori stavke, ključ iz ispravke. */
public final class TestJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private static final JsonMapper STROGI = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final TypeReference<Map<String, Object>> UNETO = new TypeReference<>() {};
    private static final TypeReference<Map<String, OcenaPoljaNastavnik>> OCENA = new TypeReference<>() {};
    private static final TypeReference<List<Long>> IDS = new TypeReference<>() {};

    private TestJson() {}

    public static String uJson(Object o) {
        return MAPPER.writeValueAsString(o);
    }

    public static SnimakZadatkaTesta snimak(String json) {
        return MAPPER.readValue(json, SnimakZadatkaTesta.class);
    }

    /** Unos studenta iz kolone; prazna kolona je {@code null}. */
    public static Map<String, Object> uneto(String json) {
        if (json == null || json.isBlank()) return null;
        return MAPPER.readValue(json, UNETO);
    }

    public static Map<String, OcenaPoljaNastavnik> ocena(String json) {
        if (json == null || json.isBlank()) return Map.of();
        return MAPPER.readValue(json, OCENA);
    }

    public static List<Long> ids(String json) {
        if (json == null || json.isBlank()) return List.of();
        return MAPPER.readValue(json, IDS);
    }

    /** Ključ iz ispravke KLJUC: strog JSON {@code OdgovorPolja}, inače 400 {@code Neispravan ključ.}. */
    public static OdgovorPolja odgovorPolja(String json) {
        if (json == null || json.isBlank()) throw new SystemException("Neispravan ključ.", HttpStatus.BAD_REQUEST);
        try {
            OdgovorPolja o = STROGI.readValue(json, OdgovorPolja.class);
            if (o == null) throw new SystemException("Neispravan ključ.", HttpStatus.BAD_REQUEST);
            return o;
        } catch (JacksonException e) {
            throw new SystemException("Neispravan ključ.", HttpStatus.BAD_REQUEST);
        }
    }

    /** Ključ iz ispravke već sačuvane (blago čitanje; neispravan red se preskače). */
    public static OdgovorPolja odgovorPoljaIliNull(String json) {
        try {
            return json == null ? null : MAPPER.readValue(json, OdgovorPolja.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    public static Map<String, Object> mapa() {
        return new LinkedHashMap<>();
    }
}
