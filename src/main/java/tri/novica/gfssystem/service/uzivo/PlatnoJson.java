package tri.novica.gfssystem.service.uzivo;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.type.LogicalType;
import tri.novica.gfssystem.validation.PlatnoPP;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.exceptions.SystemException;

import java.nio.charset.StandardCharsets;

/**
 * Strogi čitač i pisac dokumenta platna. Ima sopstveni mapper: aplikacioni ({@code use-jackson2-defaults})
 * ignoriše nepoznata polja, a dokument iz klijenta ne sme da ih tiho izgubi.
 */
@Component
public class PlatnoJson {

    private static final int MAX_PORUKA = 200;

    private static final JsonMapper STROGI = osnovniMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Isti mapper bez FAIL_ON_UNKNOWN_PROPERTIES, samo za čitanje sačuvane kolone: kasnija faza može da doda polja, a
     * vraćena (starija) slika mora da ih pročita. Ulaz iz zahteva ({@link #procitaj}) ostaje strog.
     */
    private static final JsonMapper BLAGI = osnovniMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static JsonMapper.Builder osnovniMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                // broj ili boolean nikad ne postaje String ("id":12, "tekst":true)
                .withCoercionConfig(LogicalType.Textual, c -> c
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.ALWAYS));
    }

    public Platno procitaj(JsonNode cvor) {
        if (cvor == null || cvor.isNull() || cvor.isMissingNode()) {
            throw greska("dokument nedostaje");
        }
        try {
            Platno p = STROGI.treeToValue(cvor, Platno.class);
            if (p == null) {
                throw greska("dokument nedostaje");
            }
            return p;
        } catch (JacksonException e) {
            throw greska(razlog(e));
        }
    }

    public Platno izKolone(String json) {
        if (json == null) {
            return null;
        }
        try {
            return BLAGI.readValue(json, Platno.class);
        } catch (JacksonException e) {
            throw greska(razlog(e));
        }
    }

    public String zapisi(Platno p) {
        try {
            return STROGI.writeValueAsString(p);
        } catch (JacksonException e) {
            throw new SystemException("Dokument platna se ne može zapisati.", 500);
        }
    }

    public int velicinaBajtova(Platno p) {
        return zapisi(p).getBytes(StandardCharsets.UTF_8).length;
    }

    /** Broj animacija sa okidačem {@code KLIK}, brzo preko stabla; null ili prazno je 0. */
    public static int brojKoraka(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            JsonNode animacije = STROGI.readTree(json).path("animacije");
            int n = 0;
            for (JsonNode a : animacije) {
                if ("KLIK".equals(a.path("okidac").asString(null))) {
                    n++;
                }
            }
            return n;
        } catch (JacksonException e) {
            return 0;
        }
    }

    private static SystemException greska(String razlog) {
        return new SystemException("Neispravan dokument platna: " + razlog + ".", 400);
    }

    /** Fiksan razlog po vrsti izuzetka plus putanja (samo imena polja i indeksi); nikad vrednosti iz ulaza. */
    private static String razlog(JacksonException e) {
        String opis;
        if (e instanceof UnrecognizedPropertyException u) {
            opis = "nepoznato polje '" + PlatnoPP.zaPoruku(u.getPropertyName()) + "'";
        } else if (e instanceof InvalidTypeIdException) {
            opis = "nepoznat tip sloja";
        } else if (e instanceof MismatchedInputException) {
            opis = "pogrešan tip vrednosti";
        } else if (e instanceof StreamReadException) {
            opis = "neispravan JSON";
        } else {
            opis = "neispravan oblik";
        }
        String putanja = putanja(e);
        String r = putanja.isEmpty() ? opis : opis + " (" + putanja + ")";
        return r.length() > MAX_PORUKA ? r.substring(0, MAX_PORUKA) : r;
    }

    private static String putanja(JacksonException e) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference r : e.getPath()) {
            if (r.getPropertyName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(PlatnoPP.zaPoruku(r.getPropertyName()));
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.toString();
    }
}
