package tri.novica.gfssystem.dto.uzivo;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.annotation.JsonDeserialize;
import tri.novica.gfssystem.entity.uzivo.Tema;
import tri.novica.gfssystem.utility.CeoBrojDeserializer;

/**
 * Nova prezentacija predmeta; podešavanja dobijaju podrazumevane vrednosti ({@code tema == null} je NACRT).
 * {@code vezba} je redni broj vežbe (0-99, {@code null} = bez broja; samo JSON ceo broj): server ga ne izvodi iz naziva,
 * to radi forma (predlog) i MCP fasada.
 */
public record CreatePrezentacijaCmd(Long predmetId, String naziv, String opis, Tema tema,
                                    @JsonDeserialize(using = CeoBrojDeserializer.class) Integer vezba) {

    @JsonCreator
    public CreatePrezentacijaCmd {
    }

    /** Bez broja vežbe (stari pozivi i testovi). */
    public CreatePrezentacijaCmd(Long predmetId, String naziv, String opis, Tema tema) {
        this(predmetId, naziv, opis, tema, null);
    }
}
