package tri.novica.gfssystem.service.banka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Čitanje i pisanje JSON-a šeme polja ({@code GrupaZadataka.polja}) i tačnih odgovora ({@code Varijanta.odgovori}).
 * Sopstveni mapper kao {@code PlatnoJson}: aplikacioni ignoriše nepoznata polja, a ulaz ne sme da ih tiho izgubi.
 * Strogi čitači ({@link #polja}, {@link #odgovori}) odbijaju nepoznato polje, pogrešan tip, {@code null} element,
 * ponovljen ključ i višak tokena sa 400 {@code Neispravan format podataka.}; čitači kolone ({@link #izKolone},
 * {@link #odgovoriIzKolone}) trpe samo nepoznata polja (starija slika posle rollbacka), a prazna kolona je prazan skup.
 * Ne proverava pravila: to radi {@code BankaPP}.
 */
public final class PoljaJson {

    private static final String PORUKA = "Neispravan format podataka.";
    private static final TypeReference<List<Polje>> POLJA = new TypeReference<>() {};
    private static final TypeReference<Map<String, OdgovorPolja>> ODGOVORI = new TypeReference<>() {};

    private static final JsonMapper STROGI = osnovniMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final JsonMapper BLAGI = osnovniMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private PoljaJson() {}

    private static JsonMapper.Builder osnovniMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                // broj ili boolean nikad ne postaje String ("kljuc":1, "tekst":true)
                .withCoercionConfig(LogicalType.Textual, c -> c
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // [null] i {"a":null} nisu ni polje ni odgovor
                .changeDefaultNullHandling(n -> n.withContentNulls(Nulls.FAIL))
                // u koloni ostaju samo postavljena svojstva ({"broj":45.0}), kao u primerima iz spec-a
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL));
    }

    /** Šema polja iz zahteva: strogo; {@code null}, prazan ulaz i JSON {@code null} su 400. */
    public static List<Polje> polja(String json) {
        return procitaj(STROGI, json, POLJA);
    }

    /** Tačni odgovori iz zahteva: strogo; {@code null}, prazan ulaz i JSON {@code null} su 400. */
    public static Map<String, OdgovorPolja> odgovori(String json) {
        return procitaj(STROGI, json, ODGOVORI);
    }

    /** Sačuvana kolona {@code polja}: blago na nepoznata polja; {@code null} ili prazna kolona je prazna lista. */
    public static List<Polje> izKolone(String json) {
        return json == null || json.isBlank() ? List.of() : procitaj(BLAGI, json, POLJA);
    }

    /** Sačuvana kolona {@code odgovori}: blago na nepoznata polja; {@code null} ili prazna kolona je prazna mapa. */
    public static Map<String, OdgovorPolja> odgovoriIzKolone(String json) {
        return json == null || json.isBlank() ? Map.of() : procitaj(BLAGI, json, ODGOVORI);
    }

    public static String uJson(List<Polje> polja) {
        return zapisi(polja == null ? List.of() : polja);
    }

    public static String uJson(Map<String, OdgovorPolja> odgovori) {
        return zapisi(odgovori == null ? new LinkedHashMap<>() : odgovori);
    }

    /**
     * {@link #polja} za polje komande ({@code @JsonDeserialize(using = PoljaJson.StrogaPolja.class)}): aplikacioni mapper
     * bi nepoznato svojstvo polja tiho preskočio. Greška je 400 {@code Neispravan format podataka.} kroz
     * {@code ApiExceptionHandler}; za JSON {@code null} se ne poziva, pa ostaje {@code null}.
     */
    public static final class StrogaPolja extends ValueDeserializer<List<Polje>> {
        @Override
        public List<Polje> deserialize(JsonParser p, DeserializationContext ctxt) {
            String json = ctxt.readTree(p).toString();
            try {
                return polja(json);
            } catch (SystemException e) {
                return ctxt.reportInputMismatch(this, PORUKA);
            }
        }
    }

    /**
     * {@link #odgovori} za polje komande ({@code VarijantaCmd.odgovori}), iz istog razloga kao {@link StrogaPolja};
     * JSON {@code null} ostaje {@code null}.
     */
    public static final class StrogiOdgovori extends ValueDeserializer<Map<String, OdgovorPolja>> {
        @Override
        public Map<String, OdgovorPolja> deserialize(JsonParser p, DeserializationContext ctxt) {
            String json = ctxt.readTree(p).toString();
            try {
                return odgovori(json);
            } catch (SystemException e) {
                return ctxt.reportInputMismatch(this, PORUKA);
            }
        }
    }

    private static <T> T procitaj(JsonMapper mapper, String json, TypeReference<T> tip) {
        if (json == null || json.isBlank()) {
            throw greska();
        }
        try {
            T vrednost = mapper.readValue(json, tip);
            if (vrednost == null) {
                throw greska();
            }
            return vrednost;
        } catch (JacksonException e) {
            throw greska();
        }
    }

    private static String zapisi(Object vrednost) {
        try {
            return STROGI.writeValueAsString(vrednost);
        } catch (JacksonException e) {
            throw new SystemException("Šema odgovora se ne može zapisati.", 500);
        }
    }

    private static SystemException greska() {
        return new SystemException(PORUKA, 400);
    }
}
