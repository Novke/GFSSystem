package tri.novica.gfssystem.utility;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.regex.Pattern;

/**
 * Serija: oznaka jednog Novicinog zahteva Klodu (npr. {@code uvoz-kol1-2024}, spec MCP 4.1). Svaki upisni MCP alat je
 * prima, server je upisuje na sve što napravi, a pregled i poništavanje rade po njoj. Ista provera za MCP i REST.
 */
public final class Serija {

    public static final Pattern OBRAZAC = Pattern.compile("^[a-z0-9-]{3,60}$");
    public static final String PORUKA = "Serija mora imati 3 do 60 malih slova, cifara ili crtica.";

    private Serija() {
    }

    /** Vraća seriju kakva jeste; {@code null} ili van {@link #OBRAZAC} je 400 {@link #PORUKA}. */
    public static String proveri(String serija) {
        if (serija == null || !OBRAZAC.matcher(serija).matches()) {
            throw new SystemException(PORUKA, HttpStatus.BAD_REQUEST);
        }
        return serija;
    }
}
