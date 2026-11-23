package tri.novica.gfssystem.service.uzivo;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;
import tri.novica.gfssystem.dto.uzivo.DokumentCrteza;
import tri.novica.gfssystem.dto.uzivo.ElementCrteza;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.validation.PlatnoPP;

import java.util.List;

/**
 * Strogi čitač i pisac crteža (spec crtanja 4.1), po uzoru na {@link PlatnoJson}: telo iz klijenta ne sme da tiho izgubi
 * nepoznato polje ni da pretvori tekst u broj. Kolona se čita blago (kasnija faza može dodati polja elementu).
 */
@Component
public class CrtanjeJson {

    private static final int MAX_PORUKA = 200;
    private static final TypeReference<List<ElementCrteza>> ELEMENTI = new TypeReference<>() { };

    private static final JsonMapper STROGI = osnovniMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final JsonMapper BLAGI = osnovniMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private static JsonMapper.Builder osnovniMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(LogicalType.Textual, c -> c
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.ALWAYS));
    }

    /** Telo upisa ({@code {elementi: [...]}}); oblik i tipovi strogo, granice proverava {@link CrtanjePravila}. */
    public DokumentCrteza procitaj(JsonNode telo) {
        if (telo == null || telo.isNull() || telo.isMissingNode()) {
            throw greska("telo nedostaje");
        }
        try {
            DokumentCrteza d = STROGI.treeToValue(telo, DokumentCrteza.class);
            if (d == null) {
                throw greska("telo nedostaje");
            }
            return d;
        } catch (JacksonException e) {
            throw greska(razlog(e));
        }
    }

    public List<ElementCrteza> izKolone(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ElementCrteza> el = BLAGI.readValue(json, ELEMENTI);
            return el == null ? List.of() : el;
        } catch (JacksonException e) {
            throw new SystemException("Sačuvan crtež se ne može pročitati.", 500);
        }
    }

    public String zapisi(List<ElementCrteza> elementi) {
        try {
            return STROGI.writeValueAsString(elementi);
        } catch (JacksonException e) {
            throw new SystemException("Crtež se ne može zapisati.", 500);
        }
    }

    static SystemException greska(String razlog) {
        String r = razlog.length() > MAX_PORUKA ? razlog.substring(0, MAX_PORUKA) : razlog;
        return new SystemException("Neispravan crtež: " + r + ".", 400);
    }

    /** Fiksan razlog po vrsti izuzetka plus putanja (samo imena polja i indeksi); nikad vrednosti iz ulaza. */
    private static String razlog(JacksonException e) {
        String opis;
        if (e instanceof UnrecognizedPropertyException u) {
            opis = "nepoznato polje '" + PlatnoPP.zaPoruku(u.getPropertyName()) + "'";
        } else if (e instanceof InvalidFormatException) {
            opis = "nepoznata vrednost";
        } else if (e instanceof MismatchedInputException) {
            opis = "pogrešan tip vrednosti";
        } else if (e instanceof StreamReadException) {
            opis = "neispravan JSON";
        } else {
            opis = "neispravan oblik";
        }
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference r : e.getPath()) {
            if (r.getPropertyName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(PlatnoPP.zaPoruku(r.getPropertyName()));
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.length() == 0 ? opis : opis + " (" + sb + ")";
    }
}
