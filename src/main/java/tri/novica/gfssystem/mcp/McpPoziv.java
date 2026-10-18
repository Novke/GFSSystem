package tri.novica.gfssystem.mcp;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jedan poziv MCP alata, kako ga vidi posao u {@link McpGreske#izvrsi}: ime alata, proverena serija (ili {@code null} za
 * alate bez serije), id-jevi za dnevnik ({@link #id}) i proteklo vreme. Rok celog alata je {@link #ROK} (frontend nginx
 * čeka 120 s, a 504 posle upisa bi naveo Kloda da ponovi); dodatna slika se traži samo dok je proteklo najviše
 * {@link #MAX_ZA_DODATNU_SLIKU}.
 */
public final class McpPoziv {

    public static final Duration ROK = Duration.ofSeconds(90);
    public static final Duration MAX_ZA_DODATNU_SLIKU = Duration.ofSeconds(30);

    private final String alat;
    private final String serija;
    private final long pocetakNs;
    private final Map<String, Object> ids = new LinkedHashMap<>();

    McpPoziv(String alat, String serija, long pocetakNs) {
        this.alat = alat;
        this.serija = serija;
        this.pocetakNs = pocetakNs;
    }

    public String alat() {
        return alat;
    }

    public String serija() {
        return serija;
    }

    /** Id za dnevnik, npr. {@code poziv.id("varijanta", 12)} daje {@code ids={varijanta=12}}; nikad sadržaj. */
    public void id(String vrsta, Object id) {
        ids.put(vrsta, id);
    }

    public Duration proteklo() {
        return Duration.ofNanos(System.nanoTime() - pocetakNs);
    }

    public boolean imaVremenaZaSliku() {
        return proteklo().compareTo(MAX_ZA_DODATNU_SLIKU) <= 0;
    }

    Map<String, Object> ids() {
        return Collections.unmodifiableMap(ids);
    }
}
