package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.test.context.TestComponent;
import tri.novica.gfssystem.mcp.alati.TraziPotvrdu;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Probni alati samo za {@code McpOmotacDbTest} ({@code @TestComponent}: skeniranje ga ne pokupi, uvozi se izričito).
 * Telo je kao kod pravih alata: samo {@link McpGreske#izvrsi}. Broje pozive, da test dokaže da omotač nije pustio poziv.
 */
@TestComponent
class McpProbaAlati {

    record ProbaStavka(String b) {}

    record ProbaObjekat(Integer a, boolean zastavica, List<ProbaStavka> stavke) {}

    final AtomicInteger poziviAlata = new AtomicInteger();
    final AtomicInteger poziviObjekta = new AtomicInteger();

    private final McpOdgovor odgovor;

    McpProbaAlati(McpOdgovor odgovor) {
        this.odgovor = odgovor;
    }

    @McpTool(name = "proba_alat", description = "Probni alat.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    public CallToolResult probaAlat(CallToolRequest zahtev,
                                    @McpToolParam(description = "Broj.") Long broj,
                                    @McpToolParam(description = "Serija.") String serija,
                                    @McpToolParam(description = "Režim.") String rezim) {
        poziviAlata.incrementAndGet();
        return McpGreske.izvrsi("proba_alat", serija, poziv -> {
            if ("sistem".equals(rezim)) {
                throw new SystemException("Probna poruka.", 409);
            }
            if ("pad".equals(rezim)) {
                throw new IllegalStateException("probni pad sa detaljem");
            }
            Map<String, Object> rezultat = new HashMap<>();
            rezultat.put("broj", broj);
            rezultat.put("imaBroj", McpPatch.iz(zahtev).ima("broj"));
            return odgovor.json(rezultat);
        });
    }

    @McpTool(name = "proba_objekat", description = "Probni alat sa objektnim argumentom.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false),
            metaProvider = TraziPotvrdu.class)
    public CallToolResult probaObjekat(@McpToolParam(description = "Objekat.") ProbaObjekat obj,
                                       @McpToolParam(description = "Serija.") String serija) {
        poziviObjekta.incrementAndGet();
        return McpGreske.izvrsi("proba_objekat", serija, poziv -> odgovor.json(Map.of("a", obj == null ? -1 : obj.a())));
    }
}
