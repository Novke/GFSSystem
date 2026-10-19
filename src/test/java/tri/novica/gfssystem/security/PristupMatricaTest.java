package tri.novica.gfssystem.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Matrica pristupa, fail-closed (spec 3 i 14.1). Skuplja <b>sve</b> endpointe iz koda ({@code @RequestMapping} metode,
 * {@code RouterFunction} rute, WebSocket ulazi) i za svaki (metoda, uzorak):
 * <ol>
 *     <li>tačno jedna klasa iz {@link PristupMatrica} (ANONIMNO/STUDENT/MCP se ne preklapaju; ostalo ADMIN);</li>
 *     <li>isti red u snimku {@code src/test/resources/pristup-matrica.txt}: nov endpoint, obrisan endpoint ili druga
 *     klasa obaraju test (nov endpoint traži svesno svrstavanje);</li>
 *     <li>nijedan uzorak pod {@code /public/**} nije ADMIN;</li>
 *     <li>stvaran zahtev kroz pravi lanac (MockMvc sa filterima) bez kolačića: ADMIN i STUDENT dobijaju 401 i ne stižu do
 *     kontrolera; ANONIMNO stiže do kontrolera (lanac ga nije odbio; šta kontroler odgovori je njegova logika, npr.
 *     {@code /auth/ja} sam vraća 401); MCP 401 ili 404. Ovo dokazuje da se {@link PristupMatrica#klasa} i matcheri lanca
 *     slažu, jer su to dva odvojena puta kroz kod.</li>
 * </ol>
 * I zaobilaženja (kodirane tačke, {@code ;}, duple kose crte, velika slova, završna kosa crta) bez kolačića nikad nisu 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PristupMatricaTest {

    static final String SNIMAK = "pristup-matrica.txt";
    static final Path PREDLOG = Path.of("target", "pristup-matrica.predlog.txt");
    /** Mapiranje bez metode važi za svaku; snimak ga beleži kao ovih pet redova. */
    static final List<HttpMethod> SVE_METODE = List.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    static final Comparator<Endpoint> REDOSLED =
            Comparator.comparing(Endpoint::uzorak).thenComparing(e -> e.metod().name());

    static final String ZAGLAVLJE = """
            # Snimak matrice pristupa (spec 3 i 14.1). Proverava ga PristupMatricaTest.
            # Red: METOD UZORAK KLASA, gde je KLASA jedno od ANONIMNO, STUDENT, MCP, ADMIN; sortirano po uzorku, pa po metodi.
            # Uzorak je backend putanja kako je mapirana u kodu (nginx dodaje /api). Mapiranje bez metode daje po red za
            # GET, POST, PUT, PATCH i DELETE; WebSocket ulazi su GET.
            #
            # Nov endpoint: test ispiše tačan red za dodavanje (klasa iz PristupMatrica). Pre nego što ga dodaš, proveri
            # klasu prema spec-u, sekcija 3: sve što nije izričito ANONIMNO, STUDENT ili MCP je ADMIN, i ništa nastavničko
            # ne sme pod /public/**. Ako klasa treba da bude drugačija, menja se PristupMatrica (i spec), ne samo ovaj fajl.
            # Obrisan endpoint: ukloni njegov red. Posle pada testa ceo predlog fajla je u target/pristup-matrica.predlog.txt.
            """;

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext ctx;

    /** Jedan endpoint: metoda, uzorak, {@code Content-Type} za probni zahtev ({@code null} = bez tela) i odakle je. */
    record Endpoint(HttpMethod metod, String uzorak, MediaType tip, String izvor) {
        String kljuc() {
            return metod.name() + " " + uzorak;
        }

        String red(KlasaPristupa k) {
            return kljuc() + " " + k;
        }

        KlasaPristupa klasa() {
            return PristupMatrica.klasa(metod, uzorak);
        }
    }

    // ---------------------------------------------------------------- skupljanje endpointa

    /** Svi endpointi iz koda, bez duplikata (isti metod i uzorak iz dva handlera, npr. {@code /error}), sortirani. */
    List<Endpoint> endpointi() {
        List<String> greske = new ArrayList<>();
        Map<String, Endpoint> sve = new LinkedHashMap<>();
        for (RequestMappingInfoHandlerMapping hm : ctx.getBeansOfType(RequestMappingInfoHandlerMapping.class).values()) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> e : hm.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = e.getKey();
                Set<HttpMethod> metode = new LinkedHashSet<>();
                info.getMethodsCondition().getMethods().forEach(m -> metode.add(m.asHttpMethod()));
                if (metode.isEmpty()) metode.addAll(SVE_METODE);
                MediaType tip = info.getConsumesCondition().getConsumableMediaTypes().stream().findFirst()
                        .orElse(MediaType.APPLICATION_JSON);
                HandlerMethod h = e.getValue();
                String izvor = h.getBeanType().getSimpleName() + "#" + h.getMethod().getName();
                for (String uzorak : info.getPatternValues()) {
                    for (HttpMethod m : metode) {
                        dodaj(sve, new Endpoint(m, uzorak, tip, izvor));
                    }
                }
            }
        }
        for (RouterFunctionMapping rm : ctx.getBeansOfType(RouterFunctionMapping.class).values()) {
            if (rm.getRouterFunction() == null) continue;
            for (Endpoint e : izRouterFunkcije(rm.getRouterFunction(), greske)) dodaj(sve, e);
        }
        for (WebSocketHandlerMapping wm : ctx.getBeansOfType(WebSocketHandlerMapping.class).values()) {
            for (String uzorak : wm.getHandlerMap().keySet()) {
                dodaj(sve, new Endpoint(HttpMethod.GET, uzorak, null, "WebSocket " + uzorak));
            }
        }
        assertTrue(greske.isEmpty(), String.join("\n", greske));
        return sve.values().stream().sorted(REDOSLED).toList();
    }

    private static void dodaj(Map<String, Endpoint> sve, Endpoint e) {
        sve.putIfAbsent(e.kljuc(), e);
    }

    /**
     * Rute iz {@code RouterFunction}-a (MCP A1 ih donosi). Ugnežđene putanje se spajaju; ruta bez metode važi za
     * {@link #SVE_METODE}. Oblik koji posetilac ne razume (sopstveni predikat, negacija, resursi) je greška: test ne
     * sme da pretpostavi putanju, ruta se svrstava svesno.
     */
    static List<Endpoint> izRouterFunkcije(RouterFunction<?> rf, List<String> greske) {
        List<Endpoint> rute = new ArrayList<>();
        Deque<List<String>> prefiksi = new ArrayDeque<>();
        prefiksi.push(List.of(""));
        rf.accept(new RouterFunctions.Visitor() {
            @Override
            public void startNested(RequestPredicate predikat) {
                Predikat p = Predikat.iz(predikat, greske);
                List<String> novi = new ArrayList<>();
                for (String pre : prefiksi.peek()) {
                    if (p.putanje.isEmpty()) novi.add(pre);
                    else for (String put : p.putanje) novi.add(pre + put);
                }
                prefiksi.push(novi);
            }

            @Override
            public void endNested(RequestPredicate predikat) {
                prefiksi.pop();
            }

            @Override
            public void route(RequestPredicate predikat, HandlerFunction<?> handler) {
                Predikat p = Predikat.iz(predikat, greske);
                Set<HttpMethod> metode = p.metode.isEmpty() ? new LinkedHashSet<>(SVE_METODE) : p.metode;
                for (String pre : prefiksi.peek()) {
                    List<String> putanje = p.putanje.isEmpty() ? List.of(pre) : p.putanje.stream().map(x -> pre + x).toList();
                    for (String put : putanje) {
                        if (put.isEmpty()) {
                            greske.add("RouterFunction ruta bez putanje (" + predikat + "): test ne zna njen uzorak");
                            continue;
                        }
                        for (HttpMethod m : metode) {
                            rute.add(new Endpoint(m, put, MediaType.APPLICATION_JSON, "RouterFunction " + predikat));
                        }
                    }
                }
            }

            @Override
            public void resources(Function<ServerRequest, Optional<org.springframework.core.io.Resource>> f) {
                greske.add("RouterFunction resursi (" + f + "): svrstaj ih ručno, test ne zna njihove putanje");
            }

            @Override
            public void attributes(Map<String, Object> atributi) {
                // ne utiču na putanju
            }

            @Override
            public void unknown(RouterFunction<?> nepoznata) {
                greske.add("Nepoznata RouterFunction (" + nepoznata + "): test ne zna njene putanje");
            }
        });
        return rute;
    }

    /** Metode i putanje jednog predikata ({@code or} = unija; zaglavlja, parametri i verzija samo sužavaju). */
    static final class Predikat implements RequestPredicates.Visitor {
        final Set<HttpMethod> metode = new LinkedHashSet<>();
        final List<String> putanje = new ArrayList<>();
        final List<String> greske;
        final RequestPredicate izvor;

        private Predikat(RequestPredicate izvor, List<String> greske) {
            this.izvor = izvor;
            this.greske = greske;
        }

        static Predikat iz(RequestPredicate p, List<String> greske) {
            Predikat v = new Predikat(p, greske);
            p.accept(v);
            return v;
        }

        @Override public void method(Set<HttpMethod> m) { metode.addAll(m); }
        @Override public void path(String uzorak) { putanje.add(uzorak); }
        @Override public void pathExtension(String ekstenzija) { nepoznato("pathExtension"); }
        @Override public void header(String ime, String vrednost) {}
        @Override public void param(String ime, String vrednost) {}
        @Override public void version(String verzija) {}
        @Override public void startAnd() {}
        @Override public void and() {}
        @Override public void endAnd() {}
        @Override public void startOr() {}
        @Override public void or() {}
        @Override public void endOr() {}
        @Override public void startNegate() { nepoznato("negate"); }
        @Override public void endNegate() {}
        @Override public void unknown(RequestPredicate p) { nepoznato("nepoznat predikat " + p); }

        private void nepoznato(String sta) {
            greske.add("RouterFunction predikat (" + izvor + ") sadrži " + sta + ": test ne zna putanju, svrstaj ručno");
        }
    }

    // ---------------------------------------------------------------- snimak i klase

    @Test
    void endpointiSeSlazuSaSnimkomIImajuTacnoJednuKlasu() throws IOException {
        List<Endpoint> kod = endpointi();
        List<String> greske = new ArrayList<>();

        for (Endpoint e : kod) {
            Set<KlasaPristupa> pogodjene = PristupMatrica.pogodjene(e.metod(), e.uzorak());
            if (pogodjene.size() > 1) {
                greske.add("Preklapanje u PristupMatrica: " + e.kljuc() + " pogađa " + pogodjene
                        + "; svaki endpoint sme imati tačno jednu klasu (spec 14.1)");
            }
        }

        Map<String, KlasaPristupa> izKoda = new TreeMap<>();
        for (Endpoint e : kod) izKoda.put(e.kljuc(), e.klasa());
        String predlog = ZAGLAVLJE + "\n" + String.join("\n", kod.stream().map(e -> e.red(e.klasa())).toList()) + "\n";

        Optional<List<String>> procitan = procitajSnimak();
        if (procitan.isEmpty()) {
            sacuvajPredlog(predlog);
            fail("Nema snimka src/test/resources/" + SNIMAK + ". Predlog (svaki red pregledaj prema spec-u, sekcija 3, "
                    + "pre commit-a; isti tekst je u " + PREDLOG + "):\n" + predlog);
        }

        List<String> redovi = procitan.get();
        Map<String, KlasaPristupa> izSnimka = new LinkedHashMap<>();
        List<String> kljuceviRedom = new ArrayList<>();
        for (String red : redovi) {
            String[] d = red.trim().split("\\s+");
            if (d.length != 3) {
                greske.add("Neispravan red u " + SNIMAK + ": \"" + red + "\" (oblik je METOD UZORAK KLASA)");
                continue;
            }
            KlasaPristupa k;
            try {
                HttpMethod.valueOf(d[0]);
                k = KlasaPristupa.valueOf(d[2]);
            } catch (IllegalArgumentException ex) {
                greske.add("Neispravan red u " + SNIMAK + ": \"" + red + "\" (nepoznata klasa; klase su "
                        + List.of(KlasaPristupa.values()) + ")");
                continue;
            }
            String kljuc = d[0] + " " + d[1];
            if (izSnimka.put(kljuc, k) != null) {
                greske.add("Dupli red u " + SNIMAK + ": ukloni jedan od redova za \"" + kljuc + "\"");
            }
            kljuceviRedom.add(kljuc);
        }

        for (Map.Entry<String, KlasaPristupa> e : izKoda.entrySet()) {
            KlasaPristupa uSnimku = izSnimka.get(e.getKey());
            if (uSnimku == null) {
                greske.add("Nov endpoint bez klase u snimku: dodaj red \"" + e.getKey() + " " + e.getValue()
                        + "\" u " + SNIMAK + " (spec 3; klasa je iz PristupMatrica, proveri je pre dodavanja)");
            } else if (uSnimku != e.getValue()) {
                greske.add("Druga klasa za " + e.getKey() + ": snimak kaže " + uSnimku + ", PristupMatrica daje "
                        + e.getValue() + ". Ispravi PristupMatrica ili, svesno i prema spec-u 3, red u " + SNIMAK
                        + " u \"" + e.getKey() + " " + e.getValue() + "\"");
            }
        }
        for (Map.Entry<String, KlasaPristupa> e : izSnimka.entrySet()) {
            if (!izKoda.containsKey(e.getKey())) {
                greske.add("Red iz snimka nema endpoint u kodu (obrisan ili preimenovan): ukloni red \""
                        + e.getKey() + " " + e.getValue() + "\" iz " + SNIMAK);
            }
        }
        if (greske.isEmpty()) {
            List<String> sortirano = kod.stream().map(Endpoint::kljuc).toList();
            if (!sortirano.equals(kljuceviRedom)) {
                greske.add("Redovi u " + SNIMAK + " nisu sortirani (po uzorku, pa po metodi); sortiran fajl je u "
                        + PREDLOG);
            }
        }
        if (!greske.isEmpty()) {
            sacuvajPredlog(predlog);
            fail("Matrica pristupa se ne slaže sa snimkom (" + greske.size() + "):\n  " + String.join("\n  ", greske)
                    + "\nCeo predlog fajla: " + PREDLOG);
        }
    }

    @Test
    void nijedanUzorakPodPublicNijeAdmin() throws IOException {
        List<String> greske = new ArrayList<>();
        for (Endpoint e : endpointi()) {
            if (podPublic(e.uzorak()) && e.klasa() == KlasaPristupa.ADMIN) {
                greske.add(e.kljuc() + " (" + e.izvor() + ") je pod /public/** a klasa mu je ADMIN: sve pod /public/** je "
                        + "javno sa interneta, nastavnički endpoint mapiraj van /public, a studentski dodaj u "
                        + "PristupMatrica (spec 3)");
            }
        }
        // i snimak, ako neko ručno upiše ADMIN red pod /public
        for (String red : procitajSnimak().orElse(List.of())) {
            String[] d = red.trim().split("\\s+");
            if (d.length == 3 && podPublic(d[1]) && "ADMIN".equals(d[2])) {
                greske.add("Red \"" + red + "\" u " + SNIMAK + " je pod /public/** a ADMIN (spec 3)");
            }
        }
        assertTrue(greske.isEmpty(), String.join("\n", greske));
    }

    private static boolean podPublic(String uzorak) {
        return uzorak.equals("/public") || uzorak.startsWith("/public/");
    }

    @Test
    void skupljaSveIzvoreEndpointa() {
        List<String> kljucevi = endpointi().stream().map(Endpoint::kljuc).toList();
        // WebSocket ulazi iz WebSocketHandlerMapping-a, ne iz @RequestMapping-a
        assertTrue(kljucevi.contains("GET /ws"), "WebSocket /ws nije skupljen: " + kljucevi);
        assertTrue(kljucevi.contains("GET /public/ws"), "WebSocket /public/ws nije skupljen: " + kljucevi);
        // mapiranje bez metode (BasicErrorController) daje svih pet metoda
        for (HttpMethod m : SVE_METODE) {
            assertTrue(kljucevi.contains(m.name() + " /error"), "mapiranje bez metode: nema " + m + " /error");
        }
    }

    @Test
    void routerFunkcijeSeSkupljajuIMcpJeMcp() {
        HandlerFunction<ServerResponse> ok = req -> ServerResponse.ok().build();
        RouterFunction<ServerResponse> rf = RouterFunctions.route()
                .GET("/mcp", ok)
                .POST("/mcp", ok)
                .nest(RequestPredicates.path("/primer"), b -> b
                        .DELETE("/{id}", ok)
                        .route(RequestPredicates.path("/sve"), ok))
                .build();
        List<String> greske = new ArrayList<>();
        List<String> kljucevi = izRouterFunkcije(rf, greske).stream().map(Endpoint::kljuc).sorted().toList();
        assertEquals(List.of(), greske);
        assertEquals(List.of("DELETE /primer/sve", "DELETE /primer/{id}", "GET /mcp", "GET /primer/sve",
                "PATCH /primer/sve", "POST /mcp", "POST /primer/sve", "PUT /primer/sve"), kljucevi);
        assertEquals(KlasaPristupa.MCP, PristupMatrica.klasa(HttpMethod.POST, "/mcp"));
        assertEquals(KlasaPristupa.MCP, PristupMatrica.klasa(HttpMethod.GET, "/mcp"));

        // oblik koji posetilac ne razume nije tiho preskočen
        List<String> g2 = new ArrayList<>();
        izRouterFunkcije(RouterFunctions.route(req -> true, ok), g2);
        assertFalse(g2.isEmpty(), "nepoznat predikat mora biti greška");
    }

    // ---------------------------------------------------------------- stvarni zahtevi kroz lanac

    @Test
    void stvaranZahtevBezKolacicaPratiKlasu() throws Exception {
        List<String> greske = new ArrayList<>();
        int anonimnih = 0;
        for (Endpoint e : endpointi()) {
            KlasaPristupa k = e.klasa();
            MvcResult r = posalji(e, greske, null);
            if (r == null) continue;
            int st = r.getResponse().getStatus();
            Object handler = r.getHandler();
            switch (k) {
                case ADMIN, STUDENT -> {
                    String telo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    if (st != 401 || !telo.contains("\"Potrebna je prijava.\"") || handler != null) {
                        greske.add(e.red(k) + ": bez kolačića očekivano 401 \"Potrebna je prijava.\" iz lanca, a dobijeno "
                                + st + (handler != null ? " od kontrolera " + handler : "") + " " + telo);
                    }
                }
                case ANONIMNO -> {
                    anonimnih++;
                    if (handler == null) {
                        greske.add(e.red(k) + ": lanac ga je odbio (" + st + " "
                                + r.getResponse().getContentAsString(StandardCharsets.UTF_8)
                                + "); ANONIMNO mora stići do kontrolera. PristupMatrica.klasa i matcheri lanca se ne slažu");
                    }
                }
                case MCP -> {
                    if (st != 401 && st != 404) {
                        greske.add(e.red(k) + ": bez ključa očekivano 401 ili 404, dobijeno " + st);
                    }
                }
            }
        }
        assertTrue(anonimnih > 0, "nijedan ANONIMNO endpoint: test ne proverava ništa za javne putanje");
        assertTrue(greske.isEmpty(), "Stvarni zahtevi se ne slažu sa klasom (" + greske.size() + "):\n  "
                + String.join("\n  ", greske));
    }

    /**
     * Sa studentskim kolačićem: ADMIN je 403 i ne stiže do kontrolera; STUDENT i ANONIMNO stižu. Studentski kolačić stiže u
     * B7: tada {@link #studentskiKolacic()} pravi uređaj studenta i vraća {@code gfs_student}.
     */
    @Test
    void saStudentskimKolacicemAdminJe403() throws Exception {
        Optional<Cookie> student = studentskiKolacic();
        assumeTrue(student.isPresent(), "studentski kolačić stiže u B7");
        List<String> greske = new ArrayList<>();
        for (Endpoint e : endpointi()) {
            KlasaPristupa k = e.klasa();
            MvcResult r = posalji(e, greske, student.get());
            if (r == null) continue;
            int st = r.getResponse().getStatus();
            if (k == KlasaPristupa.ADMIN && (st != 403 || r.getHandler() != null)) {
                greske.add(e.red(k) + ": sa studentskim kolačićem očekivano 403 iz lanca, dobijeno " + st
                        + (r.getHandler() != null ? " od kontrolera " + r.getHandler() : ""));
            }
            if ((k == KlasaPristupa.STUDENT || k == KlasaPristupa.ANONIMNO) && r.getHandler() == null) {
                greske.add(e.red(k) + ": sa studentskim kolačićem lanac ga je odbio (" + st + ")");
            }
        }
        assertTrue(greske.isEmpty(), String.join("\n", greske));
    }

    /** Do B7 nema studentske sesije; B7 ovde pravi uređaj studenta i vraća {@code gfs_student=<token>}. */
    Optional<Cookie> studentskiKolacic() {
        return Optional.empty();
    }

    /** Zahtev po endpointu: parametri putanje {@code 1}, telo {@code {}} (JSON, ili tip iz {@code consumes}), same-origin. */
    private MvcResult posalji(Endpoint e, List<String> greske, Cookie kolacic) {
        String putanja = e.uzorak().replaceAll("\\{[^}]*}", "1").replace("**", "1").replace("*", "1");
        MockHttpServletRequestBuilder b = request(e.metod(), putanja).header("Sec-Fetch-Site", "same-origin");
        if (e.tip() != null && e.metod() != HttpMethod.GET) {
            b.contentType(e.tip()).content("{}");
        }
        if (kolacic != null) b.cookie(kolacic);
        try {
            return mvc.perform(b).andReturn();
        } catch (Exception ex) {
            greske.add(e.kljuc() + " (" + putanja + "): zahtev je bacio izuzetak " + ex);
            return null;
        }
    }

    @Test
    void zaobilazenjaBezKolacicaNikadNisu200() throws Exception {
        List<String> greske = new ArrayList<>();
        for (String sirov : new String[]{"/public/../studenti", "/public/upis/x/..;/studenti",
                "/%70ublic/x/../../studenti", "//studenti", "/STUDENTI", "/studenti;x=1", "/studenti/"}) {
            for (HttpMethod m : List.of(HttpMethod.GET, HttpMethod.POST)) {
                MvcResult r = mvc.perform(request(m, "/").with(sirovUri(sirov))
                                .header("Sec-Fetch-Site", "same-origin")
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn();
                int st = r.getResponse().getStatus();
                if ((st >= 200 && st < 300) || r.getHandler() != null) {
                    greske.add(m + " " + sirov + ": bez prijave dobijeno " + st
                            + (r.getHandler() != null ? " od kontrolera " + r.getHandler() : "")
                            + "; zaobilaženje mora odbiti firewall (400) ili lanac (401)");
                }
            }
        }
        assertTrue(greske.isEmpty(), String.join("\n", greske));
    }

    /** Sirov URI (MockMvc inače parsira {@code //x} kao host, a {@code %} kodira još jednom). */
    static RequestPostProcessor sirovUri(String sirov) {
        return req -> {
            req.setRequestURI(sirov);
            req.setServletPath("");
            req.setPathInfo(null);
            return req;
        };
    }

    // ---------------------------------------------------------------- snimak sa diska

    /** Redovi snimka bez komentara ({@code #}) i praznih redova; prazno kad fajla nema. */
    static Optional<List<String>> procitajSnimak() throws IOException {
        try (InputStream in = PristupMatricaTest.class.getClassLoader().getResourceAsStream(SNIMAK)) {
            if (in == null) return Optional.empty();
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .toList());
        }
    }

    private static void sacuvajPredlog(String predlog) throws IOException {
        Files.createDirectories(PREDLOG.getParent());
        Files.writeString(PREDLOG, predlog, StandardCharsets.UTF_8);
    }
}
