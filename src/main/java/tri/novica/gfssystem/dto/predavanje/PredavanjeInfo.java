package tri.novica.gfssystem.dto.predavanje;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class PredavanjeInfo {
    private Long id;
    private int rb;
    private LocalDate datum;
    private String tema;
    private Integer posecenost;
    private PredavanjeGrupaInfo grupa;
    private PredavanjePredmetInfo predmet;
}
