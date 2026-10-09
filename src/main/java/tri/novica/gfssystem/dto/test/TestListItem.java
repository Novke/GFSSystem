package tri.novica.gfssystem.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;

import java.time.LocalDate;

/** Red liste testova. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestListItem {
    private Long id;
    private LocalDate datum;
    private TipTestaInfo tipTesta;
    private Integer maxPoena;
    /** Prag prolaza u poenima; null = nastavnik ga nije odredio, pa je {@code procenatProlaznosti} uvek null. */
    private Integer pragProlaza;
    private Boolean pregledan;
    private PredmetInfo predmet;
    private GrupaInfo grupa;
    /** Sva polaganja testa, i ona bez poena. */
    private long brojPolaganja;
    /** Prosek poena polaganja sa upisanim poenima; null kad takvih nema. */
    private Double prosek;
    /** Procenat (0-100) polaganja sa upisanim poenima koja su položena po {@link tri.novica.gfssystem.utility.Prolaz}
     * (poeni >= {@code pragProlaza}, bez prepisivanja; polje {@code polozio} se ignoriše); null kad test nema prag ili nema polaganja sa poenima. */
    private Double procenatProlaznosti;
}
