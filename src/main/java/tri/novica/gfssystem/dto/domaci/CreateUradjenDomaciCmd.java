package tri.novica.gfssystem.dto.domaci;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateUradjenDomaciCmd {
    private Long studentId;
    private Long domaciId;
    @Min(value = 0, message = "Bodovi ne mogu biti negativni")
    @Max(value = 10, message = "Max 10 bodova je dozvoljeno")
    private int bodovi;
    private String napomene;
    private boolean prepisivanje = false;
}
