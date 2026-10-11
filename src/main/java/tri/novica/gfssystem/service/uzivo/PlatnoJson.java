package tri.novica.gfssystem.service.uzivo;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
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

    private static final JsonMapper STROGI = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.ALWAYS))
            .build();

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
            return STROGI.readValue(json, Platno.class);
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

    private static String razlog(JacksonException e) {
        String m = e.getOriginalMessage();
        if (m == null) {
            m = "neispravan oblik";
        }
        int nl = m.indexOf('\n');
        if (nl >= 0) {
            m = m.substring(0, nl);
        }
        if (m.length() > MAX_PORUKA) {
            m = m.substring(0, MAX_PORUKA);
        }
        return m;
    }
}
