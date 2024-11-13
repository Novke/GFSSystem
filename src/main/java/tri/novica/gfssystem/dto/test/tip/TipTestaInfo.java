package tri.novica.gfssystem.dto.test.tip;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TipTestaInfo {

    private Long id;

    private String naziv;
    private Boolean aktivan;
    private PredmetInfo predmet;

}
