package tri.novica.gfssystem.dto.anketa;

import tri.novica.gfssystem.entity.anketa.PublikaAnkete;

import java.util.List;

/**
 * Anketa na telefonu studenta ({@code CasStanje.ankete}, {@code GET /public/nalog/ankete}): {@code mojGlas} = njegovi
 * indeksi ili {@code null}, {@code rezultat} samo kad ga je nastavnik prikazao, {@code rokMs}/{@code sadaMs} epoch ms za
 * odbrojavanje. Bez {@code brojMogucih}, grupe, predavanja i ičega o drugim studentima.
 */
public record AnketaStudentu(Long id, String pitanje, List<String> opcije, boolean viseOdgovora, PublikaAnkete publika,
                             Long rokMs, boolean otvorena, List<Integer> mojGlas, RezultatAnkete rezultat, long sadaMs) {
}
