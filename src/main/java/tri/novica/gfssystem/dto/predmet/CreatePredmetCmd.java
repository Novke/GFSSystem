package tri.novica.gfssystem.dto.predmet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePredmetCmd {
    private String naziv;
}
