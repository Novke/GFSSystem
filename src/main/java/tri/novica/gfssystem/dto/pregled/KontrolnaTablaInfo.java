package tri.novica.gfssystem.dto.pregled;

import com.fasterxml.jackson.annotation.JsonProperty;
import tri.novica.gfssystem.dto.domaci.DomaciListItem;
import tri.novica.gfssystem.dto.predavanje.PredavanjeListItem;
import tri.novica.gfssystem.dto.test.TestListItem;

import java.util.List;

/**
 * Početna strana ({@code GET /pregled/kontrolna-tabla}). Liste nikad nisu null (prazna baza daje prazne nizove),
 * {@code sledece} je null kad nema nijednog predavanja.
 *
 * @param sledece predlog sledećeg predavanja (predmet i grupa poslednjeg, {@code rb + 1})
 * @param uToku   nezavršena predavanja sa današnjim datumom
 * @param ceka    "čeka na tebe"
 * @param nedelja predavanja, domaći i testovi tekuće nedelje (ponedeljak-nedelja), po datumu
 */
public record KontrolnaTablaInfo(
        SledecePredavanjeInfo sledece,
        // eksplicitno: iz getter-a "getUToku" Jackson bi izveo "utoku"
        @JsonProperty("uToku") List<PredavanjeListItem> uToku,
        Ceka ceka,
        List<AgendaStavkaInfo> nedelja) {

    /**
     * Stavke koje čekaju nastavnika, najviše 10 po listi, najnovije prve (prijave: prvo sesije kojima rok ističe najranije).
     *
     * @param testovi    testovi sa {@code pregledan != true}
     * @param domaci     domaći sa {@code pregledan != true}
     * @param prijave    onboarding sesije sa bar jednom prijavom na čekanju
     * @param nezavrsena nezavršena predavanja sa datumom pre danas
     */
    public record Ceka(
            List<TestListItem> testovi,
            List<DomaciListItem> domaci,
            List<CekaStavkaInfo> prijave,
            List<PredavanjeListItem> nezavrsena) {
    }
}
