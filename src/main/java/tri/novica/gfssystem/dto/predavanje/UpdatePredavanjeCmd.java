package tri.novica.gfssystem.dto.predavanje;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class UpdatePredavanjeCmd {
    private Integer rb;
    private LocalDate datum;
    private String tema;
    private Integer posecenost;
}
