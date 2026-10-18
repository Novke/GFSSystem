package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.extern.slf4j.Slf4j;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.Serija;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Izvršenje svakog MCP alata (spec MCP 2, "Greške" i "Dnevnik"). Telo alata je samo
 * {@code McpGreske.izvrsi("<alat>", serija, poziv -> odgovor.json(fasada.metoda(...)))}:
 * <ul>
 *   <li>upisni alat ({@code serija}): serija se proverava pre posla, pa {@code null} ili van obrasca daje {@code isError}
 *       {@link Serija#PORUKA} i ništa se ne radi;</li>
 *   <li>{@link SystemException} postaje {@code isError} sa porukom na srpskom (Klod je čita), svaki drugi izuzetak
 *       {@link #SISTEMSKA} (detalj samo u ERROR log);</li>
 *   <li>tačno jedan INFO red po pozivu: alat, oznaka ključa, serija, id-jevi, trajanje, ishod ({@code ok},
 *       {@code greska-<kod>}, {@code sistemska}); nikad argumenti ni sadržaj.</li>
 * </ul>
 * Nit pamti da je metoda alata ušla ({@link #usaoUAlat()}): {@link McpOmotac} tako razlikuje grešku vezivanja
 * argumenata (Spring AI, pre metode) od greške alata. Alat radi na niti zahteva ({@code immediateExecution}).
 */
@Slf4j
public final class McpGreske {

    public static final String SISTEMSKA = "Sistemska greška.";
    public static final String NEISPRAVAN_FORMAT = "Neispravan format podataka.";

    private static final ThreadLocal<Boolean> USAO = new ThreadLocal<>();

    private McpGreske() {
    }

    /** Upisni alat: prvo {@link Serija#proveri}, pa posao. */
    public static CallToolResult izvrsi(String alat, String serija, Function<McpPoziv, CallToolResult> posao) {
        return izvrsi(alat, serija, true, posao);
    }

    /** Alat bez serije (čitanje, {@code proba_rendera}, {@code ukljuci_temu}). */
    public static CallToolResult izvrsi(String alat, Function<McpPoziv, CallToolResult> posao) {
        return izvrsi(alat, null, false, posao);
    }

    public static CallToolResult greska(String poruka) {
        return CallToolResult.builder().isError(true).addTextContent(poruka).build();
    }

    static boolean usaoUAlat() {
        return Boolean.TRUE.equals(USAO.get());
    }

    static void ocisti() {
        USAO.remove();
    }

    private static CallToolResult izvrsi(String alat, String serija, boolean saSerijom,
                                         Function<McpPoziv, CallToolResult> posao) {
        USAO.set(Boolean.TRUE);
        boolean ispravnaSerija = saSerijom && serija != null && Serija.OBRAZAC.matcher(serija).matches();
        McpPoziv poziv = new McpPoziv(alat, ispravnaSerija ? serija : null, System.nanoTime());
        String ishod = "sistemska";
        try {
            if (saSerijom) {
                Serija.proveri(serija);
            }
            CallToolResult rezultat = posao.apply(poziv);
            if (rezultat == null) {
                throw new IllegalStateException("MCP alat " + alat + " nije vratio rezultat.");
            }
            // alat koji sam vrati isError (umesto da baci SystemException) beleži se kao greška zahteva
            ishod = Boolean.TRUE.equals(rezultat.isError()) ? "greska-400" : "ok";
            return rezultat;
        } catch (SystemException e) {
            ishod = "greska-" + (e.getCode() == null ? 400 : e.getCode());
            return greska(e.getMessage() == null ? SISTEMSKA : e.getMessage());
        } catch (RuntimeException e) {
            log.error("MCP alat={} neočekivana greška", alat, e);
            return greska(SISTEMSKA);
        } finally {
            zabelezi(alat, serijaZaDnevnik(saSerijom, serija, ispravnaSerija), poziv.ids(), poziv.proteklo(), ishod);
        }
    }

    /** Neispravna serija se ne piše doslovno (to je argument, ne oznaka). */
    static String serijaZaDnevnik(boolean saSerijom, String serija, boolean ispravna) {
        if (!saSerijom || serija == null) {
            return "-";
        }
        return ispravna ? serija : "neispravna";
    }

    static void zabelezi(String alat, String serija, Map<String, Object> ids, Duration trajanje, String ishod) {
        log.info("MCP alat={} kljuc={} serija={} ids={} trajanjeMs={} ishod={}",
                alat, McpZahtev.procitaj().orElse("-"), serija, ids, trajanje.toMillis(), ishod);
    }
}
