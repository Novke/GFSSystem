package tri.novica.gfssystem.dto.uzivo.platno;

import java.util.List;

public record OblikSloj(String id, String naziv, Double x, Double y, Double sirina, Double visina, Double rotacija, Double providnost,
        Boolean zakljucan, Senka senka,
        VrstaOblika oblik, String popuna, Ivica ivica, Tacka od, @com.fasterxml.jackson.annotation.JsonProperty("do") Tacka doTacke) implements Sloj {
}
