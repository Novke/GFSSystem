package tri.novica.gfssystem.dto.test.online;

import java.util.List;

/** Stavka šablona sa kandidatima i upozorenjima. */
public record StavkaSablona(Long id, int rb, String naziv, int poeni, int broj, List<Integer> nivoi, boolean istiTip, boolean rucno, List<IzvorStavke> izvori, KandidatiStavke kandidati, List<String> upozorenja) {
}
