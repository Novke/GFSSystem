package tri.novica.gfssystem.mcp;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * Oznaka MCP ključa tekućeg zahteva (upisuje je {@link McpKljucFilter}). Alati rade na niti zahteva (Spring AI
 * {@code immediateExecution}), pa je atribut zahteva vidljiv kroz {@link RequestContextHolder}.
 */
public final class McpZahtev {

    private McpZahtev() {
    }

    /** Oznaka ključa (budžet, dnevnik); bez nje zahtev nije prošao filter, pa je to {@link IllegalStateException}. */
    public static String oznaka() {
        return procitaj().orElseThrow(() -> new IllegalStateException("MCP zahtev bez oznake ključa."));
    }

    /** Oznaka ako postoji (dnevnik ne sme da padne zbog nje). */
    static Optional<String> procitaj() {
        RequestAttributes atributi = RequestContextHolder.getRequestAttributes();
        if (atributi == null) {
            return Optional.empty();
        }
        return atributi.getAttribute(McpKljucFilter.OZNAKA, RequestAttributes.SCOPE_REQUEST) instanceof String s
                ? Optional.of(s) : Optional.empty();
    }
}
