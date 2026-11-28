package tri.novica.gfssystem.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;

import tri.novica.gfssystem.dto.test.online.StanjeOnline;
import tri.novica.gfssystem.entity.test.NacinTesta;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    /** PAPIR ili ONLINE (online testovi, plan 4.1). */
    private NacinTesta nacin = NacinTesta.PAPIR;
    /** Stanje online testa; null za test na papiru. */
    private StanjeOnline stanjeOnline;
    /** Trenutak objave rezultata studentima; null = nije objavljeno. */
    private LocalDateTime objavljeno;

    /** Red bez polja online testova (test na papiru, neobjavljen). */
    public TestListItem(Long id, LocalDate datum, TipTestaInfo tipTesta, Integer maxPoena, Integer pragProlaza, Boolean pregledan,
                        PredmetInfo predmet, GrupaInfo grupa, long brojPolaganja, Double prosek, Double procenatProlaznosti) {
        this(id, datum, tipTesta, maxPoena, pragProlaza, pregledan, predmet, grupa, brojPolaganja, prosek, procenatProlaznosti,
                NacinTesta.PAPIR, null, null);
    }
}
