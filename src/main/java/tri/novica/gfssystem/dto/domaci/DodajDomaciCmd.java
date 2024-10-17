package tri.novica.gfssystem.dto.domaci;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DodajDomaciCmd {
    Long predavanjeId;
    Long grupaId;
    Long predmetId;
}
