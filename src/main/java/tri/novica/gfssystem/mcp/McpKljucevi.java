package tri.novica.gfssystem.mcp;

import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ključevi MCP klijenata iz {@code gfs.mcp.kljucevi} (spec MCP 3.1): stavke {@code oznaka:sha256hex} razdvojene zarezom,
 * oznaka {@code [a-z0-9-]{1,40}}, heš tačno 64 mala hex znaka. Na serveru je samo heš; više stavki omogućava rotaciju
 * (stari i novi ključ važe istovremeno), prazna vrednost znači da je MCP isključen. Neispravna stavka ruši start, a
 * poruka nosi samo njen redni broj, nikad sadržaj (to može biti sirov ključ zalepljen umesto heša).
 */
public final class McpKljucevi {

    private static final Pattern STAVKA = Pattern.compile("^([a-z0-9-]{1,40}):([0-9a-f]{64})$");

    private record Stavka(String oznaka, byte[] hes) {}

    private final List<Stavka> stavke;

    private McpKljucevi(List<Stavka> stavke) {
        this.stavke = List.copyOf(stavke);
    }

    public static McpKljucevi parsiraj(String vrednost) {
        if (vrednost == null || vrednost.isBlank()) {
            return new McpKljucevi(List.of());
        }
        String[] delovi = vrednost.strip().split(",", -1);
        List<Stavka> stavke = new ArrayList<>(delovi.length);
        for (int i = 0; i < delovi.length; i++) {
            Matcher m = STAVKA.matcher(delovi[i]);
            if (!m.matches()) {
                throw new IllegalStateException("Neispravna stavka gfs.mcp.kljucevi: #" + (i + 1));
            }
            stavke.add(new Stavka(m.group(1), m.group(2).getBytes(StandardCharsets.US_ASCII)));
        }
        return new McpKljucevi(stavke);
    }

    public boolean prazno() {
        return stavke.isEmpty();
    }

    /**
     * Oznaka stavke čiji heš odgovara ključu. Poređenje je u konstantnom vremenu ({@link MessageDigest#isEqual}) i ide
     * preko svih stavki bez ranog izlaza, pa trajanje ne zavisi od toga koja stavka (ni da li ijedna) odgovara.
     */
    public Optional<String> oznaka(String kljuc) {
        if (kljuc == null || kljuc.isEmpty()) {
            return Optional.empty();
        }
        byte[] hes = TokenHash.od(kljuc).getBytes(StandardCharsets.US_ASCII);
        String nadjena = null;
        for (Stavka s : stavke) {
            if (MessageDigest.isEqual(hes, s.hes()) && nadjena == null) {
                nadjena = s.oznaka();
            }
        }
        return Optional.ofNullable(nadjena);
    }

    public List<String> oznake() {
        return stavke.stream().map(Stavka::oznaka).toList();
    }
}
