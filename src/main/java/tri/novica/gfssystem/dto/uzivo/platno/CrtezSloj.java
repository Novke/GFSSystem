package tri.novica.gfssystem.dto.uzivo.platno;

import java.util.List;

public record CrtezSloj(String id, String naziv, Double x, Double y, Double sirina, Double visina, Double rotacija, Double providnost,
        Boolean zakljucan, Senka senka,
        String medijId, List<Potez> potezi) implements Sloj {
}
