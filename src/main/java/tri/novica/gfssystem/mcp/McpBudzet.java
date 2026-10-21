package tri.novica.gfssystem.mcp;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Dnevni budžet po oznaci ključa (spec MCP 3.1), u memoriji: najviše {@link #MAX_PRAVLJENJA} pravljenja (grupe,
 * varijante, slajdovi, prezentacije) i {@link #MAX_UPLOAD_BAJTOVA} upload-a, nula kad se promeni dan po {@link Clock}-u.
 * Preko granice 429 {@link #PORUKA} i ništa se ne računa. Zaštita od petlje ili procurelog ključa, ne od napadača.
 */
@Component
public class McpBudzet {

    public static final long MAX_UPLOAD_BAJTOVA = 200L * 1024 * 1024;
    public static final int MAX_PRAVLJENJA = 2_000;
    public static final String PORUKA = "Dnevni limit za Kloda je potrošen.";

    private final Clock clock;
    private final Map<String, Potrosnja> potrosnja = new HashMap<>();

    public McpBudzet(Clock clock) {
        this.clock = clock;
    }

    public synchronized void trosiPravljenje(String oznaka, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Negativan broj pravljenja.");
        }
        Potrosnja p = danas(oznaka);
        if (n > MAX_PRAVLJENJA - p.pravljenja) {
            throw new SystemException(PORUKA, HttpStatus.TOO_MANY_REQUESTS);
        }
        p.pravljenja += n;
    }

    public synchronized void trosiUpload(String oznaka, long bajtova) {
        if (bajtova < 0) {
            throw new IllegalArgumentException("Negativna veličina upload-a.");
        }
        Potrosnja p = danas(oznaka);
        if (bajtova > MAX_UPLOAD_BAJTOVA - p.bajtova) {
            throw new SystemException(PORUKA, HttpStatus.TOO_MANY_REQUESTS);
        }
        p.bajtova += bajtova;
    }

    private Potrosnja danas(String oznaka) {
        LocalDate dan = LocalDate.now(clock);
        Potrosnja p = potrosnja.get(oznaka);
        if (p == null || !p.dan.equals(dan)) {
            p = new Potrosnja(dan);
            potrosnja.put(oznaka, p);
        }
        return p;
    }

    private static final class Potrosnja {
        final LocalDate dan;
        int pravljenja;
        long bajtova;

        Potrosnja(LocalDate dan) {
            this.dan = dan;
        }
    }
}
