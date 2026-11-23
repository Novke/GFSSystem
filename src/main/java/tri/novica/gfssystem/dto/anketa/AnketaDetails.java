package tri.novica.gfssystem.dto.anketa;

import tri.novica.gfssystem.entity.anketa.IzvorAnkete;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Anketa za nastavnika i MCP: {@code glasovi} = broj glasova po odgovoru (isti redosled kao {@code opcije}; za više
 * odgovora zbir po opciji), {@code brojGlasalo} = koliko studenata je glasalo, {@code brojMogucih} = koliko sme da glasa.
 * {@code zatvoreno} je efektivno vreme zatvaranja ({@code null} dok je otvorena). <b>Nikad</b> id, ime ni glas pojedinca.
 * {@code rokMs}/{@code sadaMs} su epoch ms za odbrojavanje.
 */
public record AnketaDetails(Long id, Long sablonId, String pitanje, List<String> opcije, boolean viseOdgovora,
                            PublikaAnkete publika, Long predavanjeId, Long grupaId, String grupaNaziv, String predmetNaziv,
                            LocalDateTime otvoreno, LocalDateTime rok, Long rokMs, LocalDateTime zatvoreno, boolean otvorena,
                            boolean rezultatPrikazan, IzvorAnkete izvor, List<Integer> glasovi, int brojGlasalo,
                            int brojMogucih, long sadaMs) {
}
