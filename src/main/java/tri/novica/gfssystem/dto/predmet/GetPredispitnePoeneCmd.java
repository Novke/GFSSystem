package tri.novica.gfssystem.dto.predmet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetPredispitnePoeneCmd {
    private Long grupaId;
    private Double vrednostPrisustvo;
    private Double vrednostAktivnost;
    private Double vrednostZvezdica;
    private Double vrednostDomaci;
    private Double vrednostDomaciMaxPoena;
}
