package tri.novica.gfssystem.mcp.servis;

import com.fasterxml.jackson.annotation.JsonInclude;
import tri.novica.gfssystem.entity.uzivo.Tema;

import java.util.List;

/**
 * Rezultat alata {@code prezentacija} (spec MCP 4.4): polja prezentacije, jedna strana sažetaka slajdova
 * ({@link PrezentacijaPregled}; {@code strana} od 1, {@code brojSlajdova} je broj svih slajdova) i link u UI-ju.
 * {@code brojFajlova}: svi fajlovi za studente uz prezentaciju, {@code fajloviUzCeluPrezentaciju}: od njih oni bez slajda (oba samo kad ih ima).
 * {@code aktivnoIzvodjenjeId} postoji samo dok se prezentacija izvodi (tada se kroz MCP ne menja); odsutna polja se ne pišu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpPrezentacijaPregled(Long id, String naziv, String opis, Tema tema, Long aktivnoIzvodjenjeId,
                                     int brojSlajdova, int strana, int brojStrana, List<McpSlajdSazetak> slajdovi,
                                     String link, Integer brojFajlova, Integer fajloviUzCeluPrezentaciju) {

    /** Bez fajlova uz prezentaciju (polja se ne pišu). */
    public McpPrezentacijaPregled(Long id, String naziv, String opis, Tema tema, Long aktivnoIzvodjenjeId, int brojSlajdova,
                                  int strana, int brojStrana, List<McpSlajdSazetak> slajdovi, String link) {
        this(id, naziv, opis, tema, aktivnoIzvodjenjeId, brojSlajdova, strana, brojStrana, slajdovi, link, null, null);
    }
}
