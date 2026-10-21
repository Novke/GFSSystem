package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JavaType;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.Serija;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Omotač svakog stateless MCP alata (spec MCP 2, "Greške"; omotava ga {@code McpKonfig.mcpOmotac()}), pre vezivanja
 * argumenata u Spring AI-ju, čiji ishod ne zavisi od nas (sopstveni {@code JsonHelper}). Redom, bez poziva alata dok
 * neka provera pada:
 * <ol>
 *   <li>ključ argumenta van {@code inputSchema.properties} je {@code Nepoznato polje 'x'.};</li>
 *   <li>svaki prisutan objektni argument (record, mapa, lista objekata) se čita strogim mapperom {@link McpPatch} u tip
 *       parametra: nepoznat ključ {@code Nepoznato polje 'obj.stavke[0].y'.}, pogrešan tip ili {@code null} za primitiv
 *       {@link McpGreske#NEISPRAVAN_FORMAT}. Alat (i Spring AI vezivanje) dobija ga normalizovanog: sva svojstva tipa,
 *       izostavljen primitiv kao podrazumevana vrednost (Spring AI bi ga odbio); prisustvo i {@code null} ključeva na
 *       vrhu ostaju kako su stigli;</li>
 *   <li>izuzetak van alata je {@link McpGreske#SISTEMSKA};</li>
 *   <li>{@code isError} pre ulaska u metodu alata (Spring AI nije mogao da veže prost argument, npr. {@code "abc"} za
 *       {@code Long}; njegov tekst nosi koren izuzetka) je {@link McpGreske#NEISPRAVAN_FORMAT}.</li>
 * </ol>
 * Odbijanje ostavlja INFO red sa {@code ishod=neispravni-argumenti}. Radi nad stateless tipom
 * {@link SyncToolSpecification}: stateful specifikacije se u režimu STATELESS ne prave.
 */
@Slf4j
public final class McpOmotac {

    static final String NEISPRAVNI_ARGUMENTI = "neispravni-argumenti";

    private McpOmotac() {
    }

    /**
     * Beleži alate jednog bean-a (isto otkrivanje kao Spring AI: deklarisane metode klase bean-a sa {@link McpTool}):
     * ime alata -> (ime parametra -> Jackson tip) za svaki parametar koji nije prost i nije infrastruktura
     * ({@link CallToolRequest}, {@link McpTransportContext}). Alat bez objektnih argumenata dobija prazan zapis; isto ime
     * dva puta ruši start.
     */
    static void zabelezi(Object bean, Map<String, Map<String, JavaType>> zapisi) {
        for (Method metoda : bean.getClass().getDeclaredMethods()) {
            McpTool alat = metoda.getAnnotation(McpTool.class);
            if (alat == null) {
                continue;
            }
            String ime = StringUtils.hasText(alat.name()) ? alat.name() : metoda.getName();
            Map<String, JavaType> objektni = new LinkedHashMap<>();
            for (Parameter p : metoda.getParameters()) {
                if (!infrastruktura(p.getType()) && !prost(p.getParameterizedType())) {
                    objektni.put(p.getName(), McpPatch.tip(p.getParameterizedType()));
                }
            }
            if (zapisi.putIfAbsent(ime, Map.copyOf(objektni)) != null) {
                throw new IllegalStateException("MCP alat " + ime + " je definisan više puta.");
            }
        }
    }

    /** Bean je spisak stateless alata (Spring AI {@code toolSpecs}) kad je neprazna lista samo takvih specifikacija. */
    static boolean jeSpisakAlata(Object bean) {
        return bean instanceof List<?> lista && !lista.isEmpty()
                && lista.stream().allMatch(SyncToolSpecification.class::isInstance);
    }

    /**
     * Omotava svaki alat; start pada kad alat nema zapis (nijedan viđen bean nema {@link McpTool} tog imena) ili kad
     * zabeleženi parametar nije u {@code inputSchema.properties}: omotavanje ne sme proći tiho bez efekta.
     */
    static List<SyncToolSpecification> omotaj(List<SyncToolSpecification> specifikacije,
                                              Map<String, Map<String, JavaType>> objektniArgumenti) {
        List<SyncToolSpecification> omotane = new ArrayList<>();
        for (SyncToolSpecification specifikacija : specifikacije) {
            if (specifikacija.callHandler() instanceof Omotan) {
                omotane.add(specifikacija);
                continue;
            }
            Tool alat = specifikacija.tool();
            Map<String, JavaType> objektni = objektniArgumenti.get(alat.name());
            if (objektni == null) {
                throw new IllegalStateException("MCP alat " + alat.name()
                        + " nema zapis argumenata (nije viđen bean sa @McpTool tog imena).");
            }
            Set<String> svojstva = svojstva(alat);
            for (String parametar : objektni.keySet()) {
                if (!svojstva.contains(parametar)) {
                    throw new IllegalStateException("MCP alat " + alat.name() + ": parametar " + parametar
                            + " nije u inputSchema.properties.");
                }
            }
            omotane.add(SyncToolSpecification.builder()
                    .tool(alat)
                    .callHandler(new Omotan(alat.name(), svojstva, objektni, specifikacija.callHandler()))
                    .build());
        }
        return List.copyOf(omotane);
    }

    private static Set<String> svojstva(Tool alat) {
        Object svojstva = alat.inputSchema().get("properties");
        if (svojstva instanceof Map<?, ?> mapa) {
            Set<String> imena = new LinkedHashSet<>();
            mapa.keySet().forEach(k -> imena.add(String.valueOf(k)));
            return Set.copyOf(imena);
        }
        return Set.of();
    }

    private static boolean infrastruktura(Class<?> tip) {
        return CallToolRequest.class.isAssignableFrom(tip) || McpTransportContext.class.isAssignableFrom(tip);
    }

    /** String, primitiv i omotač, enum, {@code java.time.*} i {@code List}/{@code Set} takvih. */
    static boolean prost(Type tip) {
        if (tip instanceof Class<?> klasa) {
            return klasa == String.class || ClassUtils.isPrimitiveOrWrapper(klasa) || klasa.isEnum()
                    || "java.time".equals(klasa.getPackageName());
        }
        if (tip instanceof ParameterizedType p && p.getRawType() instanceof Class<?> sirov
                && (sirov == List.class || sirov == Set.class)) {
            return prost(p.getActualTypeArguments()[0]);
        }
        return false;
    }

    /** Handler jednog alata; {@code instanceof} sprečava dvostruko omotavanje. */
    private record Omotan(String alat, Set<String> svojstva, Map<String, JavaType> objektni,
                          BiFunction<McpTransportContext, CallToolRequest, CallToolResult> original)
            implements BiFunction<McpTransportContext, CallToolRequest, CallToolResult> {

        @Override
        public CallToolResult apply(McpTransportContext kontekst, CallToolRequest zahtev) {
            long pocetak = System.nanoTime();
            try {
                Map<String, Object> argumenti = zahtev.arguments() == null ? Map.of() : zahtev.arguments();
                for (String kljuc : argumenti.keySet()) {
                    if (!svojstva.contains(kljuc)) {
                        return odbij(argumenti, pocetak, "Nepoznato polje '" + kljuc + "'.");
                    }
                }
                Map<String, Object> normalizovani = null;
                for (Map.Entry<String, JavaType> parametar : objektni.entrySet()) {
                    Object vrednost = argumenti.get(parametar.getKey());
                    if (vrednost != null) {
                        try {
                            Object procitano = McpPatch.procitaj(parametar.getKey(), vrednost, parametar.getValue());
                            if (normalizovani == null) {
                                normalizovani = new LinkedHashMap<>(argumenti);
                            }
                            normalizovani.put(parametar.getKey(), McpPatch.kaoJson(procitano));
                        } catch (SystemException e) {
                            return odbij(argumenti, pocetak, e.getMessage());
                        }
                    }
                }
                CallToolRequest zaAlat = normalizovani == null ? zahtev
                        : new CallToolRequest(zahtev.name(), normalizovani, zahtev.meta());
                CallToolResult rezultat;
                try {
                    rezultat = original.apply(kontekst, zaAlat);
                } catch (RuntimeException e) {
                    log.error("MCP alat={} greška van alata", alat, e);
                    if (!McpGreske.usaoUAlat()) {
                        McpGreske.zabelezi(alat, serija(argumenti), Map.of(), proteklo(pocetak), "sistemska");
                    }
                    return McpGreske.greska(McpGreske.SISTEMSKA);
                }
                if (rezultat == null) {
                    log.error("MCP alat={} bez rezultata", alat);
                    return McpGreske.greska(McpGreske.SISTEMSKA);
                }
                if (Boolean.TRUE.equals(rezultat.isError()) && !McpGreske.usaoUAlat()) {
                    return odbij(argumenti, pocetak, McpGreske.NEISPRAVAN_FORMAT);
                }
                return rezultat;
            } finally {
                McpGreske.ocisti();
            }
        }

        private CallToolResult odbij(Map<String, Object> argumenti, long pocetak, String poruka) {
            McpGreske.zabelezi(alat, serija(argumenti), Map.of(), proteklo(pocetak), NEISPRAVNI_ARGUMENTI);
            return McpGreske.greska(poruka);
        }

        private String serija(Map<String, Object> argumenti) {
            Object serija = argumenti.get("serija");
            String s = serija instanceof String str ? str : null;
            return McpGreske.serijaZaDnevnik(svojstva.contains("serija"), s,
                    s != null && Serija.OBRAZAC.matcher(s).matches());
        }

        private static Duration proteklo(long pocetak) {
            return Duration.ofNanos(System.nanoTime() - pocetak);
        }
    }
}
