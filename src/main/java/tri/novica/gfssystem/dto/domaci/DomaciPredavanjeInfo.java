package tri.novica.gfssystem.dto.domaci;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class DomaciPredavanjeInfo {
    Long id;
    Long rb;
    String tema;
    LocalDate datum;
}
