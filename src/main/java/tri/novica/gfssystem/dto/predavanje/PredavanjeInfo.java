package tri.novica.gfssystem.dto.predavanje;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class PredavanjeInfo {
    Long id;
    Long rb;
    String tema;
    LocalDate datum;
    Boolean zavrseno;
}
