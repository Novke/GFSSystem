package tri.novica.gfssystem.mcp.servis;

import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.uzivo.ZadatakSlajdaInfo;

import java.util.List;

/**
 * Zadatak slajd u MCP sažetku ({@code McpSlajdSazetak.zadatak}): čvor banke ({@code putanja} od korena, poslednji element
 * je sam čvor, pa nema posebnog naziva), broj kandidata nivoa slajda ({@code brojAktivnih}) i po nivou
 * ({@code brojAktivnihPoNivou}, za nivo 1, 2, 3), broj različitih zadataka ({@code null} = sve aktivne), nivo ({@code null}
 * = svi nivoi), tajmer, isključenja i upozorenja istim redom kao u editoru. Bez SVG-ova i odgovora varijanti.
 */
public record McpZadatakSazetak(Long grupaId, List<GrupaPutanja> putanja, int brojAktivnih,
                                List<Integer> brojAktivnihPoNivou, Integer brojVarijanti, Integer nivo, Integer vremeSekunde, boolean iskljucena, List<GrupaPutanja> iskljucenePodgrupe,
                                List<String> upozorenja) {

    /** Sažetak iz opisa slajda; {@code null} za {@code null}. */
    public static McpZadatakSazetak od(ZadatakSlajdaInfo z) {
        if (z == null) return null;
        return new McpZadatakSazetak(z.grupaId(), z.putanja() == null ? List.of() : z.putanja(), z.brojAktivnihVarijanti(),
                z.brojAktivnihPoNivou() == null ? List.of() : z.brojAktivnihPoNivou(), z.brojVarijanti(), z.nivo(),
                z.vremeSekunde(), z.iskljucena(),
                z.iskljucenePodgrupe() == null ? List.of() : z.iskljucenePodgrupe(),
                z.upozorenja() == null ? List.of() : z.upozorenja());
    }
}
