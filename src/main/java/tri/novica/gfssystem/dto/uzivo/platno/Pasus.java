package tri.novica.gfssystem.dto.uzivo.platno;

import java.util.List;

public record Pasus(Poravnanje poravnanje, Integer nivo, Lista lista, Uvlacenje uvlacenje, Double razmakPre,
                    Double razmakPosle, Double proredProcenat, List<Tabulator> tabulatori, List<Deo> delovi) {
}
