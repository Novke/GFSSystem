package tri.novica.gfssystem.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Jedini izvor istine za klase pristupa (spec 2.3, tabela u sekciji 3). Putanje su backend putanje (nginx dodaje
 * {@code /api}). ANONIMNO, STUDENT i MCP su nabrojani; <b>sve ostalo je ADMIN</b> (zaboravljen endpoint je zaključan, ne
 * otvoren). {@code SecurityConfig} gradi pravila lanca iz ovih lista, a {@code PristupMatricaTest} ih poredi sa svim
 * stvarnim endpointima i sa snimkom {@code pristup-matrica.txt}.
 * <p>
 * Uzorci se porede {@link PathPatternParser#defaultInstance}-om, istim parserom kao Spring MVC i
 * {@link PathPatternRequestMatcher} u lancu: bez trailing slash-a, osetljivo na velika slova, {@code {x}} je tačno jedan
 * segment. {@code metod == null} znači svaka metoda. Liste se ne preklapaju (test), pa redosled u {@link #klasa} ne
 * menja ishod; MCP se ipak gleda prvi, jer njegov lanac ({@code @Order(1)}) hvata te putanje pre glavnog.
 * <p>
 * Uzorci čiji endpointi stižu kasnije (LTI) su ovde već sada, da njihovo svrstavanje ne zavisi od toga ko prvi merguje.
 */
public final class PristupMatrica {

    /** Jedno pravilo: metoda ({@code null} = sve) i PathPattern uzorak. */
    public record Pravilo(HttpMethod metod, String uzorak) {
        public Pravilo {
            Objects.requireNonNull(uzorak, "uzorak");
        }

        boolean pogadja(HttpMethod m, PathContainer putanja) {
            if (metod != null && !metod.equals(m)) return false;
            PathPattern sablon = UZORCI.get(uzorak);
            return (sablon != null ? sablon : PathPatternParser.defaultInstance.parse(uzorak)).matches(putanja);
        }

        RequestMatcher matcher() {
            PathPatternRequestMatcher.Builder b = PathPatternRequestMatcher.withDefaults();
            return metod == null ? b.matcher(uzorak) : b.matcher(metod, uzorak);
        }
    }

    private static final HttpMethod GET = HttpMethod.GET;
    private static final HttpMethod POST = HttpMethod.POST;
    private static final HttpMethod PUT = HttpMethod.PUT;
    private static final HttpMethod DELETE = HttpMethod.DELETE;

    private static final List<Pravilo> ANONIMNO = List.of(
            // samostalni upis studenta (QR)
            new Pravilo(GET, "/public/upis/{token}"),
            new Pravilo(POST, "/public/upis/{token}"),
            // Uživo: telefon studenta; bodovan run traži identitet u servisu (faza 2), ne ovde
            new Pravilo(GET, "/public/uzivo/{kod}"),
            new Pravilo(GET, "/public/uzivo/{kod}/ja"),
            new Pravilo(POST, "/public/uzivo/{kod}/prijava"),
            new Pravilo(GET, "/public/mediji/{uuid}"),
            // studentski WebSocket: rukovanje i dalje traži kolačić učesnika (UzivoHandshakeInterceptor)
            new Pravilo(null, "/public/ws"),
            // studentski nalog bez lozinke
            new Pravilo(POST, "/public/nalog/kod"),
            new Pravilo(POST, "/public/nalog/prijava"),
            new Pravilo(POST, "/public/nalog/pomoc"),
            new Pravilo(GET, "/public/nalog/stanje"),
            // čekiranje QR-om: prijavljen se čekira odmah, neprijavljen dobija ključ (student iz sesije, ako postoji)
            new Pravilo(POST, "/public/nalog/cas/skeniraj"),
            // Moodle LTI 1.3 (prijava i start su i izuzeti iz provere porekla)
            new Pravilo(GET, "/public/lti/prijava"),
            new Pravilo(POST, "/public/lti/prijava"),
            new Pravilo(POST, "/public/lti/start"),
            new Pravilo(GET, "/public/lti/jwks"),
            // ime Moodle naloga za ekran povezivanja: samo čita, uz kolačić __Host-gfs_lti_par (bez njega 410)
            new Pravilo(GET, "/public/lti/povezivanje"),
            // prijava nastavnika: nastavničko, pa namerno van /public; /auth/ja bez sesije vraća 401 sam kontroler
            new Pravilo(POST, "/auth/prijava"),
            new Pravilo(GET, "/auth/ja"));

    private static final List<Pravilo> STUDENT = List.of(
            new Pravilo(GET, "/public/nalog/ja"),
            new Pravilo(POST, "/public/nalog/odjava"),
            new Pravilo(POST, "/public/nalog/odjava-svuda"),
            // čekiranje iz ključa posle prijave i stanje časa (telefon prati čas)
            new Pravilo(POST, "/public/nalog/cas/potvrdi"),
            new Pravilo(GET, "/public/nalog/cas"),
            // naredni časovi i najava odsustva (student samo iz sesije)
            new Pravilo(GET, "/public/nalog/casovi"),
            new Pravilo(POST, "/public/nalog/casovi/najavi"),
            new Pravilo(POST, "/public/nalog/casovi/povuci"),
            // nalog studenta, faze 2 i 3 (spec studentskog dela 12.7): predmeti, istorija, vežbanje, pregled, ocena; svaki
            // servis ograničava po studentu iz sesije i njegovim grupama (tuđe = 404)
            new Pravilo(GET, "/public/nalog/predmeti"),
            new Pravilo(GET, "/public/nalog/predmeti/{id}/prisustvo"),
            new Pravilo(GET, "/public/nalog/predmeti/{id}/vezbanje"),
            new Pravilo(GET, "/public/nalog/predmeti/{id}/moji-zadaci"),
            new Pravilo(GET, "/public/nalog/predmeti/{id}/prezentacije"),
            new Pravilo(GET, "/public/nalog/predmeti/{id}/ocena"),
            new Pravilo(GET, "/public/nalog/prezentacije/{id}"),
            new Pravilo(POST, "/public/nalog/vezbanje"),
            new Pravilo(POST, "/public/nalog/vezbanje/{pokusajId}/provera"),
            new Pravilo(POST, "/public/nalog/vezbanje/{pokusajId}/resenje"),
            // podešavanja naloga (tab "Ja"): mejl o otkazanom času
            new Pravilo(GET, "/public/nalog/podesavanja"),
            new Pravilo(PUT, "/public/nalog/podesavanja"),
            // push na ovom uređaju i zvono naloga (spec aplikacije 5.4 i 6.8): uređaj i student samo iz sesije
            new Pravilo(GET, "/public/nalog/push"),
            new Pravilo(PUT, "/public/nalog/push"),
            new Pravilo(DELETE, "/public/nalog/push"),
            new Pravilo(POST, "/public/nalog/push/proba"),
            new Pravilo(GET, "/public/nalog/obavestenja"),
            new Pravilo(POST, "/public/nalog/obavestenja/otvoreno"),
            // povezivanje traži i studenta i važeći __Host-gfs_lti_par (proverava servis)
            new Pravilo(POST, "/public/lti/povezi"));

    /**
     * Tačno {@code /mcp} (Spring AI {@code RouterFunction}), {@code /mcp/prilozi} i {@code /mcp/slike} ({@code McpUploadRest}),
     * svaka metoda, bez prefiksa: isto što hvata MCP lanac. Ključ proverava {@code McpKljucFilter} (servlet filter pre lanca,
     * 401 Bearer, 404 kad je MCP isključen); ostalo ispod {@code /mcp} je ADMIN, pa ključ ne otvara ništa drugo.
     */
    private static final List<Pravilo> MCP = List.of(
            new Pravilo(null, "/mcp"),
            new Pravilo(null, "/mcp/prilozi"),
            new Pravilo(null, "/mcp/slike"));

    private static final Map<String, PathPattern> UZORCI = new LinkedHashMap<>();

    static {
        for (List<Pravilo> lista : List.of(ANONIMNO, STUDENT, MCP)) {
            for (Pravilo p : lista) {
                UZORCI.computeIfAbsent(p.uzorak(), PathPatternParser.defaultInstance::parse);
            }
        }
    }

    private PristupMatrica() {}

    public static List<Pravilo> anonimno() {
        return ANONIMNO;
    }

    public static List<Pravilo> student() {
        return STUDENT;
    }

    public static List<Pravilo> mcp() {
        return MCP;
    }

    /** Klasa zahteva {@code metod putanja} (putanja ili uzorak, npr. {@code /studenti/{id}}); ADMIN kad ništa ne pogodi. */
    public static KlasaPristupa klasa(HttpMethod metod, String putanja) {
        PathContainer p = PathContainer.parsePath(putanja);
        if (pogadja(MCP, metod, p)) return KlasaPristupa.MCP;
        if (pogadja(ANONIMNO, metod, p)) return KlasaPristupa.ANONIMNO;
        if (pogadja(STUDENT, metod, p)) return KlasaPristupa.STUDENT;
        return KlasaPristupa.ADMIN;
    }

    /** Sve nabrojane klase čija pravila pogađaju zahtev (za proveru nepreklapanja; prazno = ADMIN). */
    public static Set<KlasaPristupa> pogodjene(HttpMethod metod, String putanja) {
        PathContainer p = PathContainer.parsePath(putanja);
        Set<KlasaPristupa> klase = EnumSet.noneOf(KlasaPristupa.class);
        if (pogadja(MCP, metod, p)) klase.add(KlasaPristupa.MCP);
        if (pogadja(ANONIMNO, metod, p)) klase.add(KlasaPristupa.ANONIMNO);
        if (pogadja(STUDENT, metod, p)) klase.add(KlasaPristupa.STUDENT);
        return klase;
    }

    /** Matcher za lanac: bilo koje pravilo iz liste. */
    static RequestMatcher matcher(List<Pravilo> pravila) {
        return new OrRequestMatcher(pravila.stream().map(Pravilo::matcher).toList());
    }

    private static boolean pogadja(List<Pravilo> pravila, HttpMethod metod, PathContainer putanja) {
        for (Pravilo p : pravila) {
            if (p.pogadja(metod, putanja)) return true;
        }
        return false;
    }
}
