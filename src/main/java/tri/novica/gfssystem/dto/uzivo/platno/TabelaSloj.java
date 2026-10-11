package tri.novica.gfssystem.dto.uzivo.platno;

import java.util.List;

public record TabelaSloj(String id, String naziv, Double x, Double y, Double sirina, Double visina, Double rotacija, Double providnost,
        Boolean zakljucan, Senka senka,
        List<Double> kolone, List<Double> redovi, List<List<Celija>> celije) implements Sloj {
}
