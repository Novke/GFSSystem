package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

/**
 * Predlog sledećeg predavanja. Bez rasporeda za danas: predmet i grupa poslednjeg predavanja (po datumu, pa id-ju), redni
 * broj za jedan veći ({@code izvor = POSLEDNJE}). Kad neki period rasporeda pokriva danas: današnji termin koji nije
 * otkazan, nema predavanje i nije završen ({@code RASPORED_DANAS}), inače prvi naredni neotkazan termin do 60 dana
 * ({@code RASPORED_SLEDECI}); tada je {@code rb} najveći redni broj + 1 (kao {@code POST /predavanja/start}).
 *
 * @param grupa         null kad poslednje predavanje nema grupu (stari redovi); tada su oba brojača 0
 * @param brojStudenata broj studenata koji su sada u grupi
 * @param brojStarijih  broj studenata iz starijih grupa (manja godina upisa) sa bar jednom aktivnošću ili polaganjem
 *                      na tom predmetu u tekućoj školskoj godini (ponovci koji dolaze)
 * @param termin        termin rasporeda iz koga je predlog (sa brojem najava), null za {@code POSLEDNJE}
 * @param izvor         odakle je predlog
 */
public record SledecePredavanjeInfo(PredmetInfo predmet, GrupaInfo grupa, Integer rb, Long brojStudenata,
                                    Long brojStarijih, TerminKratkoInfo termin, Izvor izvor) {

    public enum Izvor {
        /** Poslednje predavanje, {@code rb + 1} (stara heuristika; nijedan period ne pokriva danas ili nema termina). */
        POSLEDNJE,
        /** Današnji termin rasporeda. */
        RASPORED_DANAS,
        /** Danas nema časa po rasporedu: prvi naredni termin. */
        RASPORED_SLEDECI
    }
}
