package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.onboarding.OnboardingSesijaInfo;

import java.util.List;

/**
 * Pregled grupe (G1 zaglavlje + G2 statistika po studentu), opciono za jedan predmet; bez predmeta brojevi su preko
 * svih predmeta.
 *
 * @param brojPredavanja      predavanja te grupe (i predmeta)
 * @param prosecnaPrisutnost  zbir {@code prisutan} / zbir {@code predavanja} po studentima, 0-1; null kad je zbir
 *                            predavanja 0 (grupa bez predavanja i bez prisustva drugde, ili bez studenata)
 * @param otvorenOnboarding   najnovija otvorena sesija (aktivna, nije istekla, nije popunjena) ili null
 * @param studenti            studenti grupe po broju indeksa
 */
public record GrupaPregledInfo(GrupaInfo grupa, int brojStudenata, long brojPredavanja, Double prosecnaPrisutnost,
                               OnboardingSesijaInfo otvorenOnboarding, List<GrupaStudentStatInfo> studenti) {
}
