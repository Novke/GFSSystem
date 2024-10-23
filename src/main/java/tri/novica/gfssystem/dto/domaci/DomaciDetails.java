package tri.novica.gfssystem.dto.domaci;

import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class DomaciDetails {
    private Long id;
    private PredmetInfo predmet;
    private String naslov;
    private String text;
    private LocalDate datum;
    private Boolean pregledan;
    private DomaciGrupaInfo grupa;
    private DomaciPredavanjeInfo predavanje;
    private List<DomaciStudentiInfo> studenti = new ArrayList<>();
}
