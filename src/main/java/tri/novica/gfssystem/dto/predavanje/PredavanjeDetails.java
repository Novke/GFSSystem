package tri.novica.gfssystem.dto.predavanje;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
public class PredavanjeDetails {
    private Long id;
    private int rb;
    private LocalDate datum;
    private String tema;
    private Integer posecenost;
    private PredavanjeGrupaInfo grupa;
    private PredavanjePredmetInfo predmet;
    private List<PredavanjeAktivnostInfo> aktivnosti;
}
