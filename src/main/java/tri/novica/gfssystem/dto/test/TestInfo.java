package tri.novica.gfssystem.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.TestGrupa;

import java.time.LocalDate;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestInfo {

    private Long id;
    private TipTestaInfo tipTesta;
    private PredmetInfo predmet;
    private GrupaInfo grupa;
    private LocalDate datum;
    private Integer maxPoena;
    private Set<TestGrupa> grupe;

}
