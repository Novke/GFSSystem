package tri.novica.gfssystem.dto.uzivo.platno;

import java.util.List;

public record Celija(List<Pasus> pasusi, String popuna, IviceCelije ivice, Integer spojDesno, Integer spojDole,
                     Boolean pokrivena, Sidro sidro) {
}
