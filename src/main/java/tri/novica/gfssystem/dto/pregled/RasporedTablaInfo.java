package tri.novica.gfssystem.dto.pregled;

import java.util.List;

/**
 * Raspored na početnoj: {@code pokrivaDanas} = neki period sadrži današnji dan (tada predlog sledećeg predavanja dolazi iz
 * rasporeda); {@code naredni} = svi termini od danas do danas + 6 (7 dana; današnji i kad su prošli), po datumu pa početku.
 */
public record RasporedTablaInfo(boolean pokrivaDanas, List<TerminKratkoInfo> naredni) {

    public static RasporedTablaInfo prazno() {
        return new RasporedTablaInfo(false, List.of());
    }
}
