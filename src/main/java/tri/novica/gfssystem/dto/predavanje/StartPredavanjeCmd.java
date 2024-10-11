package tri.novica.gfssystem.dto.predavanje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartPredavanjeCmd {
    private Long predmetId;
    private Long grupaId;
}
