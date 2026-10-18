package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.ClassUtil;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PrilogCmd;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.PoljaJson;
import tri.novica.gfssystem.utility.Promena;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Sirovi argumenti MCP alata ({@link CallToolRequest#arguments()}), čitani strogo (spec MCP 4.1). Tipizirani parametri
 * metode alata postoje zbog šeme; prisustvo ključa i {@code null} se vide samo ovde:
 * <ul>
 *   <li>{@link #promena}: merge patch (RFC 7386), odsutan ključ ostaje, {@code null} briše samo brisivo polje;</li>
 *   <li>{@link #strogo}: objekat bez nepoznatih ključeva ({@code Nepoznato polje 'slajd.x'.}, putanja sa {@code [i]} za
 *       listu), bez izričitog {@code null} za primitiv (izostavljen primitiv je podrazumevana vrednost) i bez
 *       pretvaranja skalara (broj iz stringa, string iz broja, ceo iz decimalnog); sve to je
 *       {@link McpGreske#NEISPRAVAN_FORMAT}.</li>
 * </ul>
 * Isti strogi mapper koristi {@link McpOmotac} za objektne argumente svakog alata, pre vezivanja Spring AI-ja.
 */
public final class McpPatch {

    private static final JsonMapper STROGI = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            // broj ili boolean nikad ne postaje String ("naziv":5, "tekst":true)
            .withCoercionConfig(LogicalType.Textual, c -> c
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .addHandler(new IzostavljenPrimitiv())
            .build();

    private static final JavaType PRILOZI = STROGI.getTypeFactory().constructCollectionType(List.class, PrilogCmd.class);

    private final Map<String, Object> argumenti;

    private McpPatch(Map<String, Object> argumenti) {
        this.argumenti = argumenti;
    }

    public static McpPatch iz(CallToolRequest zahtev) {
        return new McpPatch(zahtev.arguments() == null ? Map.of() : zahtev.arguments());
    }

    public boolean ima(String kljuc) {
        return argumenti.containsKey(kljuc);
    }

    /**
     * Odsutan ključ je {@link Promena#bez()}; {@code null} je {@code Promena.na(null)} kad je polje brisivo, inače 400
     * {@code Polje '<kljuc>' se ne briše.}; vrednost se čita strogo u {@code tip}.
     */
    public <T> Promena<T> promena(String kljuc, Class<T> tip, boolean brisivo) {
        if (!argumenti.containsKey(kljuc)) {
            return Promena.bez();
        }
        Object vrednost = argumenti.get(kljuc);
        if (vrednost == null) {
            if (!brisivo) {
                throw new SystemException("Polje '" + kljuc + "' se ne briše.", HttpStatus.BAD_REQUEST);
            }
            return Promena.na(null);
        }
        return Promena.na(procitaj(kljuc, vrednost, STROGI.constructType(tip)));
    }

    /** Vrednost ključa strogo u {@code tip}; odsutan ključ ili {@code null} je {@code null}. */
    public <T> T strogo(String kljuc, Class<T> tip) {
        return strogo(kljuc, STROGI.constructType(tip));
    }

    /** Kao {@link #strogo(String, Class)}, za generičke tipove ({@code List<PrilogCmd>}). */
    public <T> T strogo(String kljuc, JavaType tip) {
        Object vrednost = argumenti.get(kljuc);
        return vrednost == null ? null : procitaj(kljuc, vrednost, tip);
    }

    /** Šema polja kroz strogi {@link PoljaJson#polja}; odsutno ili {@code null} je {@code null}. */
    public List<Polje> polja(String kljuc) {
        Object vrednost = argumenti.get(kljuc);
        return vrednost == null ? null : PoljaJson.polja(uJson(vrednost));
    }

    /** Tačni odgovori kroz strogi {@link PoljaJson#odgovori}; odsutno ili {@code null} je {@code null}. */
    public Map<String, OdgovorPolja> odgovori(String kljuc) {
        Object vrednost = argumenti.get(kljuc);
        return vrednost == null ? null : PoljaJson.odgovori(uJson(vrednost));
    }

    public List<PrilogCmd> prilozi(String kljuc) {
        return strogo(kljuc, PRILOZI);
    }

    /** Jackson tip parametra metode alata (za {@link McpOmotac}). */
    static JavaType tip(Type tip) {
        return STROGI.constructType(tip);
    }

    /**
     * Strogo čitanje jedne vrednosti; nepoznat ključ je 400 {@code Nepoznato polje '<kljuc><putanja>'.}, svaka druga
     * greška 400 {@link McpGreske#NEISPRAVAN_FORMAT} (bez teksta Jackson-a: on ide samo nazad Klodu kao opšta poruka).
     */
    static <T> T procitaj(String kljuc, Object vrednost, JavaType tip) {
        try {
            JsonNode cvor = STROGI.valueToTree(vrednost);
            return STROGI.treeToValue(cvor, tip);
        } catch (UnrecognizedPropertyException e) {
            throw new SystemException("Nepoznato polje '" + kljuc + putanja(e) + "'.", HttpStatus.BAD_REQUEST);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Strogo pročitana vrednost nazad u običan JSON oblik (mapa, lista, skalar), sa svim svojstvima tipa: izostavljen
     * primitiv postaje podrazumevana vrednost. {@link McpOmotac} tako predaje objektni argument Spring AI vezivanju,
     * koje bi izostavljen primitiv record-a odbilo.
     */
    static Object kaoJson(Object procitano) {
        try {
            return STROGI.treeToValue(STROGI.valueToTree(procitano), Object.class);
        } catch (JacksonException e) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
    }

    /** {@code .ime} za polje objekta, {@code [i]} za element liste. */
    private static String putanja(JacksonException e) {
        StringBuilder putanja = new StringBuilder();
        for (JacksonException.Reference r : e.getPath()) {
            if (r.getPropertyName() != null) {
                putanja.append('.').append(r.getPropertyName());
            } else if (r.getIndex() >= 0) {
                putanja.append('[').append(r.getIndex()).append(']');
            }
        }
        return putanja.toString();
    }

    /**
     * {@code FAIL_ON_NULL_FOR_PRIMITIVES} u Jackson-u 3 obara i izostavljen primitiv record-a (odsutan parametar
     * konstruktora se čita kao {@code null}). Izostavljen ključ je u merge patch-u "bez promene", pa dobija
     * podrazumevanu vrednost ({@code false}, {@code 0}); samo izričit {@code null} (parser stoji na {@code null}) ostaje
     * greška.
     */
    private static final class IzostavljenPrimitiv extends DeserializationProblemHandler {
        @Override
        public Object handleNullForPrimitives(DeserializationContext ctxt, Class<?> tip, JsonParser p,
                                              ValueDeserializer<?> deser, String poruka) {
            return p.currentToken() == JsonToken.VALUE_NULL ? NOT_HANDLED : ClassUtil.defaultValue(tip);
        }
    }

    private static String uJson(Object vrednost) {
        try {
            return STROGI.writeValueAsString(vrednost);
        } catch (JacksonException e) {
            throw new SystemException(McpGreske.NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
    }
}
