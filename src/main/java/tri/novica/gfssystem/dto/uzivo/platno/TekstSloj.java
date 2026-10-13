package tri.novica.gfssystem.dto.uzivo.platno;

import java.util.List;

public record TekstSloj(String id, String naziv, Double x, Double y, Double sirina, Double visina, Double rotacija, Double providnost,
        Boolean zakljucan, Senka senka,
        List<Pasus> pasusi, Okvir okvir, String popuna, Ivica ivica) implements Sloj {
}
