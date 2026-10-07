package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

/**
 * Predlog sledećeg predavanja: predmet i grupa poslednjeg predavanja (po datumu, pa id-ju), redni broj za jedan veći.
 *
 * @param grupa         null kad poslednje predavanje nema grupu (stari redovi); tada su oba brojača 0
 * @param brojStudenata broj studenata koji su sada u grupi
 * @param brojStarijih  broj studenata iz starijih grupa (manja godina upisa) sa bar jednom aktivnošću ili polaganjem
 *                      na tom predmetu (ponovci koji dolaze)
 */
public record SledecePredavanjeInfo(PredmetInfo predmet, GrupaInfo grupa, Integer rb, Long brojStudenata,
                                    Long brojStarijih) {
}
