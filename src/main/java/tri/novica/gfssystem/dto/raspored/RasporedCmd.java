package tri.novica.gfssystem.dto.raspored;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Ceo period sa stavkama ({@code POST /rasporedi}, {@code PUT /rasporedi/{id}}). {@code pragNajava} null = 5. {@code PUT}
 * zamenjuje stavke: stavka sa {@code id}-jem ostaje (i njene najave) dok joj se ne promene predmet ili grupa, nove nemaju
 * {@code id}, izostavljene se brišu. Podsetnik dan pre časa (spec aplikacije 6.1): {@code podsetnik} ({@code null} = pri
 * upisu uključen, pri izmeni ne menja) i {@code podsetnikVreme} ({@code "HH:mm"}, od 06:00 do 21:30; {@code null} = pri
 * upisu 18:00, pri izmeni ne menja).
 */
public record RasporedCmd(String naziv, LocalDate vaziOd, LocalDate vaziDo, Integer pragNajava, List<StavkaCmd> stavke,
                          Boolean podsetnik, LocalTime podsetnikVreme) {

    @JsonCreator
    public RasporedCmd {
    }

    /** Bez izbora podsetnika (stariji front): pri upisu 18:00, pri izmeni ostaje kakav je bio. */
    public RasporedCmd(String naziv, LocalDate vaziOd, LocalDate vaziDo, Integer pragNajava, List<StavkaCmd> stavke) {
        this(naziv, vaziOd, vaziDo, pragNajava, stavke, null, null);
    }
}
