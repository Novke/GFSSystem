package tri.novica.gfssystem.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * MCP kostur (spec MCP 2 i 3.1): filter ključa nad putanjom endpointa, UTF-8 uputstva servera, omotač alata, stateless
 * server i startne provere. Transport se ne podešava: {@code WebMvcStatelessServerTransport} iz Spring AI
 * auto-konfiguracije, sa podrazumevanim {@code ServerTransportSecurityValidator.NOOP} (Claude Code ne šalje {@code Origin});
 * menja se samo JSON mapper koji koristi, kroz Spring AI tačku zamene ({@link StatelessServer#mcpServerJsonMapper()}).
 */
@Configuration
public class McpKonfig {

    static final String ENDPOINT = "spring.ai.mcp.server.streamable-http.mcp-endpoint";
    static final String UPUTSTVA = "mcp/uputstva.md";
    static final int MAX_UPUTSTVA = 4_000;

    @Bean
    McpKljucevi mcpKljucevi(@Value("${gfs.mcp.kljucevi:}") String kljucevi) {
        return McpKljucevi.parsiraj(kljucevi);
    }

    /** Obrasci iz istog svojstva kao endpoint; {@code /mcp/*} pokriva i {@code /mcp/prilozi} i {@code /mcp/slike}. */
    @Bean
    FilterRegistrationBean<McpKljucFilter> mcpKljucFilter(McpKljucevi kljucevi, JsonMapper json, Clock clock,
                                                          @Value("${" + ENDPOINT + ":/mcp}") String endpoint) {
        FilterRegistrationBean<McpKljucFilter> registracija =
                new FilterRegistrationBean<>(new McpKljucFilter(kljucevi, json, clock));
        registracija.addUrlPatterns(endpoint, endpoint + "/*");
        registracija.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registracija.setName("mcpKljucFilter");
        return registracija;
    }

    /**
     * Uputstva servera ({@code instructions} u odgovoru na {@code initialize}) iz {@code classpath:mcp/uputstva.md},
     * čitana kao UTF-8: Boot čita {@code .properties} kao ISO-8859-1, pa bi dijakritici iz svojstva stigli pokvareni.
     * Posle inicijalizacije, da vezivanje {@code spring.ai.mcp.server.*} ne pregazi tekst.
     */
    @Bean
    static BeanPostProcessor mcpUputstva() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String ime) {
                if (bean instanceof McpServerProperties svojstva) {
                    svojstva.setInstructions(ucitajUputstva(new ClassPathResource(UPUTSTVA)));
                }
                return bean;
            }
        };
    }

    /**
     * Omotač alata ({@link McpOmotac}): za svaki bean sa {@code @McpTool} metodama beleži objektne argumente po alatu, a
     * listu stateless specifikacija alata (Spring AI {@code toolSpecs}) zamenjuje omotanom. Statički i bez
     * {@code ApplicationContext}-a: vidi bean-ove kako nastaju, kao i Spring AI skener.
     */
    @Bean
    static BeanPostProcessor mcpOmotac() {
        Map<String, Map<String, JavaType>> zapisi = new ConcurrentHashMap<>();
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings("unchecked")
            public Object postProcessAfterInitialization(Object bean, String ime) {
                McpOmotac.zabelezi(bean, zapisi);
                return McpOmotac.jeSpisakAlata(bean)
                        ? McpOmotac.omotaj((List<SyncToolSpecification>) bean, zapisi) : bean;
            }
        };
    }

    /**
     * Stateless server umesto Spring AI {@code McpServerStatelessAutoConfiguration} (isključena u
     * {@code GfsSystemApplication}; isto ime bean-a {@code mcpStatelessSyncServer}, pa bi bez isključenja start pao), sa
     * JSON mapper-om transporta koji čuva {@code null}. Razlog za server: MCP SDK 2.0.0 podrazumevano
     * proverava argumente prema {@code inputSchema} pre handler-a ({@code McpStatelessAsyncServer} kroz
     * {@code ToolInputValidator}) i vraća tehničku englesku poruku ({@code Tool (x) input validation failed: ...}) mimo
     * {@link McpOmotac}-a, a Spring AI 2.0.1 tu proveru ne izlaže. Ovde je {@code validateToolInputs(false)}: argumente
     * proveravaju omotač, {@link McpGreske} i {@link McpPatch}, sa porukama na srpskom. Ostalo je kao u Spring AI-ju:
     * samo alati (resursi, promptovi i dopune su isključeni svojstvima), instrukcije, rok i
     * {@code immediateExecution} (alat radi na niti zahteva, pa vidi atribute zahteva).
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional({McpServerStdioDisabledCondition.class,
            McpServerStatelessAutoConfiguration.EnabledStatelessServerCondition.class})
    @EnableConfigurationProperties(McpServerProperties.class)
    static class StatelessServer {

        /**
         * JSON mapper MCP transporta, umesto Spring AI {@code McpServerJsonMapperAutoConfiguration} (njegova tačka
         * zamene: bean istog imena). Isti je kao Spring AI-jev, osim što ne izbacuje {@code null} vrednosti iz mapa:
         * transport poruku prevodi kroz mapu ({@code McpSchema.deserializeJsonRpcMessage}), a Spring AI-jev
         * {@code NON_NULL} za sadržaj bi iz argumenata alata izbacio {@code "opis": null}, pa merge patch ne bi mogao da
         * obriše polje niti da odbije {@code null} za polje koje se ne briše. Svojstva record-a bez vrednosti se i dalje
         * ne pišu.
         */
        @Bean(name = "mcpServerJsonMapper", defaultCandidate = false)
        JsonMapper mcpServerJsonMapper() {
            return JsonMapper.builder()
                    .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .addModules(JacksonUtils.instantiateAvailableModules())
                    .changeDefaultPropertyInclusion(
                            v -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
                    .build();
        }

        @Bean
        McpStatelessSyncServer mcpStatelessSyncServer(McpStatelessServerTransport transport,
                                                      McpServerProperties svojstva,
                                                      ObjectProvider<List<SyncToolSpecification>> alati) {
            List<SyncToolSpecification> sviAlati = alati.stream().flatMap(List::stream).toList();
            McpServer.StatelessSyncSpecification server = McpServer.sync(transport)
                    .serverInfo(McpSchema.Implementation.builder(svojstva.getName(), svojstva.getVersion()).build())
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                    .instructions(svojstva.getInstructions())
                    .requestTimeout(svojstva.getRequestTimeout())
                    .immediateExecution(true)
                    .validateToolInputs(false);
            if (!sviAlati.isEmpty()) {
                server.tools(sviAlati);
            }
            return server.build();
        }
    }

    /**
     * Start pada kad je MCP uključen a protokol nije STATELESS, ili kad neka {@link RouterFunction} ruta nije pod putanjom
     * koju filter štiti (npr. buduća ruta van {@code /mcp} bi bila bez ključa).
     */
    @Bean
    SmartInitializingSingleton mcpProvera(Environment env, ObjectProvider<RouterFunction<?>> rute) {
        return () -> {
            List<String> putanje = new ArrayList<>();
            rute.orderedStream().forEach(ruta -> putanje.addAll(putanje(ruta)));
            proveri(env.getProperty(McpUkljucenje.SVOJSTVO, Boolean.class, true),
                    env.getProperty("spring.ai.mcp.server.protocol"),
                    putanje,
                    env.getProperty(ENDPOINT, "/mcp"));
        };
    }

    static void proveri(boolean ukljucen, String protokol, List<String> putanje, String endpoint) {
        if (!ukljucen) {
            return;   // isključen MCP ne pravi rute, a filter vraća 404
        }
        if (!"STATELESS".equalsIgnoreCase(protokol)) {
            throw new IllegalStateException("MCP mora raditi sa spring.ai.mcp.server.protocol=STATELESS (sada: "
                    + protokol + ").");
        }
        for (String putanja : putanje) {
            if (!putanja.equals(endpoint) && !putanja.startsWith(endpoint + "/")) {
                throw new IllegalStateException("RouterFunction ruta " + putanja + " nije pod " + endpoint
                        + " (filter MCP ključa je ne štiti).");
            }
        }
    }

    /**
     * Obrasci putanja svih ruta jedne {@link RouterFunction} (ugnežđene dobijaju prefiks). Fail-closed: ruta bez
     * putanje daje {@code /**}, putanja pod negacijom {@code !<putanja>}, a resursi i nepoznati delovi {@code ?}, pa
     * {@link #proveri} takvu rutu odbija.
     */
    static List<String> putanje(RouterFunction<?> ruta) {
        List<String> rezultat = new ArrayList<>();
        Deque<String> prefiksi = new ArrayDeque<>();
        ruta.accept(new RouterFunctions.Visitor() {
            @Override
            public void startNested(RequestPredicate predikat) {
                List<String> p = putanjePredikata(predikat);
                // ugnežđeni predikat sa jednom putanjom je prefiks; bilo šta drugo ne ograničava putanju pouzdano
                prefiksi.push(p.size() == 1 && !p.getFirst().startsWith("!") && !p.getFirst().equals("/**")
                        ? trenutni() + p.getFirst() : "?");
            }

            @Override
            public void endNested(RequestPredicate predikat) {
                prefiksi.pop();
            }

            @Override
            public void route(RequestPredicate predikat, HandlerFunction<?> handler) {
                String prefiks = trenutni();
                for (String p : putanjePredikata(predikat)) {
                    rezultat.add(prefiks.startsWith("?") || p.startsWith("!") ? "?" + prefiks + p : prefiks + p);
                }
            }

            @Override
            public void resources(Function<ServerRequest, Optional<Resource>> pretraga) {
                rezultat.add("?resursi");
            }

            @Override
            public void attributes(Map<String, Object> atributi) {
            }

            @Override
            public void unknown(RouterFunction<?> nepoznata) {
                rezultat.add("?nepoznata");
            }

            private String trenutni() {
                return prefiksi.isEmpty() ? "" : prefiksi.peek();
            }
        });
        return rezultat;
    }

    /** Putanje jednog predikata: {@code path} pod negacijom dobija {@code !}, predikat bez putanje daje {@code /**}. */
    private static List<String> putanjePredikata(RequestPredicate predikat) {
        List<String> putanje = new ArrayList<>();
        int[] negacija = {0};
        predikat.accept(new RequestPredicates.Visitor() {
            @Override public void method(Set<HttpMethod> metode) {}
            @Override public void path(String obrazac) { putanje.add(negacija[0] > 0 ? "!" + obrazac : obrazac); }
            @Override public void pathExtension(String ekstenzija) {}
            @Override public void header(String ime, String vrednost) {}
            @Override public void param(String ime, String vrednost) {}
            @Override public void version(String verzija) {}
            @Override public void startAnd() {}
            @Override public void and() {}
            @Override public void endAnd() {}
            @Override public void startOr() {}
            @Override public void or() {}
            @Override public void endOr() {}
            @Override public void startNegate() { negacija[0]++; }
            @Override public void endNegate() { negacija[0]--; }
            @Override public void unknown(RequestPredicate nepoznat) { putanje.add("!nepoznat"); }
        });
        return putanje.isEmpty() ? List.of("/**") : putanje;
    }

    /** Tekst uputstava kao UTF-8 (neispravan UTF-8 ruši start, ne kvari se tiho), najviše {@link #MAX_UPUTSTVA} znakova. */
    static String ucitajUputstva(Resource resurs) {
        String tekst;
        try (InputStream ulaz = resurs.getInputStream()) {
            tekst = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(ulaz.readAllBytes())).toString().strip();
        } catch (IOException e) {
            throw new IllegalStateException("Uputstva MCP servera (" + UPUTSTVA + ") nisu čitljiva kao UTF-8.", e);
        }
        if (tekst.length() > MAX_UPUTSTVA) {
            throw new IllegalStateException("Uputstva MCP servera imaju " + tekst.length() + " znakova, najviše "
                    + MAX_UPUTSTVA + ".");
        }
        return tekst;
    }
}
