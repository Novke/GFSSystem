package tri.novica.gfssystem.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.TestGrupa;

import tri.novica.gfssystem.dto.test.online.StanjeOnline;
import tri.novica.gfssystem.entity.test.NacinTesta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestDetails {

    private Long id;
    private TipTestaInfo tipTesta;
    private PredmetInfo predmet;
    private GrupaInfo grupa;
    private LocalDate datum;
    private Integer maxPoena;
    /** Prag prolaza u poenima; null = nastavnik ga nije odredio, pa test nema prolaznost. */
    private Integer pragProlaza;
    private Boolean pregledan;
    private Set<TestGrupa> grupe;
    private Set<TestPolaganjeInfo> polaganja;
    private TestStatistikaInfo statistika;
    /** PAPIR ili ONLINE (online testovi, plan 4.1). */
    private NacinTesta nacin = NacinTesta.PAPIR;
    /** Stanje online testa; null za test na papiru. */
    private StanjeOnline stanjeOnline;
    /** Trenutak objave rezultata studentima; null = nije objavljeno. */
    private LocalDateTime objavljeno;
    /** Objava pokazuje i tačne odgovore i rešenja. */
    private boolean objavaTacni;
}
