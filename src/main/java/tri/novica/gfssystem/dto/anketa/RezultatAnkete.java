package tri.novica.gfssystem.dto.anketa;

import java.util.List;

/** Rezultat ankete studentu, samo kad ga je nastavnik prikazao: glasovi po odgovoru i koliko je glasalo. */
public record RezultatAnkete(List<Integer> glasovi, int brojGlasalo) {
}
