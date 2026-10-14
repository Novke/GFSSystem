package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalDate;

/**
 * Stavka "ove nedelje": predavanje, domaći ili test. {@code naslov} je "Predavanje {rb}", naslov domaćeg
 * ("Domaći" kad ga nema) ili naziv tipa testa ("Test" kad ga nema); {@code grupa} je null za stare redove bez grupe.
 */
public record AgendaStavkaInfo(Tip tip, Long id, LocalDate datum, String naslov, PredmetInfo predmet, GrupaInfo grupa) {

    /** Redosled konstanti je i redosled stavki istog dana. */
    public enum Tip {
        PREDAVANJE, DOMACI, TEST
    }
}
