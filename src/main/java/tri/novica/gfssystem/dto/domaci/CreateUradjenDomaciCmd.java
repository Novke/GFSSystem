package tri.novica.gfssystem.dto.domaci;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateUradjenDomaciCmd {
    private Long studentId;
    private Long domaciId;
    private int bodovi;
    private String napomene;
    private boolean prepisivanje = false;
}
