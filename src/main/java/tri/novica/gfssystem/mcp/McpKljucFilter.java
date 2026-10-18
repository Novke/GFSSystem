package tri.novica.gfssystem.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.advice.ApiException;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Štiti {@code /mcp} i {@code /mcp/*} (spec MCP 3.1; registruje ga {@link McpKonfig}). Bez ključeva u svojstvu 404, kao
 * nepostojeća putanja (fail-closed, ne otkriva da MCP postoji); inače čita samo {@code Authorization: Bearer <ključ>} i
 * bez ispravnog ključa vraća 401 sa {@code WWW-Authenticate: Bearer}. Oznaka ključa ide u atribut zahteva {@link #OZNAKA}
 * (dnevnik, budžet). Zaglavlje se nikad ne loguje. Nije {@code @Component}, da ga {@code @WebMvcTest} ne pokupi.
 */
@Slf4j
public class McpKljucFilter extends OncePerRequestFilter {

    public static final String OZNAKA = "gfs.mcp.oznaka";
    static final String PORUKA = "Neispravan MCP ključ.";
    /** Ista poruka kao {@code ApiExceptionHandler} za nepostojeću putanju. */
    static final String NE_POSTOJI = "Ne postoji.";

    private static final String SEMA = "Bearer";

    private final McpKljucevi kljucevi;
    private final JsonMapper json;
    private final Clock clock;

    public McpKljucFilter(McpKljucevi kljucevi, JsonMapper json, Clock clock) {
        this.kljucevi = kljucevi;
        this.json = json;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest zahtev, HttpServletResponse odgovor, FilterChain lanac)
            throws ServletException, IOException {
        if (kljucevi.prazno()) {
            odgovori(odgovor, HttpServletResponse.SC_NOT_FOUND, NE_POSTOJI);
            return;
        }
        Optional<String> oznaka = kljucevi.oznaka(bearer(zahtev.getHeader(HttpHeaders.AUTHORIZATION)));
        if (oznaka.isEmpty()) {
            log.info("MCP zahtev odbijen: neispravan ili bez ključa");
            odgovor.setHeader(HttpHeaders.WWW_AUTHENTICATE, SEMA);
            odgovori(odgovor, HttpServletResponse.SC_UNAUTHORIZED, PORUKA);
            return;
        }
        zahtev.setAttribute(OZNAKA, oznaka.get());
        lanac.doFilter(zahtev, odgovor);
    }

    /** Ključ iz {@code Bearer <ključ>} (šema bez obzira na velika slova), inače {@code null}. */
    static String bearer(String zaglavlje) {
        if (zaglavlje == null) {
            return null;
        }
        String z = zaglavlje.strip();
        int razmak = z.indexOf(' ');
        if (razmak < 0 || !z.substring(0, razmak).equalsIgnoreCase(SEMA)) {
            return null;
        }
        String kljuc = z.substring(razmak + 1).strip();
        return kljuc.isEmpty() ? null : kljuc;
    }

    private void odgovori(HttpServletResponse odgovor, int status, String poruka) throws IOException {
        odgovor.setStatus(status);
        odgovor.setContentType("application/json;charset=UTF-8");
        odgovor.getOutputStream().write(json.writeValueAsBytes(new ApiException(poruka, LocalDateTime.now(clock))));
    }
}
